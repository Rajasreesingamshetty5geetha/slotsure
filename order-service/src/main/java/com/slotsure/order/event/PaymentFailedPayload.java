package com.slotsure.order.event;

public record PaymentFailedPayload(String orderId, String reason) {
}
