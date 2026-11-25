package com.slotsure.payment.dto;

import com.slotsure.payment.entity.PaymentAttempt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record PaymentAttemptResponse(
        String paymentId,
        String orderId,
        String customerId,
        String gateway,
        BigDecimal amount,
        String status,
        String failureReason,
        Instant createdAt) {

    public static PaymentAttemptResponse from(PaymentAttempt a) {
        return new PaymentAttemptResponse(a.getId(), a.getOrderId(), a.getCustomerId(), a.getGateway(),
                a.getAmount().setScale(2, RoundingMode.HALF_UP), a.getStatus().name(), a.getFailureReason(), a.getCreatedAt());
    }
}
