package com.slotsure.dispatch.service;

import com.slotsure.dispatch.dto.CompletionResponse;
import com.slotsure.dispatch.entity.Assignment;
import com.slotsure.dispatch.entity.AssignmentStatus;
import com.slotsure.dispatch.event.DeliveryAssignedPayload;
import com.slotsure.dispatch.event.DeliveryCompletedPayload;
import com.slotsure.dispatch.event.SlotConfirmedPayload;
import com.slotsure.dispatch.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AssignmentServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 29);

    private final InMemoryStore store = new InMemoryStore();
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final AssignmentService service = new AssignmentService(store.agentRepository, store.assignmentRepository,
            store.processedEventRepository, eventPublisher, InMemoryStore.PROPERTIES);

    @BeforeEach
    void setUp() {
        store.addAgent("a-kor-1", "Ravi Kumar", "Koramangala");
        store.addAgent("a-kor-2", "Suresh Gowda", "Koramangala");
        store.addAgent("a-hsr", "Manjunath R", "HSR Layout");
        store.addAgent("a-heb", "Rahul Sharma", "Hebbal");
    }

    @Test
    void prefersAnAgentFromTheOrdersArea() {
        String orderId = assign("HSR Layout", 18);

        assertThat(store.assignments.get(orderId).getAgentId()).isEqualTo("a-hsr");
        assertThat(store.assignments.get(orderId).getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq("delivery.assigned"), eq(orderId), payload.capture());
        assertThat(payload.getValue()).isEqualTo(
                new DeliveryAssignedPayload(orderId, "a-hsr", "Manjunath R", "+91 98450 00000", DATE, 18));
    }

    @Test
    void spreadsAnAreasOrdersOverItsLeastLoadedAgents() {
        String first = assign("Koramangala", 19);
        String second = assign("Koramangala", 19);

        assertThat(store.assignments.get(first).getAgentId()).isNotEqualTo(store.assignments.get(second).getAgentId());
        assertThat(store.assignments.get(first).getAgentId()).startsWith("a-kor");
        assertThat(store.assignments.get(second).getAgentId()).startsWith("a-kor");
    }

    @Test
    void respectsThePerHourCapThenUsesAnotherArea() {
        IntStream.range(0, 6).forEach(i -> assign("HSR Layout", 18));
        assertThat(store.assignments.values()).allMatch(a -> a.getAgentId().equals("a-hsr"));

        String seventh = assign("HSR Layout", 18);

        assertThat(store.assignments.get(seventh).getAgentId()).isNotEqualTo("a-hsr");
    }

    @Test
    void theCapIsPerHour() {
        IntStream.range(0, 6).forEach(i -> assign("HSR Layout", 18));

        assertThat(store.assignments.get(assign("HSR Layout", 19)).getAgentId()).isEqualTo("a-hsr");
    }

    @Test
    void anAreaWithoutAgentsGetsTheLeastLoadedAgentOverall() {
        assign("Koramangala", 18);
        assign("HSR Layout", 18);
        assign("Hebbal", 18);

        String orderId = assign("Whitefield", 18);

        assertThat(store.assignments.get(orderId).getAgentId()).isEqualTo("a-kor-2");
    }

    @Test
    void whenEveryoneIsFullTheLeastLoadedAgentIsOverbooked() {
        IntStream.range(0, 24).forEach(i -> assign("Hebbal", 20));
        assertThat(store.assignments).hasSize(24);

        String orderId = assign("Hebbal", 20);

        assertThat(store.assignments.get(orderId)).isNotNull();
    }

    @Test
    void inactiveAgentsAreNotUsed() {
        store.agents.get("a-hsr").setActive(false);

        assertThat(store.assignments.get(assign("HSR Layout", 18)).getAgentId()).isNotEqualTo("a-hsr");
    }

    @Test
    void aDuplicateEventIsSkipped() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        SlotConfirmedPayload payload = confirmed(orderId, "HSR Layout", 18);

        service.assign(eventId, payload);
        service.assign(eventId, payload);

        assertThat(store.assignments).hasSize(1);
        verify(eventPublisher, times(1)).publish(eq("delivery.assigned"), eq(orderId), any());
    }

    @Test
    void aSecondConfirmationOfTheSameOrderKeepsTheFirstAgent() {
        String orderId = UUID.randomUUID().toString();
        service.assign(UUID.randomUUID().toString(), confirmed(orderId, "HSR Layout", 18));

        service.assign(UUID.randomUUID().toString(), confirmed(orderId, "Hebbal", 20));

        assertThat(store.assignments).hasSize(1);
        assertThat(store.assignments.get(orderId).getAgentId()).isEqualTo("a-hsr");
    }

    @Test
    void aPreV2ConfirmationWithoutASlotTimeIsNotAssigned() {
        String orderId = UUID.randomUUID().toString();

        service.assign(UUID.randomUUID().toString(),
                new SlotConfirmedPayload(orderId, "hold-1", "slot-1", "89618925c4bffff", null, null, null, null));

        assertThat(store.assignments).isEmpty();
        verify(eventPublisher, times(0)).publish(anyString(), anyString(), any());
    }

    @Test
    void completeIsIdempotent() {
        String orderId = assign("Koramangala", 18);

        CompletionResponse first = service.complete(orderId, "key-1");
        CompletionResponse second = service.complete(orderId, "key-2");

        assertThat(first.orderId()).isEqualTo(orderId);
        assertThat(first.deliveredAt()).isNotNull();
        assertThat(second).isEqualTo(first);
        Assignment assignment = store.assignments.get(orderId);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publish(eq("delivery.completed"), eq(orderId), payload.capture());
        assertThat(payload.getAllValues()).containsOnly(
                new DeliveryCompletedPayload(orderId, assignment.getAgentId(), first.deliveredAt()));
    }

    @Test
    void completingAnUnknownOrderIsNotAssigned() {
        assertThatThrownBy(() -> service.complete("nope", "key-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("NOT_ASSIGNED");
    }

    @Test
    void completeNeedsAnIdempotencyKey() {
        String orderId = assign("Koramangala", 18);

        assertThatThrownBy(() -> service.complete(orderId, " "))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("BAD_REQUEST");
        verify(eventPublisher, times(0)).publish(eq("delivery.completed"), anyString(), any());
    }

    private String assign(String area, int hour) {
        String orderId = UUID.randomUUID().toString();
        service.assign(UUID.randomUUID().toString(), confirmed(orderId, area, hour));
        return orderId;
    }

    private static SlotConfirmedPayload confirmed(String orderId, String area, int hour) {
        return new SlotConfirmedPayload(orderId, "hold-1", "slot-1", "89618925c4bffff", area, DATE, hour, hour + 1);
    }
}
