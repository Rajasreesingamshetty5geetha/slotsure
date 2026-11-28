package com.slotsure.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/admin/summary (CONTRACT V2.6), for orders whose slot falls on the date.
 * confirmed counts every booking whose slot was confirmed (also those out for delivery or delivered);
 * failed counts FAILED and CANCELLED. codExpected is cash still to collect; codCollected is cash taken.
 */
public record AdminSummaryResponse(LocalDate date,
                                   long totalBookings,
                                   long confirmed,
                                   long failed,
                                   long delivered,
                                   BigDecimal prepaidAmount,
                                   BigDecimal onlineAmount,
                                   BigDecimal codExpected,
                                   BigDecimal codCollected,
                                   List<ItemCount> topItems,
                                   List<HourCount> byHour,
                                   List<AreaCount> byArea) {

    public record ItemCount(String itemName, long count) {
    }

    public record HourCount(int startHour, long count) {
    }

    public record AreaCount(String areaName, long count) {
    }
}
