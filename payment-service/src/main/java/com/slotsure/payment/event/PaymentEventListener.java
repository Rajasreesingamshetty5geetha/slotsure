package com.slotsure.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point. Parses the envelope and hands the payload to PaymentService.
 * Any exception goes to the DefaultErrorHandler (retry, then &lt;topic&gt;.DLT).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

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

    private EventEnvelope<JsonNode> readEnvelope(String message) throws JsonProcessingException {
        EventEnvelope<JsonNode> envelope = objectMapper.readValue(message, ENVELOPE_TYPE);
        if (envelope.eventId() == null || envelope.payload() == null || envelope.payload().isNull()) {
            throw new IllegalArgumentException("Envelope is missing eventId or payload");
        }
        return envelope;
    }
}
