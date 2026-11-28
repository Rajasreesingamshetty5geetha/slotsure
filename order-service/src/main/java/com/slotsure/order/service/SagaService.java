package com.slotsure.order.service;

import com.slotsure.order.config.TopicProperties;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.entity.ProcessedEvent;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.entity.SagaStep;
import com.slotsure.order.event.CodCollectPayload;
import com.slotsure.order.event.DeliveryAssignedPayload;
import com.slotsure.order.event.DeliveryCompletedPayload;
import com.slotsure.order.event.EscrowReleasePayload;
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
import com.slotsure.order.repository.PurchaseRepository;
import com.slotsure.order.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Saga orchestrator and delivery tracker. Each handler runs in one transaction: the processed_events
 * insert, the order/saga/purchase changes, the notification and the after-commit outgoing events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaService {

    static final String PAYMENT_FAILED_REASON = "PAYMENT_FAILED";

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PurchaseRepository purchaseRepository;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final TopicProperties topics;

    @Transactional
    public void onPaymentCaptured(String eventId, PaymentCapturedPayload payload) {
        if (!markProcessed(eventId, topics.paymentCaptured(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findPendingOrder(payload.orderId(), topics.paymentCaptured(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        loadSaga(order.getId()).moveTo(SagaStep.SLOT_CONFIRMING);
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
        Optional<Order> found = findPendingOrder(payload.orderId(), topics.paymentFailed(), eventId);
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
        releasePurchase(order);
        notificationService.paymentFailed(order);

        saga.moveTo(SagaStep.DONE);
        log.info("Saga finished orderId={} status=FAILED sagaStep=DONE (slot release requested)", order.getId());
    }

    @Transactional
    public void onSlotConfirmed(String eventId, SlotConfirmedPayload payload) {
        if (!markProcessed(eventId, topics.slotConfirmed(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findPendingOrder(payload.orderId(), topics.slotConfirmed(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        SagaInstance saga = loadSaga(order.getId());

        // The slot is the source of truth for where and when.
        if (payload.areaName() != null) {
            order.setAreaName(payload.areaName());
        }
        if (payload.date() != null) {
            order.setSlotDate(payload.date());
        }
        if (payload.startHour() != null) {
            order.setStartHour(payload.startHour());
            order.setEndHour(payload.endHour() != null ? payload.endHour() : payload.startHour() + 1);
        }
        order.moveTo(OrderStatus.CONFIRMED);
        saga.moveTo(SagaStep.DONE);
        purchaseOf(order).ifPresent(Purchase::markScheduled);
        notificationService.slotConfirmed(order);
        log.info("Slot confirmed orderId={} holdId={} slotId={} area={} -> status=CONFIRMED sagaStep=DONE",
                order.getId(), payload.holdId(), payload.slotId(), order.getAreaName());

        if (order.hasAgent()) {
            // delivery.assigned arrived before slot.confirmed (different topics, no ordering between them).
            order.moveTo(OrderStatus.OUT_FOR_DELIVERY);
            notificationService.agentAssigned(order);
            log.info("Agent {} was assigned earlier, orderId={} -> status=OUT_FOR_DELIVERY", order.getAgentName(), order.getId());
        }
    }

    @Transactional
    public void onSlotConfirmFailed(String eventId, SlotConfirmFailedPayload payload) {
        if (!markProcessed(eventId, topics.slotConfirmFailed(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findPendingOrder(payload.orderId(), topics.slotConfirmFailed(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        SagaInstance saga = loadSaga(order.getId());
        saga.moveTo(SagaStep.COMPENSATING);
        saga.recordError(payload.reason());

        if (order.effectivePaymentChoice().paysAtBooking()) {
            log.info("Slot confirm failed orderId={} holdId={} reason={} -> sagaStep=COMPENSATING, requesting refund",
                    order.getId(), payload.holdId(), payload.reason());
            eventPublisher.publishAfterCommit(topics.refundRequested(), order.getId(),
                    new RefundRequestedPayload(order.getId(), order.getAmount(), payload.reason()));
            return;
        }
        // Nothing was charged while booking, so there is nothing to refund.
        order.moveTo(OrderStatus.FAILED);
        saga.moveTo(SagaStep.DONE);
        releasePurchase(order);
        log.info("Slot confirm failed orderId={} holdId={} reason={} paymentChoice={} -> status=FAILED sagaStep=DONE",
                order.getId(), payload.holdId(), payload.reason(), order.getPaymentChoice());
    }

    @Transactional
    public void onRefundCompleted(String eventId, RefundCompletedPayload payload) {
        if (!markProcessed(eventId, topics.refundCompleted(), payload.orderId())) {
            return;
        }
        Optional<Order> found = findPendingOrder(payload.orderId(), topics.refundCompleted(), eventId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        order.moveTo(OrderStatus.CANCELLED);
        loadSaga(order.getId()).moveTo(SagaStep.DONE);
        releasePurchase(order);
        log.info("Refund completed orderId={} amount={} -> status=CANCELLED sagaStep=DONE",
                order.getId(), payload.amount());
    }

    @Transactional
    public void onDeliveryAssigned(String eventId, DeliveryAssignedPayload payload) {
        if (!markProcessed(eventId, topics.deliveryAssigned(), payload.orderId())) {
            return;
        }
        Order order = orderRepository.findById(payload.orderId()).orElse(null);
        if (order == null) {
            log.warn("Ignoring {} eventId={} for unknown orderId={}", topics.deliveryAssigned(), eventId, payload.orderId());
            return;
        }
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.FAILED || status == OrderStatus.CANCELLED) {
            log.warn("Ignoring agent {} for orderId={} in status {}", payload.agentName(), order.getId(), status);
            return;
        }
        order.assignAgent(payload.agentId(), payload.agentName(), payload.agentPhone());
        if (status == OrderStatus.CONFIRMED) {
            order.moveTo(OrderStatus.OUT_FOR_DELIVERY);
            notificationService.agentAssigned(order);
            log.info("Agent {} assigned orderId={} -> status=OUT_FOR_DELIVERY", payload.agentName(), order.getId());
        } else {
            log.info("Agent {} recorded for orderId={} in status {} (no status change)",
                    payload.agentName(), order.getId(), status);
        }
    }

    @Transactional
    public void onDeliveryCompleted(String eventId, DeliveryCompletedPayload payload) {
        if (!markProcessed(eventId, topics.deliveryCompleted(), payload.orderId())) {
            return;
        }
        Order order = orderRepository.findById(payload.orderId()).orElse(null);
        if (order == null) {
            log.warn("Ignoring {} eventId={} for unknown orderId={}", topics.deliveryCompleted(), eventId, payload.orderId());
            return;
        }
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.PENDING) {
            // Rolls back the processed_events insert too, so the retry (and then the DLT) sees the event again.
            throw new IllegalStateException("Order " + order.getId() + " delivered before its slot was confirmed; retrying");
        }
        if (!Set.of(OrderStatus.CONFIRMED, OrderStatus.OUT_FOR_DELIVERY).contains(status)) {
            log.warn("Ignoring delivery of orderId={} in status {}", order.getId(), status);
            return;
        }
        order.markDelivered(payload.deliveredAt() != null ? payload.deliveredAt() : Instant.now());
        purchaseOf(order).ifPresent(Purchase::markDelivered);
        notificationService.delivered(order);

        if (order.effectivePaymentChoice() == PaymentChoice.CASH) {
            eventPublisher.publishAfterCommit(topics.codCollect(), order.getId(),
                    new CodCollectPayload(order.getId(), order.getAmount(), order.getCustomerId()));
        } else {
            eventPublisher.publishAfterCommit(topics.escrowRelease(), order.getId(),
                    new EscrowReleasePayload(order.getId(), order.getAmount(), order.getCustomerId()));
        }
        log.info("Delivered orderId={} at {} paymentChoice={} -> status=DELIVERED, {} requested",
                order.getId(), order.getDeliveredAt(), order.effectivePaymentChoice(),
                order.effectivePaymentChoice() == PaymentChoice.CASH ? topics.codCollect() : topics.escrowRelease());
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
    private Optional<Order> findPendingOrder(String orderId, String eventType, String eventId) {
        Optional<Order> order = orderRepository.findById(orderId);
        if (order.isEmpty()) {
            log.warn("Ignoring {} eventId={} for unknown orderId={}", eventType, eventId, orderId);
            return Optional.empty();
        }
        if (order.get().getStatus() != OrderStatus.PENDING) {
            log.warn("Ignoring {} eventId={} for orderId={} already in status {}",
                    eventType, eventId, orderId, order.get().getStatus());
            return Optional.empty();
        }
        return order;
    }

    private Optional<Purchase> purchaseOf(Order order) {
        return order.getPurchaseId() == null ? Optional.empty() : purchaseRepository.findById(order.getPurchaseId());
    }

    /** A failed booking leaves the purchase waiting for a slot, so the customer can try again. */
    private void releasePurchase(Order order) {
        purchaseOf(order).ifPresent(purchase -> purchase.detachOrder(order.getId()));
    }

    private SagaInstance loadSaga(String orderId) {
        return sagaRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("No saga instance for orderId=" + orderId));
    }
}
