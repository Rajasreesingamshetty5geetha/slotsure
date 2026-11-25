package com.slotsure.order.dto;

import java.math.BigDecimal;

public record CreateOrderRequest(String customerId, String holdId, BigDecimal amount) {
}
