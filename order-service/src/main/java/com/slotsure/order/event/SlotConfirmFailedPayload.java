package com.slotsure.order.event;

public record SlotConfirmFailedPayload(String orderId, String holdId, String reason) {
}
