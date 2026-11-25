package com.slotsure.payment.event;

/** Payload of payment.failed (payment → order). */
public record PaymentFailedEvent(String orderId, String reason) {
}
