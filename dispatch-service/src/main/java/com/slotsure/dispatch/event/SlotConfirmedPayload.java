package com.slotsure.dispatch.event;

import java.time.LocalDate;

/** slot.confirmed (slot-service to order-service and dispatch-service, CONTRACT V2.5). */
public record SlotConfirmedPayload(String orderId,
                                   String holdId,
                                   String slotId,
                                   String h3Cell,
                                   String areaName,
                                   LocalDate date,
                                   Integer startHour,
                                   Integer endHour) {
}
