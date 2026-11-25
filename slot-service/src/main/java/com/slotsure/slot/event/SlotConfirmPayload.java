package com.slotsure.slot.event;

/** Payload of slot.confirm (order-service to slot-service). */
public record SlotConfirmPayload(String orderId, String holdId) {
}
