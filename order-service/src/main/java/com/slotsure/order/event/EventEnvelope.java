package com.slotsure.order.event;

import java.time.Instant;

/**
 * Wrapper around every Kafka message value (CONTRACT 5.2). correlationId is the orderId.
 * Publishing uses the payload record itself (a JsonNode would strip trailing zeros from money);
 * consuming reads the payload as JsonNode first and then converts it to the right record.
 */
public record EventEnvelope<T>(String eventId,
                               String eventType,
                               Instant occurredAt,
                               String correlationId,
                               T payload) {
}
