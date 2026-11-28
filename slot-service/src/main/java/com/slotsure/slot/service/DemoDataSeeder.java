package com.slotsure.slot.service;

import com.slotsure.slot.config.SlotsureProperties;
import com.slotsure.slot.entity.Reservation;
import com.slotsure.slot.entity.ReservationStatus;
import com.slotsure.slot.entity.Slot;
import com.slotsure.slot.repository.ReservationRepository;
import com.slotsure.slot.repository.SlotRepository;
import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds dummy slots for yesterday and today so the admin dashboard is never empty (CONTRACT V2.10).
 * A date is seeded once: when it already has demo slots it is left alone. Each area gets the
 * locality centroid's resolution-9 cell; bookings peak 18:00-21:00. Every booking is a CONFIRMED
 * reservation, so a Redis counter rebuilt from MySQL agrees with the seeded booked count.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder {

    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final AreaResolver areaResolver;
    private final H3Core h3Core;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final SlotsureProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!properties.demoData().enabled()) {
            return;
        }
        LocalDate today = LocalDate.now(properties.slot().zone());
        Random random = new Random();
        for (LocalDate date : List.of(today.minusDays(1), today)) {
            try {
                seed(date, random);
            } catch (RuntimeException e) {
                log.error("Could not seed demo slots for {}", date, e);
            }
        }
    }

    /** Returns how many bookings were seeded; 0 when the date already has demo slots. */
    public int seed(LocalDate date, Random random) {
        if (slotRepository.existsBySlotDateAndDemoTrue(date)) {
            log.info("Demo slots for {} already exist, not seeding", date);
            return 0;
        }
        List<Slot> slots = demoSlots(date);
        if (slots.isEmpty()) {
            log.info("No free demo cells for {}, not seeding", date);
            return 0;
        }
        int bookings = distributeBookings(slots, random);
        List<Reservation> reservations = confirmedReservations(slots, random);

        transactionTemplate.executeWithoutResult(status -> {
            slotRepository.saveAll(slots);
            reservationRepository.saveAll(reservations);
        });
        for (Slot slot : slots) {
            redis.opsForValue().set(SlotCounterService.key(slot.getId()),
                    Integer.toString(slot.getCapacity() - slot.getBooked()));
        }
        log.info("Seeded {} demo slots with {} bookings for {}", slots.size(), bookings, date);
        return bookings;
    }

    /** One day of slots per configured area. An area whose cell already has slots that day is skipped. */
    private List<Slot> demoSlots(LocalDate date) {
        SlotsureProperties.SlotSettings settings = properties.slot();
        List<Slot> slots = new ArrayList<>();
        for (SlotsureProperties.Area area : properties.areas()) {
            String cell = h3Core.latLngToCellAddress(area.lat(), area.lng(), SlotService.H3_RESOLUTION);
            if (!slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, date).isEmpty()) {
                log.info("Cell {} ({}) already has slots on {}, skipping it for demo data", cell, area.name(), date);
                continue;
            }
            String areaName = areaResolver.resolve(cell);
            for (int hour = settings.firstHour(); hour < settings.lastHour(); hour++) {
                Slot slot = new Slot(cell, areaName, date, hour, settings.capacity());
                slot.setDemo(true);
                slots.add(slot);
            }
        }
        return slots;
    }

    /**
     * Books between min and max bookings (from config) across the slots, weighted by hour and by a
     * random popularity per area. A full slot drops out of the draw. Returns the number booked.
     */
    int distributeBookings(List<Slot> slots, Random random) {
        SlotsureProperties.DemoData demo = properties.demoData();
        int spread = Math.max(0, demo.maxBookingsPerDay() - demo.minBookingsPerDay());
        int totalCapacity = slots.stream().mapToInt(Slot::getCapacity).sum();
        int target = Math.min(totalCapacity, demo.minBookingsPerDay() + random.nextInt(spread + 1));

        Map<String, Double> areaPopularity = new HashMap<>();
        double[] weights = new double[slots.size()];
        double totalWeight = 0;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            double popularity = areaPopularity.computeIfAbsent(slot.getAreaName(), name -> 0.6 + random.nextDouble() * 0.8);
            weights[i] = slot.getBooked() < slot.getCapacity() ? popularity * hourWeight(slot.getStartHour()) : 0;
            totalWeight += weights[i];
        }

        int booked = 0;
        while (booked < target && totalWeight > 0) {
            int i = pick(weights, random.nextDouble() * totalWeight);
            Slot slot = slots.get(i);
            slot.setBooked(slot.getBooked() + 1);
            booked++;
            if (slot.getBooked() >= slot.getCapacity()) {
                totalWeight -= weights[i];
                weights[i] = 0;
            }
        }
        return booked;
    }

    /** Relative demand by start hour: evening peak 18:00-21:00, shoulders either side, a small lunch bump. */
    static double hourWeight(int startHour) {
        if (startHour >= 18 && startHour < 21) {
            return 3.0;
        }
        if (startHour == 17 || startHour == 21) {
            return 1.5;
        }
        if (startHour == 12 || startHour == 13) {
            return 1.2;
        }
        return 0.7;
    }

    private static int pick(double[] weights, double point) {
        double running = 0;
        int last = -1;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) {
                continue;
            }
            running += weights[i];
            last = i;
            if (point < running) {
                return i;
            }
        }
        return last;
    }

    private List<Reservation> confirmedReservations(List<Slot> slots, Random random) {
        long expirySeconds = properties.hold().expirySeconds();
        List<Reservation> reservations = new ArrayList<>();
        for (Slot slot : slots) {
            Instant slotStart = slot.getSlotDate().atTime(slot.getStartHour(), 0).atZone(properties.slot().zone()).toInstant();
            for (int n = 0; n < slot.getBooked(); n++) {
                // Booked between 1 and 30 hours before the slot starts.
                Instant createdAt = slotStart.minusSeconds(3600L + random.nextInt(29 * 3600));
                Reservation reservation = new Reservation(slot.getId(), UUID.randomUUID().toString(),
                        "demo-" + UUID.randomUUID(), createdAt, createdAt.plusSeconds(expirySeconds));
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservation.setOrderId(UUID.randomUUID().toString());
                reservation.setDemo(true);
                reservations.add(reservation);
            }
        }
        return reservations;
    }
}
