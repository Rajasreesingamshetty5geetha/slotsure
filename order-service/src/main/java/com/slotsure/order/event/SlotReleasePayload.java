package com.slotsure.order.event;

public record SlotReleasePayload(String orderId, String holdId, String reason) {
}
