package com.slotsure.slot.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.slot.service.EventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventEnvelopeJsonTest {

    // Same defaults as Spring Boot's ObjectMapper: ISO-8601 dates, unknown properties ignored.
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    @SuppressWarnings("unchecked")
    void publishesContractEnvelopeAsJsonString() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        when(kafkaTemplate.send(eq("slot.released"), eq("hold-1"), json.capture()))
                .thenReturn(new CompletableFuture<>());

        new EventPublisher(kafkaTemplate, objectMapper).publish("slot.released", "hold-1", "hold-1",
                new SlotReleasedPayload("hold-1", "slot-1", SlotReleasedPayload.EXPIRED));

        verify(kafkaTemplate).send(eq("slot.released"), eq("hold-1"), json.capture());
        JsonNode node = objectMapper.readTree(json.getValue());
        assertThat(node.get("eventId").asText()).hasSize(36);
        assertThat(node.get("eventType").asText()).isEqualTo("slot.released");
        assertThat(node.get("occurredAt").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(node.get("correlationId").asText()).isEqualTo("hold-1");
        assertThat(node.get("payload").get("holdId").asText()).isEqualTo("hold-1");
        assertThat(node.get("payload").get("slotId").asText()).isEqualTo("slot-1");
        assertThat(node.get("payload").get("reason").asText()).isEqualTo("EXPIRED");
    }

    @Test
    void readsIncomingConfirmFromAnotherService() throws Exception {
        String json = """
                {"eventId":"0b6f6a1e-3f0e-4c7a-9a4a-6f1f9d2f6e11","eventType":"slot.confirm",
                 "occurredAt":"2026-11-26T01:00:00Z","correlationId":"order-1",
                 "payload":{"orderId":"order-1","holdId":"hold-1","extra":"ignored"}}
                """;

        EventEnvelope envelope = objectMapper.readValue(json, EventEnvelope.class);
        SlotConfirmPayload payload = objectMapper.treeToValue(envelope.payload(), SlotConfirmPayload.class);

        assertThat(envelope.eventId()).isEqualTo("0b6f6a1e-3f0e-4c7a-9a4a-6f1f9d2f6e11");
        assertThat(envelope.occurredAt()).hasToString("2026-11-26T01:00:00Z");
        assertThat(payload).isEqualTo(new SlotConfirmPayload("order-1", "hold-1"));
    }
}
