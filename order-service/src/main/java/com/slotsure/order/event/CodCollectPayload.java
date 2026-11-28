package com.slotsure.order.event;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * cod.collect (order-service to payment-service), sent for CASH orders on delivery.
 * customerId goes beyond the contract payload (consumers ignore unknown fields).
 */
public record CodCollectPayload(String orderId, BigDecimal amount, String customerId) {

    public CodCollectPayload {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
