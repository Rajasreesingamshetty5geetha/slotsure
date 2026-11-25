package com.slotsure.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Wraps a payload in the contract envelope and sends it as a JSON string.
 * Inside a database transaction the send is deferred until after commit, so a rolled-back
 * change never produces an event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String topic, String eventType, String orderId, Object payload) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                orderId,
                payload);
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + eventType + " for orderId=" + orderId, e);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, orderId, json, envelope);
                }
            });
        } else {
            send(topic, orderId, json, envelope);
        }
    }

    private void send(String topic, String key, String json, EventEnvelope<Object> envelope) {
        kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} orderId={} eventId={}",
                        envelope.eventType(), envelope.correlationId(), envelope.eventId(), ex);
            } else {
                log.info("Published {} orderId={} eventId={}",
                        envelope.eventType(), envelope.correlationId(), envelope.eventId());
            }
        });
    }
}
