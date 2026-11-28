package com.slotsure.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.dispatch.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Wraps a payload in the contract envelope and sends it as a JSON string, keyed by orderId.
 * Inside a transaction the send waits for the commit, so a rolled-back change never produces an event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String topic, String orderId, Object payload) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(UUID.randomUUID().toString(), topic, Instant.now(),
                orderId, payload);
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + topic + " for orderId=" + orderId, e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, orderId, json, envelope.eventId());
                }
            });
        } else {
            send(topic, orderId, json, envelope.eventId());
        }
    }

    private void send(String topic, String orderId, String json, String eventId) {
        kafkaTemplate.send(topic, orderId, json).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish {} eventId={} orderId={}", topic, eventId, orderId, error);
            } else {
                log.info("Published {} eventId={} orderId={}", topic, eventId, orderId);
            }
        });
    }
}
