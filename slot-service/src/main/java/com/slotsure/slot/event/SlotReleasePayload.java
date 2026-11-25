package com.slotsure.slot.event;

/** Payload of slot.release (order-service to slot-service). */
public record SlotReleasePayload(String orderId, String holdId, String reason) {
}
