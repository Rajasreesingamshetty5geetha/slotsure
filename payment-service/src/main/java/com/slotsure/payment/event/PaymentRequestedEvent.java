package com.slotsure.payment.event;

import java.math.BigDecimal;

/** Payload of payment.requested (order → payment). */
public record PaymentRequestedEvent(String orderId, String customerId, BigDecimal amount) {
}
