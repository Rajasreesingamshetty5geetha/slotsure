package com.slotsure.slot.dto;

import java.time.LocalDate;

public record SlotResponse(
        String slotId,
        String h3Cell,
        String areaName,
        LocalDate date,
        int startHour,
        int endHour,
        int capacity,
        long remaining,
        PriceTag priceTag) {
}
