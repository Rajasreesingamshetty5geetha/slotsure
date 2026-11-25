package com.slotsure.order.event;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record RefundRequestedPayload(String orderId, BigDecimal amount, String reason) {

    public RefundRequestedPayload {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
