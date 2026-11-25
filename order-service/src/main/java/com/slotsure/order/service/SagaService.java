package com.slotsure.order.service;

import com.slotsure.order.config.TopicProperties;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.ProcessedEvent;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.entity.SagaStep;
import com.slotsure.order.event.PaymentCapturedPayload;
import com.slotsure.order.event.PaymentFailedPayload;
import com.slotsure.order.event.RefundCompletedPayload;
import com.slotsure.order.event.RefundRequestedPayload;
import com.slotsure.order.event.SlotConfirmFailedPayload;
import com.slotsure.order.event.SlotConfirmPayload;
import com.slotsure.order.event.SlotConfirmedPayload;
import com.slotsure.order.event.SlotReleasePayload;
import com.slotsure.order.repository.OrderRepository;
import com.slotsure.order.repository.ProcessedEventRepository;
import com.slotsure.order.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Saga orchestrator. Each handler runs in one transaction: the processed_events insert,
 * the order/saga changes and the after-commit registration of outgoing events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaService {

    static final String PAYMENT_FAILED_REASON = "PAYMENT_FAILED";

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EventPublisher eventPublisher;
    private final TopicProperties topics;

    @Transactional
    public void onPaymentCaptured(String eventId, PaymentCapturedPayload payload) {
        if (!markProcessed(eventId, topics.paymentCaptured(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findActiveOrder(payload.orderId(), topics.paymentCaptured(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        SagaInstance saga = loadSaga(order.getId());
        saga.moveTo(SagaStep.SLOT_CONFIRMING);
        log.info("Payment captured orderId={} paymentId={} gateway={} -> sagaStep=SLOT_CONFIRMING",
                order.getId(), payload.paymentId(), payload.gateway());

        eventPublisher.publishAfterCommit(topics.slotConfirm(), order.getId(),
                new SlotConfirmPayload(order.getId(), order.getHoldId()));
    }

    @Transactional
    public void onPaymentFailed(String eventId, PaymentFailedPayload payload) {
        if (!markProcessed(eventId, topics.paymentFailed(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findActiveOrder(payload.orderId(), topics.paymentFailed(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        SagaInstance saga = loadSaga(order.getId());

        order.moveTo(OrderStatus.FAILED);
        saga.moveTo(SagaStep.COMPENSATING);
        saga.recordError(payload.reason());
        log.info("Payment failed orderId={} reason={} -> status=FAILED sagaStep=COMPENSATING",
                order.getId(), payload.reason());

        eventPublisher.publishAfterCommit(topics.slotRelease(), order.getId(),
                new SlotReleasePayload(order.getId(), order.getHoldId(), PAYMENT_FAILED_REASON));

        saga.moveTo(SagaStep.DONE);
        log.info("Saga finished orderId={} status=FAILED sagaStep=DONE (slot release requested)", order.getId());
    }

    @Transactional
    public void onSlotConfirmed(String eventId, SlotConfirmedPayload payload) {
        if (!markProcessed(eventId, topics.slotConfirmed(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findActiveOrder(payload.orderId(), topics.slotConfirmed(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        SagaInstance saga = loadSaga(order.getId());

        order.moveTo(OrderStatus.CONFIRMED);
        saga.moveTo(SagaStep.DONE);
        log.info("Slot confirmed orderId={} holdId={} slotId={} -> status=CONFIRMED sagaStep=DONE",
                order.getId(), payload.holdId(), payload.slotId());
    }

    @Transactional
    public void onSlotConfirmFailed(String eventId, SlotConfirmFailedPayload payload) {
        if (!markProcessed(eventId, topics.slotConfirmFailed(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findActiveOrder(payload.orderId(), topics.slotConfirmFailed(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        SagaInstance saga = loadSaga(order.getId());

        saga.moveTo(SagaStep.COMPENSATING);
        saga.recordError(payload.reason());
        log.info("Slot confirm failed orderId={} holdId={} reason={} -> sagaStep=COMPENSATING, requesting refund",
                order.getId(), payload.holdId(), payload.reason());

        eventPublisher.publishAfterCommit(topics.refundRequested(), order.getId(),
                new RefundRequestedPayload(order.getId(), order.getAmount(), payload.reason()));
    }

    @Transactional
    public void onRefundCompleted(String eventId, RefundCompletedPayload payload) {
        if (!markProcessed(eventId, topics.refundCompleted(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findActiveOrder(payload.orderId(), topics.refundCompleted(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        SagaInstance saga = loadSaga(order.getId());

        order.moveTo(OrderStatus.CANCELLED);
        saga.moveTo(SagaStep.DONE);
        log.info("Refund completed orderId={} amount={} -> status=CANCELLED sagaStep=DONE",
                order.getId(), payload.amount());
    }

    /**
     * Records the eventId in processed_events in the current transaction.
     * Returns false (and logs) when the event was already processed.
     * The primary key on event_id also protects against two concurrent deliveries.
     */
    private boolean markProcessed(String eventId, String eventType, String orderId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate {} eventId={} orderId={}", eventType, eventId, orderId);
            return false;
        }
        processedEventRepository.save(new ProcessedEvent(eventId));
        return true;
    }

    /** Returns the order only if it exists and is still PENDING; otherwise logs and returns empty. */
    private Optional<Order> findActiveOrder(String orderId, String eventType, String eventId) {
        Optional<Order> order = orderRepository.findById(orderId);
        if (order.isEmpty()) {
            log.warn("Ignoring {} eventId={} for unknown orderId={}", eventType, eventId, orderId);
            return Optional.empty();
        }
        if (order.get().getStatus().isFinal()) {
            log.warn("Ignoring {} eventId={} for orderId={} already in final status {}",
                    eventType, eventId, orderId, order.get().getStatus());
            return Optional.empty();
        }
        return order;
    }

    private SagaInstance loadSaga(String orderId) {
        return sagaRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("No saga instance for orderId=" + orderId));
    }
}
