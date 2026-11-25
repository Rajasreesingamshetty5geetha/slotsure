package com.slotsure.slot.service;

import com.slotsure.slot.config.SlotsureProperties;
import com.slotsure.slot.dto.PriceTag;
import com.slotsure.slot.dto.SlotResponse;
import com.slotsure.slot.entity.Slot;
import com.slotsure.slot.exception.ApiException;
import com.slotsure.slot.repository.SlotRepository;
import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    static final int H3_RESOLUTION = 9;

    private final SlotRepository slotRepository;
    private final SlotCounterService slotCounterService;
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

    /**
     * Creates the day's slots in one transaction. If another request created them first,
     * the unique constraint on (h3_cell, slot_date, start_hour) fails and we read theirs instead.
     */
    private List<Slot> createSlots(String cell, LocalDate date) {
        SlotsureProperties.SlotSettings settings = properties.slot();
        List<Slot> fresh = new ArrayList<>();
        for (int hour = settings.firstHour(); hour < settings.lastHour(); hour++) {
            fresh.add(new Slot(cell, date, hour, settings.capacity()));
        }
        try {
            transactionTemplate.executeWithoutResult(status -> slotRepository.saveAllAndFlush(fresh));
            log.info("Created {} slots for cell {} on {}", fresh.size(), cell, date);
        } catch (DataIntegrityViolationException e) {
            log.info("Slots for cell {} on {} were created by a concurrent request, re-reading", cell, date);
        }
        return slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, date);
    }

    private SlotResponse toResponse(Slot slot, long remaining) {
        return new SlotResponse(
                slot.getId(),
                slot.getH3Cell(),
                slot.getSlotDate(),
                slot.getStartHour(),
                slot.getStartHour() + 1,
                slot.getCapacity(),
                remaining,
                PriceTag.of(remaining, slot.getCapacity()));
    }
}
