package com.slotsure.dispatch.event;

import java.time.Instant;

/**
 * Wrapper around every Kafka message value (CONTRACT 5.2). correlationId is the orderId.
 * Outgoing: T is the payload record. Incoming: T is JsonNode, converted by the listener.
 */
public record EventEnvelope<T>(String eventId,
                               String eventType,
                               Instant occurredAt,
                               String correlationId,
                               T payload) {
}
