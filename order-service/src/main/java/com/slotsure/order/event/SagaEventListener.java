package com.slotsure.order.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.order.service.SagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: parses the envelope and payload, then hands over to SagaService.
 * Any exception goes to the DefaultErrorHandler (2 retries, then topic.DLT).
 * Unknown JSON fields are ignored (spring.jackson.deserialization.fail-on-unknown-properties: false).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaEventListener {

    private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE_TYPE = new TypeReference<>() {
    };

    private final SagaService sagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${slotsure.topics.payment-captured}")
    public void onPaymentCaptured(String message) {
        EventEnvelope<JsonNode> envelope = parse(message);
        sagaService.onPaymentCaptured(envelope.eventId(), payload(envelope, PaymentCapturedPayload.class));
    }

    @KafkaListener(topics = "${slotsure.topics.payment-failed}")
    public void onPaymentFailed(String message) {
        EventEnvelope<JsonNode> envelope = parse(message);
        sagaService.onPaymentFailed(envelope.eventId(), payload(envelope, PaymentFailedPayload.class));
    }

    @KafkaListener(topics = "${slotsure.topics.slot-confirmed}")
    public void onSlotConfirmed(String message) {
        EventEnvelope<JsonNode> envelope = parse(message);
        sagaService.onSlotConfirmed(envelope.eventId(), payload(envelope, SlotConfirmedPayload.class));
    }

    @KafkaListener(topics = "${slotsure.topics.slot-confirm-failed}")
    public void onSlotConfirmFailed(String message) {
        EventEnvelope<JsonNode> envelope = parse(message);
        sagaService.onSlotConfirmFailed(envelope.eventId(), payload(envelope, SlotConfirmFailedPayload.class));
    }

    @KafkaListener(topics = "${slotsure.topics.refund-completed}")
    public void onRefundCompleted(String message) {
        EventEnvelope<JsonNode> envelope = parse(message);
        sagaService.onRefundCompleted(envelope.eventId(), payload(envelope, RefundCompletedPayload.class));
    }

    @KafkaListener(topics = "${slotsure.topics.delivery-assigned}")
    public void onDeliveryAssigned(String message) {
        EventEnvelope<JsonNode> envelope = parse(message);
        sagaService.onDeliveryAssigned(envelope.eventId(), payload(envelope, DeliveryAssignedPayload.class));
    }

    @KafkaListener(topics = "${slotsure.topics.delivery-completed}")
    public void onDeliveryCompleted(String message) {
        EventEnvelope<JsonNode> envelope = parse(message);
        sagaService.onDeliveryCompleted(envelope.eventId(), payload(envelope, DeliveryCompletedPayload.class));
    }

    private EventEnvelope<JsonNode> parse(String message) {
        try {
            EventEnvelope<JsonNode> envelope = objectMapper.readValue(message, ENVELOPE_TYPE);
            if (envelope.eventId() == null || envelope.payload() == null) {
                throw new IllegalArgumentException("Envelope without eventId or payload: " + message);
            }
            log.debug("Received {} eventId={} correlationId={}",
                    envelope.eventType(), envelope.eventId(), envelope.correlationId());
            return envelope;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Message is not a valid event envelope: " + message, e);
        }
    }

    private <T> T payload(EventEnvelope<JsonNode> envelope, Class<T> type) {
        try {
            return objectMapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid payload for " + type.getSimpleName()
                    + " eventId=" + envelope.eventId(), e);
        }
    }
}
