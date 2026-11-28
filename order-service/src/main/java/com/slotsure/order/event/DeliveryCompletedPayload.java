package com.slotsure.order.event;

import java.time.Instant;

/** delivery.completed (dispatch-service to order-service). */
public record DeliveryCompletedPayload(String orderId, String agentId, Instant deliveredAt) {
}
