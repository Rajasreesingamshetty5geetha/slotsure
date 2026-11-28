package com.slotsure.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.order.InMemoryStore;
import com.slotsure.order.entity.Notification;
import com.slotsure.order.entity.NotificationType;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.PurchaseStatus;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.entity.SagaStep;
import com.slotsure.order.service.EventPublisher;
import com.slotsure.order.service.NotificationService;
import com.slotsure.order.service.SagaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Drives the listener with real JSON envelopes. SagaService and NotificationService are real;
 * repositories are in-memory and the Kafka publisher is a Mockito mock.
 */
class SagaEventListenerTest {

    private static final String CUSTOMER = "6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e01";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 29);

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final InMemoryStore store = new InMemoryStore();
    private final EventPublisher eventPublisher = mock(EventPublisher.class);

    private SagaEventListener listener;

    @BeforeEach
    void setUp() {
        NotificationService notificationService = new NotificationService(store.notificationRepository, InMemoryStore.PROPERTIES);
        SagaService sagaService = new SagaService(store.orderRepository, store.sagaRepository,
                store.processedEventRepository, store.purchaseRepository, notificationService, eventPublisher,
                InMemoryStore.TOPICS);
        listener = new SagaEventListener(sagaService, objectMapper);
    }

    @Test
    void paymentCapturedMovesSagaToSlotConfirmingAndPublishesSlotConfirm() throws Exception {
        Order order = order(PaymentChoice.ONLINE);

        listener.onPaymentCaptured(message("payment.captured",
                new PaymentCapturedPayload(order.getId(), "pay-1", "GATEWAY_A", order.getAmount())));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saga(order).getStep()).isEqualTo(SagaStep.SLOT_CONFIRMING);
        verify(eventPublisher).publishAfterCommit("slot.confirm", order.getId(),
                new SlotConfirmPayload(order.getId(), order.getHoldId()));
    }

    @Test
    void paymentFailedFailsOrderReleasesSlotFreesPurchaseAndNotifies() throws Exception {
        Order order = order(PaymentChoice.ONLINE);

        listener.onPaymentFailed(message("payment.failed", new PaymentFailedPayload(order.getId(), "CARD_DECLINED")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(saga(order).getStep()).isEqualTo(SagaStep.DONE);
        assertThat(saga(order).getLastError()).isEqualTo("CARD_DECLINED");
        assertThat(purchase(order).getStatus()).isEqualTo(PurchaseStatus.AWAITING_SLOT);
        assertThat(purchase(order).getOrderId()).isNull();
        assertThat(onlyNotification(order).getType()).isEqualTo(NotificationType.PAYMENT_FAILED);
        assertThat(onlyNotification(order).getTitle()).isEqualTo("Payment failed — your slot was released");
        verify(eventPublisher).publishAfterCommit("slot.release", order.getId(),
                new SlotReleasePayload(order.getId(), order.getHoldId(), "PAYMENT_FAILED"));
    }

    @Test
    void slotConfirmedConfirmsSchedulesThePurchaseAndNotifies() throws Exception {
        Order order = order(PaymentChoice.CASH);

        listener.onSlotConfirmed(message("slot.confirmed", new SlotConfirmedPayload(order.getId(), order.getHoldId(),
                "slot-1", "89618925c4bffff", "Koramangala", DATE, 18, 19)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getAreaName()).isEqualTo("Koramangala");
        assertThat(saga(order).getStep()).isEqualTo(SagaStep.DONE);
        assertThat(purchase(order).getStatus()).isEqualTo(PurchaseStatus.SCHEDULED);
        Notification notification = onlyNotification(order);
        assertThat(notification.getType()).isEqualTo(NotificationType.SLOT_CONFIRMED);
        assertThat(notification.getTitle()).isEqualTo("Slot confirmed — Wireless Headphones arrives Sun 29 Nov, 6–7 PM");
        assertThat(notification.getBody()).startsWith("Pay ₹2,499 to the agent on delivery");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void slotConfirmedIgnoresUnknownFieldsFromNewerProducers() throws Exception {
        Order order = order(PaymentChoice.PREPAID);
        String json = message("slot.confirmed", new SlotConfirmedPayload(order.getId(), order.getHoldId(),
                "slot-1", "89618925c4bffff", "Koramangala", DATE, 18, 19))
                .replace("\"slotId\"", "\"futureField\":true,\"slotId\"");

        listener.onSlotConfirmed(json);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void slotConfirmFailedForOnlineRequestsARefund() throws Exception {
        Order order = order(PaymentChoice.ONLINE);
        saga(order).moveTo(SagaStep.SLOT_CONFIRMING);

        listener.onSlotConfirmFailed(message("slot.confirm.failed",
                new SlotConfirmFailedPayload(order.getId(), order.getHoldId(), "HOLD_EXPIRED")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saga(order).getStep()).isEqualTo(SagaStep.COMPENSATING);
        verify(eventPublisher).publishAfterCommit("refund.requested", order.getId(),
                new RefundRequestedPayload(order.getId(), new BigDecimal("2499.00"), "HOLD_EXPIRED"));
    }

    @Test
    void slotConfirmFailedForCashFailsWithoutARefund() throws Exception {
        Order order = order(PaymentChoice.CASH);

        listener.onSlotConfirmFailed(message("slot.confirm.failed",
                new SlotConfirmFailedPayload(order.getId(), order.getHoldId(), "HOLD_EXPIRED")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(saga(order).getStep()).isEqualTo(SagaStep.DONE);
        assertThat(purchase(order).getOrderId()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void refundCompletedCancelsOrderAndFinishesSaga() throws Exception {
        Order order = order(PaymentChoice.ONLINE);
        saga(order).moveTo(SagaStep.SLOT_CONFIRMING);
        saga(order).moveTo(SagaStep.COMPENSATING);

        listener.onRefundCompleted(message("refund.completed", new RefundCompletedPayload(order.getId(), order.getAmount())));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saga(order).getStep()).isEqualTo(SagaStep.DONE);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deliveryAssignedPutsTheOrderOutForDeliveryAndNotifies() throws Exception {
        Order order = confirmed(PaymentChoice.PREPAID);

        listener.onDeliveryAssigned(message("delivery.assigned", assigned(order)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(order.getAgentName()).isEqualTo("Ravi Kumar");
        assertThat(order.getAgentPhone()).isEqualTo("+91 98450 11201");
        assertThat(store.notificationsFor(order.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.SLOT_CONFIRMED, NotificationType.AGENT_ASSIGNED);
    }

    @Test
    void anAssignmentThatOvertakesSlotConfirmedIsAppliedOnConfirmation() throws Exception {
        Order order = order(PaymentChoice.PREPAID);

        listener.onDeliveryAssigned(message("delivery.assigned", assigned(order)));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        listener.onSlotConfirmed(message("slot.confirmed", new SlotConfirmedPayload(order.getId(), order.getHoldId(),
                "slot-1", "89618925c4bffff", "Koramangala", DATE, 18, 19)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(store.notificationsFor(order.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.SLOT_CONFIRMED, NotificationType.AGENT_ASSIGNED);
    }

    @Test
    void deliveryOfACashOrderAsksPaymentServiceToRecordTheCash() throws Exception {
        Order order = outForDelivery(PaymentChoice.CASH);

        listener.onDeliveryCompleted(message("delivery.completed", completed(order)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isEqualTo(Instant.parse("2026-11-29T12:52:00Z"));
        assertThat(purchase(order).getStatus()).isEqualTo(PurchaseStatus.DELIVERED);
        assertThat(store.notificationsFor(order.getId())).extracting(Notification::getType).contains(NotificationType.DELIVERED);
        verify(eventPublisher).publishAfterCommit("cod.collect", order.getId(),
                new CodCollectPayload(order.getId(), new BigDecimal("2499.00"), CUSTOMER));
        verify(eventPublisher, never()).publishAfterCommit(eq("escrow.release"), anyString(), any());
    }

    @Test
    void deliveryOfAPrepaidOrderReleasesEscrow() throws Exception {
        Order order = outForDelivery(PaymentChoice.PREPAID);

        listener.onDeliveryCompleted(message("delivery.completed", completed(order)));

        verify(eventPublisher).publishAfterCommit("escrow.release", order.getId(),
                new EscrowReleasePayload(order.getId(), new BigDecimal("2499.00"), CUSTOMER));
        verify(eventPublisher, never()).publishAfterCommit(eq("cod.collect"), anyString(), any());
    }

    @Test
    void deliveryOfAnOnlinePaidOrderReleasesEscrow() throws Exception {
        Order order = outForDelivery(PaymentChoice.ONLINE);

        listener.onDeliveryCompleted(message("delivery.completed", completed(order)));

        verify(eventPublisher).publishAfterCommit("escrow.release", order.getId(),
                new EscrowReleasePayload(order.getId(), new BigDecimal("2499.00"), CUSTOMER));
    }

    @Test
    void aDuplicateDeliveryIsProcessedOnce() throws Exception {
        Order order = outForDelivery(PaymentChoice.CASH);
        String message = message("delivery.completed", completed(order));

        listener.onDeliveryCompleted(message);
        listener.onDeliveryCompleted(message);
        listener.onDeliveryCompleted(message("delivery.completed", completed(order)));

        verify(eventPublisher, times(1)).publishAfterCommit(eq("cod.collect"), anyString(), any());
    }

    @Test
    void aDeliveryBeforeConfirmationIsRetriedLater() throws Exception {
        Order order = order(PaymentChoice.CASH);

        assertThatThrownBy(() -> listener.onDeliveryCompleted(message("delivery.completed", completed(order))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void sameEventIdDeliveredTwiceIsProcessedOnce() throws Exception {
        Order order = order(PaymentChoice.ONLINE);
        String message = message("payment.captured",
                new PaymentCapturedPayload(order.getId(), "pay-1", "GATEWAY_A", order.getAmount()));

        listener.onPaymentCaptured(message);
        listener.onPaymentCaptured(message);

        assertThat(saga(order).getStep()).isEqualTo(SagaStep.SLOT_CONFIRMING);
        assertThat(store.processedEvents).hasSize(1);
        verify(eventPublisher, times(1)).publishAfterCommit(anyString(), anyString(), any());
    }

    @Test
    void eventForUnknownOrderIsIgnored() throws Exception {
        listener.onPaymentCaptured(message("payment.captured",
                new PaymentCapturedPayload(UUID.randomUUID().toString(), "pay-1", "GATEWAY_A", new BigDecimal("1.00"))));
        listener.onDeliveryAssigned(message("delivery.assigned", new DeliveryAssignedPayload(
                UUID.randomUUID().toString(), "agent-1", "Ravi Kumar", "+91 98450 11201", DATE, 18)));

        verifyNoInteractions(eventPublisher);
        assertThat(store.notifications).isEmpty();
    }

    @Test
    void paymentEventForAConfirmedOrderIsIgnored() throws Exception {
        Order order = confirmed(PaymentChoice.ONLINE);

        listener.onPaymentFailed(message("payment.failed", new PaymentFailedPayload(order.getId(), "LATE_FAILURE")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(eventPublisher);
    }

    /** A PENDING order for a fresh purchase, as OrderService would create it. */
    private Order order(PaymentChoice choice) {
        PaymentMode mode = choice == PaymentChoice.PREPAID ? PaymentMode.PREPAID : PaymentMode.COD;
        Purchase purchase = store.addPurchase(UUID.randomUUID().toString(), CUSTOMER, "Wireless Headphones", "2499", mode);
        Order order = new Order(CUSTOMER, UUID.randomUUID().toString(), purchase.getAmount(), UUID.randomUUID().toString());
        order.setPurchaseId(purchase.getId());
        order.setItemName(purchase.getItemName());
        order.setPaymentMode(mode);
        order.setPaymentChoice(choice);
        order.setSlotDate(DATE);
        order.setStartHour(18);
        order.setEndHour(19);
        purchase.attachOrder(order.getId());
        store.orders.put(order.getId(), order);
        store.sagas.put(order.getId(), new SagaInstance(order.getId(),
                choice.paysAtBooking() ? SagaStep.PAYMENT_REQUESTED : SagaStep.SLOT_CONFIRMING));
        return order;
    }

    private Order confirmed(PaymentChoice choice) throws Exception {
        Order order = order(choice);
        if (choice.paysAtBooking()) {
            saga(order).moveTo(SagaStep.SLOT_CONFIRMING);
        }
        listener.onSlotConfirmed(message("slot.confirmed", new SlotConfirmedPayload(order.getId(), order.getHoldId(),
                "slot-1", "89618925c4bffff", "Koramangala", DATE, 18, 19)));
        return order;
    }

    private Order outForDelivery(PaymentChoice choice) throws Exception {
        Order order = confirmed(choice);
        listener.onDeliveryAssigned(message("delivery.assigned", assigned(order)));
        return order;
    }

    private static DeliveryAssignedPayload assigned(Order order) {
        return new DeliveryAssignedPayload(order.getId(), "agent-1", "Ravi Kumar", "+91 98450 11201", DATE, 18);
    }

    private static DeliveryCompletedPayload completed(Order order) {
        return new DeliveryCompletedPayload(order.getId(), "agent-1", Instant.parse("2026-11-29T12:52:00Z"));
    }

    private SagaInstance saga(Order order) {
        return store.sagas.get(order.getId());
    }

    private Purchase purchase(Order order) {
        return store.purchases.get(order.getPurchaseId());
    }

    private Notification onlyNotification(Order order) {
        assertThat(store.notificationsFor(order.getId())).hasSize(1);
        return store.notificationsFor(order.getId()).get(0);
    }

    private String message(String eventType, Object payload) throws Exception {
        String orderId = objectMapper.valueToTree(payload).get("orderId").asText();
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(), eventType, Instant.now(), orderId, payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
