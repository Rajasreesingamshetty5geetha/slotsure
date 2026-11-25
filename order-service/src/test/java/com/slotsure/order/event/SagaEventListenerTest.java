package com.slotsure.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.order.config.TopicProperties;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.ProcessedEvent;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.entity.SagaStep;
import com.slotsure.order.repository.OrderRepository;
import com.slotsure.order.repository.ProcessedEventRepository;
import com.slotsure.order.repository.SagaInstanceRepository;
import com.slotsure.order.service.EventPublisher;
import com.slotsure.order.service.SagaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Drives the listener with real JSON envelopes. SagaService is real; repositories are
 * in-memory Mockito stubs and the Kafka publisher is a Mockito mock.
 */
class SagaEventListenerTest {

    private static final TopicProperties TOPICS = new TopicProperties(
            "payment.requested", "payment.captured", "payment.failed",
            "slot.confirm", "slot.release", "slot.confirmed", "slot.confirm.failed",
            "refund.requested", "refund.completed");

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final SagaInstanceRepository sagaRepository = mock(SagaInstanceRepository.class);
    private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final Set<String> processedEventIds = new HashSet<>();

    private SagaEventListener listener;
    private Order order;
    private SagaInstance saga;

    @BeforeEach
    void setUp() {
        SagaService sagaService = new SagaService(
                orderRepository, sagaRepository, processedEventRepository, eventPublisher, TOPICS);
        listener = new SagaEventListener(sagaService, objectMapper);

        order = new Order("customer-1", UUID.randomUUID().toString(), new BigDecimal("25.50"), "key-1");
        saga = new SagaInstance(order.getId());
        when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(sagaRepository.findById(order.getId())).thenReturn(Optional.of(saga));

        when(processedEventRepository.existsById(anyString()))
                .thenAnswer(inv -> processedEventIds.contains(inv.<String>getArgument(0)));
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(inv -> {
            ProcessedEvent event = inv.getArgument(0);
            processedEventIds.add(event.getEventId());
            return event;
        });
    }

    @Test
    void paymentCapturedMovesSagaToSlotConfirmingAndPublishesSlotConfirm() throws Exception {
        listener.onPaymentCaptured(message(newEventId(), "payment.captured",
                new PaymentCapturedPayload(order.getId(), "pay-1", "FAKE", order.getAmount())));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saga.getStep()).isEqualTo(SagaStep.SLOT_CONFIRMING);
        verify(eventPublisher).publishAfterCommit("slot.confirm", order.getId(),
                new SlotConfirmPayload(order.getId(), order.getHoldId()));
    }

    @Test
    void paymentFailedFailsOrderReleasesSlotAndFinishesSaga() throws Exception {
        listener.onPaymentFailed(message(newEventId(), "payment.failed",
                new PaymentFailedPayload(order.getId(), "CARD_DECLINED")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
        assertThat(saga.getLastError()).isEqualTo("CARD_DECLINED");
        verify(eventPublisher).publishAfterCommit("slot.release", order.getId(),
                new SlotReleasePayload(order.getId(), order.getHoldId(), "PAYMENT_FAILED"));
    }

    @Test
    void slotConfirmedConfirmsOrderAndFinishesSaga() throws Exception {
        saga.moveTo(SagaStep.SLOT_CONFIRMING);

        listener.onSlotConfirmed(message(newEventId(), "slot.confirmed",
                new SlotConfirmedPayload(order.getId(), order.getHoldId(), "slot-1", "8928308280fffff")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void slotConfirmFailedStartsCompensationAndRequestsRefund() throws Exception {
        saga.moveTo(SagaStep.SLOT_CONFIRMING);

        listener.onSlotConfirmFailed(message(newEventId(), "slot.confirm.failed",
                new SlotConfirmFailedPayload(order.getId(), order.getHoldId(), "HOLD_EXPIRED")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saga.getStep()).isEqualTo(SagaStep.COMPENSATING);
        assertThat(saga.getLastError()).isEqualTo("HOLD_EXPIRED");
        verify(eventPublisher).publishAfterCommit("refund.requested", order.getId(),
                new RefundRequestedPayload(order.getId(), new BigDecimal("25.50"), "HOLD_EXPIRED"));
    }

    @Test
    void refundCompletedCancelsOrderAndFinishesSaga() throws Exception {
        saga.moveTo(SagaStep.SLOT_CONFIRMING);
        saga.moveTo(SagaStep.COMPENSATING);

        listener.onRefundCompleted(message(newEventId(), "refund.completed",
                new RefundCompletedPayload(order.getId(), order.getAmount())));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sameEventIdDeliveredTwiceIsProcessedOnce() throws Exception {
        String message = message(newEventId(), "payment.captured",
                new PaymentCapturedPayload(order.getId(), "pay-1", "FAKE", order.getAmount()));

        listener.onPaymentCaptured(message);
        listener.onPaymentCaptured(message);

        assertThat(saga.getStep()).isEqualTo(SagaStep.SLOT_CONFIRMING);
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
        verify(eventPublisher, times(1)).publishAfterCommit(anyString(), anyString(), any());
    }

    @Test
    void eventForUnknownOrderIsIgnored() throws Exception {
        String unknownOrderId = UUID.randomUUID().toString();

        listener.onPaymentCaptured(message(newEventId(), "payment.captured",
                new PaymentCapturedPayload(unknownOrderId, "pay-1", "FAKE", new BigDecimal("1.00"))));

        verify(sagaRepository, never()).findById(unknownOrderId);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void eventForOrderInFinalStateIsIgnored() throws Exception {
        saga.moveTo(SagaStep.SLOT_CONFIRMING);
        saga.moveTo(SagaStep.DONE);
        order.moveTo(OrderStatus.CONFIRMED);

        listener.onPaymentFailed(message(newEventId(), "payment.failed",
                new PaymentFailedPayload(order.getId(), "LATE_FAILURE")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
        verifyNoInteractions(eventPublisher);
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }

    private String message(String eventId, String eventType, Object payload) throws Exception {
        String orderId = objectMapper.valueToTree(payload).get("orderId").asText();
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                eventId, eventType, Instant.now(), orderId, payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
