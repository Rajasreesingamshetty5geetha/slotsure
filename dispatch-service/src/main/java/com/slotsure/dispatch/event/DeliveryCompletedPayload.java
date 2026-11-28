package com.slotsure.dispatch.event;

import java.time.Instant;

/** delivery.completed (dispatch-service to order-service). */
public record DeliveryCompletedPayload(String orderId, String agentId, Instant deliveredAt) {
}
