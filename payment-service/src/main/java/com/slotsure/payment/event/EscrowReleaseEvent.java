package com.slotsure.payment.event;

import java.math.BigDecimal;

/**
 * Payload of escrow.release (order -> payment), sent when a PREPAID or ONLINE order is delivered.
 * customerId is an extra field order-service adds; it may be missing from other producers.
 */
public record EscrowReleaseEvent(String orderId, BigDecimal amount, String customerId) {
}
