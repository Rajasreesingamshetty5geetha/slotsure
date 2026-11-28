package com.slotsure.order.event;

import java.time.LocalDate;

/** slot.confirmed (CONTRACT V2.5). The slot's own area and time win over what the client sent. */
public record SlotConfirmedPayload(String orderId,
                                   String holdId,
                                   String slotId,
                                   String h3Cell,
                                   String areaName,
                                   LocalDate date,
                                   Integer startHour,
                                   Integer endHour) {
}
