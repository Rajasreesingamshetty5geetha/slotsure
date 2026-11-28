package com.slotsure.dispatch.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.dispatch.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: parses the envelope and payload, then hands over to AssignmentService.
 * A malformed message goes straight to slot.confirmed.DLT; other failures are retried twice first.
 * Unknown JSON fields are ignored (spring.jackson.deserialization.fail-on-unknown-properties: false).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchEventListener {

    private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final AssignmentService assignmentService;

    @KafkaListener(topics = "${slotsure.topics.slot-confirmed}")
    public void onSlotConfirmed(String message) {
        EventEnvelope<JsonNode> envelope;
        SlotConfirmedPayload payload;
        try {
            envelope = objectMapper.readValue(message, ENVELOPE_TYPE);
            if (envelope.eventId() == null || envelope.payload() == null || envelope.payload().isNull()) {
                throw new IllegalArgumentException("Envelope without eventId or payload: " + message);
            }
            payload = objectMapper.treeToValue(envelope.payload(), SlotConfirmedPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Message is not a valid slot.confirmed envelope: " + message, e);
        }
        if (payload.orderId() == null) {
            throw new IllegalArgumentException("slot.confirmed eventId=" + envelope.eventId() + " has no orderId");
        }
        log.info("Received slot.confirmed eventId={} orderId={} area={}", envelope.eventId(), payload.orderId(),
                payload.areaName());
        assignmentService.assign(envelope.eventId(), payload);
    }
}
