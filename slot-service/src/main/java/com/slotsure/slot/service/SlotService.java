package com.slotsure.slot.service;

import com.slotsure.slot.config.SlotsureProperties;
import com.slotsure.slot.dto.AdminSlotSummary;
import com.slotsure.slot.dto.PriceTag;
import com.slotsure.slot.dto.SlotResponse;
import com.slotsure.slot.entity.Slot;
import com.slotsure.slot.exception.ApiException;
import com.slotsure.slot.repository.SlotRepository;
import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    static final int H3_RESOLUTION = 9;
    static final int BUSIEST_LIMIT = 5;
    private static final int BACKFILL_BATCH = 500;

    private final SlotRepository slotRepository;
    private final SlotCounterService slotCounterService;
    private final AreaResolver areaResolver;
    private final H3Core h3Core;
    private final TransactionTemplate transactionTemplate;
    private final SlotsureProperties properties;

    public List<SlotResponse> findSlots(double lat, double lng, LocalDate date) {
        String cell = toCell(lat, lng);
        List<Slot> slots = slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, date);
        if (slots.isEmpty()) {
            slots = createSlots(cell, date);
        }
        Map<String, Long> remaining = slotCounterService.remaining(slots);
        return slots.stream().map(slot -> toResponse(slot, remaining.get(slot.getId()))).toList();
    }

    public String toCell(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw ApiException.badRequest("lat must be within [-90, 90] and lng within [-180, 180]");
        }
        return h3Core.latLngToCellAddress(lat, lng, H3_RESOLUTION);
    }

    /** Today in the zone that slot dates are kept in. */
    public LocalDate today() {
        return LocalDate.now(properties.slot().zone());
    }

    /** Capacity and confirmed bookings for one date, by area and by busiest area-hour (CONTRACT V2.7). */
    public AdminSlotSummary adminSummary(LocalDate date) {
        List<AdminSlotSummary.AreaLoad> byArea = slotRepository.totalsByArea(date).stream()
                .map(row -> new AdminSlotSummary.AreaLoad(areaOrUnknown(row.getAreaName()), row.getCapacity(), row.getBooked()))
                .toList();
        long totalCapacity = byArea.stream().mapToLong(AdminSlotSummary.AreaLoad::capacity).sum();
        long totalBooked = byArea.stream().mapToLong(AdminSlotSummary.AreaLoad::booked).sum();
        List<AdminSlotSummary.BusySlot> busiest = slotRepository.busiestSlots(date, PageRequest.of(0, BUSIEST_LIMIT)).stream()
                .map(row -> new AdminSlotSummary.BusySlot(areaOrUnknown(row.getAreaName()), row.getStartHour(),
                        row.getStartHour() + 1, row.getBooked(), row.getCapacity()))
                .toList();
        return new AdminSlotSummary(date, totalCapacity, totalBooked, utilisationPct(totalBooked, totalCapacity),
                byArea, busiest);
    }

    /** Names slots created before areas existed. Runs once on startup; one bulk update per area. */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillAreaNames() {
        try {
            List<String> cells = slotRepository.findCellsWithoutAreaName();
            if (cells.isEmpty()) {
                return;
            }
            Map<String, List<String>> cellsByArea = new TreeMap<>();
            for (String cell : cells) {
                try {
                    cellsByArea.computeIfAbsent(areaResolver.resolve(cell), name -> new ArrayList<>()).add(cell);
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping area backfill for invalid cell {}", cell);
                }
            }
            int updated = 0;
            for (Map.Entry<String, List<String>> entry : cellsByArea.entrySet()) {
                List<String> areaCells = entry.getValue();
                for (int from = 0; from < areaCells.size(); from += BACKFILL_BATCH) {
                    List<String> batch = areaCells.subList(from, Math.min(from + BACKFILL_BATCH, areaCells.size()));
                    Integer rows = transactionTemplate.execute(status -> slotRepository.setAreaName(batch, entry.getKey()));
                    updated += rows == null ? 0 : rows;
                }
            }
            log.info("Backfilled areaName on {} slots across {} cells", updated, cells.size());
        } catch (RuntimeException e) {
            log.error("Area name backfill failed; it will be retried on next startup", e);
        }
    }

    static double utilisationPct(long booked, long capacity) {
        return capacity == 0 ? 0.0 : Math.round(booked * 1000.0 / capacity) / 10.0;
    }

    /**
     * Creates the day's slots in one transaction. If another request created them first,
     * the unique constraint on (h3_cell, slot_date, start_hour) fails and we read theirs instead.
     */
    private List<Slot> createSlots(String cell, LocalDate date) {
        SlotsureProperties.SlotSettings settings = properties.slot();
        String areaName = areaResolver.resolve(cell);
        List<Slot> fresh = new ArrayList<>();
        for (int hour = settings.firstHour(); hour < settings.lastHour(); hour++) {
            fresh.add(new Slot(cell, areaName, date, hour, settings.capacity()));
        }
        try {
            transactionTemplate.executeWithoutResult(status -> slotRepository.saveAllAndFlush(fresh));
            log.info("Created {} slots for cell {} ({}) on {}", fresh.size(), cell, areaName, date);
        } catch (DataIntegrityViolationException e) {
            log.info("Slots for cell {} on {} were created by a concurrent request, re-reading", cell, date);
        }
        return slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, date);
    }

    private SlotResponse toResponse(Slot slot, long remaining) {
        // Only slots not yet reached by the startup backfill lack a name.
        String areaName = slot.getAreaName() != null ? slot.getAreaName() : areaResolver.resolve(slot.getH3Cell());
        return new SlotResponse(
                slot.getId(),
                slot.getH3Cell(),
                areaName,
                slot.getSlotDate(),
                slot.getStartHour(),
                slot.getEndHour(),
                slot.getCapacity(),
                remaining,
                PriceTag.of(remaining, slot.getCapacity()));
    }

    private static String areaOrUnknown(String areaName) {
        return areaName != null ? areaName : AreaResolver.UNKNOWN;
    }
}
