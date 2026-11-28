package com.slotsure.payment.event;

import java.math.BigDecimal;

/**
 * Payload of cod.collect (order -> payment), sent when a CASH order is delivered.
 * customerId is an extra field order-service adds; it may be missing from other producers.
 */
public record CodCollectEvent(String orderId, BigDecimal amount, String customerId) {
}
