package com.slotsure.payment.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final EventPublisher publisher = new EventPublisher(kafkaTemplate, objectMapper);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void writesContractEnvelopeKeyedByOrderId() throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publish("payment.captured", "payment.captured", "order-1",
                new PaymentCapturedEvent("order-1", "pay-1", "B", new BigDecimal("250.00")));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("payment.captured"), eq("order-1"), json.capture());
        JsonNode node = objectMapper.readTree(json.getValue());
        assertThat(node.get("eventId").asText()).hasSize(36);
        assertThat(node.get("eventType").asText()).isEqualTo("payment.captured");
        assertThat(node.get("occurredAt").asText()).endsWith("Z");
        assertThat(node.get("correlationId").asText()).isEqualTo("order-1");
        assertThat(node.get("payload").get("paymentId").asText()).isEqualTo("pay-1");
        assertThat(json.getValue()).contains("\"amount\":250.00");
    }

    @Test
    void insideTransaction_sendsOnlyAfterCommit() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish("payment.failed", "payment.failed", "order-2", new PaymentFailedEvent("order-2", "x"));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(kafkaTemplate).send(eq("payment.failed"), eq("order-2"), anyString());
    }
}
