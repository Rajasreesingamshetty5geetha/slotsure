package com.slotsure.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.order.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes Saga events only after the surrounding database transaction has committed,
 * so a rolled-back state change never produces a message.
 *
 * DUAL WRITE: the DB commit and the Kafka send are two separate writes. If the process dies
 * between them, the event is lost and the saga stalls. To be replaced by a Transactional Outbox
 * (write the event into an outbox table in the same transaction, relay it to Kafka separately).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param topic   topic name, also used as eventType
     * @param orderId message key and correlationId
     * @param payload one of the payload records from the event package
     */
    public void publishAfterCommit(String topic, String orderId, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("publishAfterCommit must be called inside a transaction, orderId=" + orderId);
        }
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(), topic, Instant.now(), orderId, payload);
        String json = toJson(envelope);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("DB committed, sending {} eventId={} orderId={} (dual write, outbox pending)",
                        topic, envelope.eventId(), orderId);
                kafkaTemplate.send(topic, orderId, json).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} eventId={} orderId={}: event is lost (dual write)",
                                topic, envelope.eventId(), orderId, ex);
                    } else {
                        log.info("Published {} eventId={} orderId={}", topic, envelope.eventId(), orderId);
                    }
                });
            }
        });
    }

    private String toJson(EventEnvelope<Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event " + envelope.eventType(), e);
        }
    }
}
