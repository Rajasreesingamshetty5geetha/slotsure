package com.slotsure.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.order.event.PaymentRequestedPayload;
import com.slotsure.order.event.RefundRequestedPayload;
import com.slotsure.order.event.SlotConfirmPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final EventPublisher publisher = new EventPublisher(kafkaTemplate, objectMapper);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsEnvelopeOnlyAfterCommit() throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit("slot.confirm", "order-1", new SlotConfirmPayload("order-1", "hold-1"));
        verifyNoInteractions(kafkaTemplate);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("slot.confirm"), eq("order-1"), value.capture());
        JsonNode json = objectMapper.readTree(value.getValue());
        assertThat(json.get("eventId").asText()).hasSize(36);
        assertThat(json.get("eventType").asText()).isEqualTo("slot.confirm");
        assertThat(json.get("occurredAt").asText()).endsWith("Z");
        assertThat(json.get("correlationId").asText()).isEqualTo("order-1");
        assertThat(json.get("payload").get("holdId").asText()).isEqualTo("hold-1");
    }

    @Test
    void moneyIsPublishedWithTwoDecimals() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit("payment.requested", "order-1",
                new PaymentRequestedPayload("order-1", "customer-1", new BigDecimal("499")));
        publisher.publishAfterCommit("refund.requested", "order-1",
                new RefundRequestedPayload("order-1", new BigDecimal("25.5"), "HOLD_EXPIRED"));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("payment.requested"), eq("order-1"), value.capture());
        assertThat(value.getValue()).contains("\"amount\":499.00");
        verify(kafkaTemplate).send(eq("refund.requested"), eq("order-1"), value.capture());
        assertThat(value.getValue()).contains("\"amount\":25.50");
    }

    @Test
    void refusesToPublishOutsideTransaction() {
        assertThatThrownBy(() -> publisher.publishAfterCommit("slot.confirm", "order-1",
                new SlotConfirmPayload("order-1", "hold-1")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(kafkaTemplate);
    }
}
