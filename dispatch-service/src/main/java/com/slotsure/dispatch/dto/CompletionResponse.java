package com.slotsure.dispatch.dto;

import java.time.Instant;

/** Response of POST /api/deliveries/{orderId}/complete; the same on every call for one order. */
public record CompletionResponse(String orderId, Instant deliveredAt) {
}
