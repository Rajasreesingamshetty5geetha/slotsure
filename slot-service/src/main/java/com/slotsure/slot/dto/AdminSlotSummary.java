package com.slotsure.slot.dto;

import java.time.LocalDate;
import java.util.List;

/** Response of GET /api/admin/slots (CONTRACT V2.7). booked counts confirmed bookings. */
public record AdminSlotSummary(
        LocalDate date,
        long totalCapacity,
        long totalBooked,
        double utilisationPct,
        List<AreaLoad> byArea,
        List<BusySlot> busiest) {

    public record AreaLoad(String areaName, long capacity, long booked) {
    }

    public record BusySlot(String areaName, int startHour, int endHour, long booked, long capacity) {
    }
}
