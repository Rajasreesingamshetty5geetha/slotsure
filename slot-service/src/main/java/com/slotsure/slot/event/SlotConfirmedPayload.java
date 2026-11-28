package com.slotsure.slot.event;

import java.time.LocalDate;

/** Payload of slot.confirmed (slot-service to order-service and dispatch-service). */
public record SlotConfirmedPayload(
        String orderId,
        String holdId,
        String slotId,
        String h3Cell,
        String areaName,
        LocalDate date,
        int startHour,
        int endHour) {
}
