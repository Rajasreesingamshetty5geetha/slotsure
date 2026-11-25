package com.slotsure.slot.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** The wrapper around every Kafka message value (CONTRACT 5.2). */
public record EventEnvelope(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        JsonNode payload) {

    public static EventEnvelope create(String eventType, String correlationId, JsonNode payload) {
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, Instant.now(), correlationId, payload);
    }
}
