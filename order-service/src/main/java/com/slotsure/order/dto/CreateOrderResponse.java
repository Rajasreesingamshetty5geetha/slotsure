package com.slotsure.order.dto;

import com.slotsure.order.entity.OrderStatus;

public record CreateOrderResponse(String orderId, OrderStatus status) {
}
