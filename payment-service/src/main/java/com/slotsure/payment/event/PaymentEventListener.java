package com.slotsure.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.payment.service.PaymentService;
import com.slotsure.payment.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point. Parses the envelope and hands the payload to PaymentService or SettlementService.
 * Any exception goes to the DefaultErrorHandler (retry, then &lt;topic&gt;.DLT).
 * Unknown JSON fields are ignored (spring.jackson.deserialization.fail-on-unknown-properties: false).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final SettlementService settlementService;

    @KafkaListener(topics = "${slotsure.topics.payment-requested}")
    public void onPaymentRequested(String message) throws JsonProcessingException {
        EventEnvelope<JsonNode> envelope = readEnvelope(message);
        PaymentRequestedEvent request = objectMapper.treeToValue(envelope.payload(), PaymentRequestedEvent.class);
        log.info("Received payment.requested orderId={} eventId={}", request.orderId(), envelope.eventId());
        paymentService.handlePaymentRequested(envelope.eventId(), request);
    }

    @KafkaListener(topics = "${slotsure.topics.refund-requested}")
    public void onRefundRequested(String message) throws JsonProcessingException {
        EventEnvelope<JsonNode> envelope = readEnvelope(message);
        RefundRequestedEvent request = objectMapper.treeToValue(envelope.payload(), RefundRequestedEvent.class);
        log.info("Received refund.requested orderId={} eventId={}", request.orderId(), envelope.eventId());
        paymentService.handleRefundRequested(envelope.eventId(), request);
    }

    @KafkaListener(topics = "${slotsure.topics.escrow-release}")
    public void onEscrowRelease(String message) throws JsonProcessingException {
        EventEnvelope<JsonNode> envelope = readEnvelope(message);
        EscrowReleaseEvent event = objectMapper.treeToValue(envelope.payload(), EscrowReleaseEvent.class);
        log.info("Received escrow.release orderId={} eventId={}", event.orderId(), envelope.eventId());
        settlementService.handleEscrowRelease(envelope.eventId(), event);
    }

    @KafkaListener(topics = "${slotsure.topics.cod-collect}")
    public void onCodCollect(String message) throws JsonProcessingException {
        EventEnvelope<JsonNode> envelope = readEnvelope(message);
        CodCollectEvent event = objectMapper.treeToValue(envelope.payload(), CodCollectEvent.class);
        log.info("Received cod.collect orderId={} eventId={}", event.orderId(), envelope.eventId());
        settlementService.handleCodCollect(envelope.eventId(), event);
    }

    private EventEnvelope<JsonNode> readEnvelope(String message) throws JsonProcessingException {
        EventEnvelope<JsonNode> envelope = objectMapper.readValue(message, ENVELOPE_TYPE);
        if (envelope.eventId() == null || envelope.payload() == null || envelope.payload().isNull()) {
            throw new IllegalArgumentException("Envelope is missing eventId or payload");
        }
        return envelope;
    }
}
