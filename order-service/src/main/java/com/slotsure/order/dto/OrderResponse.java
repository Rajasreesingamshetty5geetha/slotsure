package com.slotsure.order.dto;

import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.SagaStep;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/** One order (CONTRACT V2.6). Lists and GET /api/orders/{id} share this shape. */
public record OrderResponse(String orderId,
                            String purchaseId,
                            String holdId,
                            String customerId,
                            String itemName,
                            BigDecimal amount,
                            PaymentMode paymentMode,
                            PaymentChoice paymentChoice,
                            OrderStatus status,
                            LocalDate slotDate,
                            Integer startHour,
                            Integer endHour,
                            String address,
                            String areaName,
                            String agentName,
                            String agentPhone,
                            Instant deliveredAt,
                            Instant createdAt,
                            SagaStep sagaStep,
                            Instant updatedAt) {

    public OrderResponse {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static OrderResponse of(Order order, SagaStep sagaStep) {
        return new OrderResponse(order.getId(), order.getPurchaseId(), order.getHoldId(), order.getCustomerId(),
                order.getItemName(), order.getAmount(), order.getPaymentMode(), order.getPaymentChoice(),
                order.getStatus(), order.getSlotDate(), order.getStartHour(), order.getEndHour(),
                order.getAddress(), order.getAreaName(), order.getAgentName(), order.getAgentPhone(),
                order.getDeliveredAt(), order.getCreatedAt(), sagaStep, order.getUpdatedAt());
    }
}
