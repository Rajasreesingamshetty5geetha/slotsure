package com.slotsure.slot.event;

/** Payload of slot.confirmed (slot-service to order-service). */
public record SlotConfirmedPayload(String orderId, String holdId, String slotId, String h3Cell) {
}
