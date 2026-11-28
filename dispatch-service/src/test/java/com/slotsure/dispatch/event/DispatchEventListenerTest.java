package com.slotsure.dispatch.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.dispatch.service.AssignmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DispatchEventListenerTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final AssignmentService assignmentService = mock(AssignmentService.class);
    private final DispatchEventListener listener = new DispatchEventListener(objectMapper, assignmentService);

    @Test
    void readsSlotConfirmedFromSlotServiceAndIgnoresUnknownFields() {
        String json = """
                {"eventId":"0b6f6a1e-3f0e-4c7a-9a4a-6f1f9d2f6e11","eventType":"slot.confirmed",
                 "occurredAt":"2026-11-28T17:13:23.773381800Z","correlationId":"order-1","schemaVersion":2,
                 "payload":{"orderId":"order-1","holdId":"hold-1","slotId":"slot-1","h3Cell":"89618925c4bffff",
                            "areaName":"Koramangala","date":"2026-11-29","startHour":18,"endHour":19,"surge":true}}
                """;

        listener.onSlotConfirmed(json);

        ArgumentCaptor<SlotConfirmedPayload> payload = ArgumentCaptor.forClass(SlotConfirmedPayload.class);
        verify(assignmentService).assign(eq("0b6f6a1e-3f0e-4c7a-9a4a-6f1f9d2f6e11"), payload.capture());
        assertThat(payload.getValue()).isEqualTo(new SlotConfirmedPayload("order-1", "hold-1", "slot-1",
                "89618925c4bffff", "Koramangala", LocalDate.of(2026, 11, 29), 18, 19));
    }

    @Test
    void aMalformedMessageIsRejectedWithoutAssigning() {
        assertThatThrownBy(() -> listener.onSlotConfirmed("{not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.onSlotConfirmed("{\"eventType\":\"slot.confirmed\",\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(assignmentService, never()).assign(anyString(), org.mockito.ArgumentMatchers.any());
    }
}
