package com.slotsure.slot.service;

import com.slotsure.slot.TestProperties;
import com.slotsure.slot.entity.Reservation;
import com.slotsure.slot.entity.ReservationStatus;
import com.slotsure.slot.entity.Slot;
import com.slotsure.slot.repository.ReservationRepository;
import com.slotsure.slot.repository.SlotRepository;
import com.uber.h3core.H3Core;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoDataSeederTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private static H3Core h3Core;

    private SlotRepository slotRepository;
    private ReservationRepository reservationRepository;
    private ValueOperations<String, String> values;
    private DemoDataSeeder seeder;

    @BeforeAll
    static void loadH3() throws IOException {
        h3Core = H3Core.newInstance();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        slotRepository = mock(SlotRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(slotRepository.findByH3CellAndSlotDateOrderByStartHour(any(), eq(DATE))).thenReturn(List.of());
        AreaResolver areaResolver = new AreaResolver(h3Core, TestProperties.defaults());
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        seeder = new DemoDataSeeder(slotRepository, reservationRepository, areaResolver, h3Core, redis,
                transactionTemplate, TestProperties.defaults());
    }

    @Test
    @SuppressWarnings("unchecked")
    void seedsEveryAreaWithAnEveningPeakAndMatchingReservationsAndCounters() {
        ArgumentCaptor<List<Slot>> slotsSaved = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Reservation>> reservationsSaved = ArgumentCaptor.forClass(List.class);
        when(slotRepository.saveAll(slotsSaved.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.saveAll(reservationsSaved.capture())).thenAnswer(inv -> inv.getArgument(0));

        int bookings = seeder.seed(DATE, new Random(42));

        List<Slot> slots = slotsSaved.getValue();
        assertThat(slots).hasSize(10 * 14);
        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.isDemo()).isTrue();
            assertThat(slot.getSlotDate()).isEqualTo(DATE);
            assertThat(slot.getBooked()).isBetween(0, slot.getCapacity());
        });
        assertThat(slots).extracting(Slot::getAreaName).containsOnly(
                TestProperties.BENGALURU.stream().map(area -> area.name()).toArray(String[]::new));
        assertThat(slots.stream().map(Slot::getAreaName).distinct()).hasSize(10);

        assertThat(bookings).isBetween(40, 80);
        assertThat(slots.stream().mapToInt(Slot::getBooked).sum()).isEqualTo(bookings);
        Map<Integer, Integer> byHour = slots.stream()
                .collect(Collectors.groupingBy(Slot::getStartHour, Collectors.summingInt(Slot::getBooked)));
        int evening = byHour.get(18) + byHour.get(19) + byHour.get(20);
        assertThat(evening * 100 / bookings).as("share of bookings 18:00-21:00").isGreaterThanOrEqualTo(30);

        List<Reservation> reservations = reservationsSaved.getValue();
        assertThat(reservations).hasSize(bookings);
        assertThat(reservations).allSatisfy(reservation -> {
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
            assertThat(reservation.isDemo()).isTrue();
            assertThat(reservation.getOrderId()).isNotNull();
        });

        for (Slot slot : slots) {
            verify(values).set(SlotCounterService.key(slot.getId()), Integer.toString(slot.getCapacity() - slot.getBooked()));
        }
    }

    @Test
    void leavesADateThatAlreadyHasDemoSlotsAlone() {
        when(slotRepository.existsBySlotDateAndDemoTrue(DATE)).thenReturn(true);

        assertThat(seeder.seed(DATE, new Random(1))).isZero();

        verify(slotRepository, never()).saveAll(anyList());
        verify(reservationRepository, never()).saveAll(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsAnAreaWhoseCellAlreadyHasSlotsThatDay() {
        String koramangala = h3Core.latLngToCellAddress(12.9352, 77.6245, 9);
        when(slotRepository.findByH3CellAndSlotDateOrderByStartHour(koramangala, DATE))
                .thenReturn(List.of(new Slot(koramangala, "Koramangala", DATE, 8, 10)));
        ArgumentCaptor<List<Slot>> slotsSaved = ArgumentCaptor.forClass(List.class);
        when(slotRepository.saveAll(slotsSaved.capture())).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed(DATE, new Random(7));

        assertThat(slotsSaved.getValue()).hasSize(9 * 14);
        assertThat(slotsSaved.getValue()).noneMatch(slot -> slot.getH3Cell().equals(koramangala));
    }

    @Test
    void neverBooksASlotPastItsCapacity() {
        List<Slot> slots = new ArrayList<>();
        for (int hour = 8; hour < 11; hour++) {
            slots.add(new Slot("cell", "Hebbal", DATE, hour, 10));
        }

        // Asked for 40-80 bookings but only 30 places exist.
        int booked = seeder.distributeBookings(slots, new Random(3));

        assertThat(booked).isEqualTo(30);
        assertThat(slots).allSatisfy(slot -> assertThat(slot.getBooked()).isEqualTo(10));
    }

    @Test
    void eveningHoursWeighMost() {
        assertThat(DemoDataSeeder.hourWeight(19)).isGreaterThan(DemoDataSeeder.hourWeight(17));
        assertThat(DemoDataSeeder.hourWeight(17)).isGreaterThan(DemoDataSeeder.hourWeight(10));
        assertThat(DemoDataSeeder.hourWeight(21)).isLessThan(DemoDataSeeder.hourWeight(20));
    }
}
