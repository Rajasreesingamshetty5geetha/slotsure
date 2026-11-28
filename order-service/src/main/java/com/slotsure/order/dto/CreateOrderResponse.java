package com.slotsure.order.dto;

import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.entity.PaymentMode;

public record CreateOrderResponse(String orderId, OrderStatus status, PaymentMode paymentMode,
                                  PaymentChoice paymentChoice) {
}
