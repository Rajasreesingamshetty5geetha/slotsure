package com.slotsure.order.dto;

import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.SagaStep;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record OrderResponse(String orderId,
                            String holdId,
                            BigDecimal amount,
                            OrderStatus status,
                            SagaStep sagaStep,
                            Instant updatedAt) {

    public OrderResponse {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
