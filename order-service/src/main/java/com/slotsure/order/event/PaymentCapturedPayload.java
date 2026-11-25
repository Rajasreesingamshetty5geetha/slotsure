package com.slotsure.order.event;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PaymentCapturedPayload(String orderId, String paymentId, String gateway, BigDecimal amount) {

    public PaymentCapturedPayload {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
