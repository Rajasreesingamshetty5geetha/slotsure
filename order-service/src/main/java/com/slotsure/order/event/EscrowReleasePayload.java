package com.slotsure.order.event;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * escrow.release (order-service to payment-service), sent for PREPAID and ONLINE orders on delivery.
 * customerId goes beyond the contract payload (consumers ignore unknown fields): payment-service needs
 * it to record the checkout capture of a PREPAID order that it has never seen.
 */
public record EscrowReleasePayload(String orderId, BigDecimal amount, String customerId) {

    public EscrowReleasePayload {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
