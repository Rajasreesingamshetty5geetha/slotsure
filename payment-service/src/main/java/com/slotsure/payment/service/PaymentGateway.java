package com.slotsure.payment.service;

import java.math.BigDecimal;

/**
 * Strategy for a payment provider. The only interface-based abstraction in this service.
 */
public interface PaymentGateway {

    String name();

    GatewayResult charge(String orderId, BigDecimal amount);
}
