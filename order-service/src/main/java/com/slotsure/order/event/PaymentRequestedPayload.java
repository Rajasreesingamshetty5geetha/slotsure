package com.slotsure.order.event;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PaymentRequestedPayload(String orderId, String customerId, BigDecimal amount) {

    public PaymentRequestedPayload {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
