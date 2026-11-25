package com.slotsure.payment.event;

import java.time.Instant;

/**
 * Wrapper around every Kafka message value (CONTRACT 5.2).
 * Outgoing: T is the payload record, so BigDecimal amounts keep their 2 decimals.
 * Incoming: T is JsonNode, converted to the payload record by the listener.
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        T payload) {
}
