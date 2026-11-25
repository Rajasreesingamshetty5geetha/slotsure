package com.slotsure.payment.event;

import java.math.BigDecimal;

/** Payload of payment.captured (payment → order). */
public record PaymentCapturedEvent(String orderId, String paymentId, String gateway, BigDecimal amount) {
}
