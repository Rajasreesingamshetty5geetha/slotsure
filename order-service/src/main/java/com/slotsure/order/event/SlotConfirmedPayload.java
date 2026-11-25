package com.slotsure.order.event;

public record SlotConfirmedPayload(String orderId, String holdId, String slotId, String h3Cell) {
}
