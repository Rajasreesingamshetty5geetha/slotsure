package com.slotsure.slot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.slot.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Wraps a payload in the contract envelope and sends it as a JSON string. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String topic, String key, String correlationId, Object payload) {
        EventEnvelope envelope = EventEnvelope.create(topic, correlationId, objectMapper.valueToTree(payload));
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + topic + " event", e);
        }
        kafkaTemplate.send(topic, key, json).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish {} eventId={} key={}", topic, envelope.eventId(), key, error);
            } else {
                log.info("Published {} eventId={} key={}", topic, envelope.eventId(), key);
            }
        });
    }
}
