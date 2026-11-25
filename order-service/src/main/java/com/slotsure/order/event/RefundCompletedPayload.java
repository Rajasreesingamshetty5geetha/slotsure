package com.slotsure.order.event;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record RefundCompletedPayload(String orderId, BigDecimal amount) {

    public RefundCompletedPayload {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
