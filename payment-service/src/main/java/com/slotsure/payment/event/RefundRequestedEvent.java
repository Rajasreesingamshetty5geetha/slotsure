package com.slotsure.payment.event;

import java.math.BigDecimal;

/** Payload of refund.requested (order → payment). */
public record RefundRequestedEvent(String orderId, BigDecimal amount, String reason) {
}
