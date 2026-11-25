package com.slotsure.payment.event;

import java.math.BigDecimal;

/** Payload of refund.completed (payment → order). */
public record RefundCompletedEvent(String orderId, BigDecimal amount) {
}
