package com.slotsure.order;

import com.slotsure.order.config.OrderProperties;
import com.slotsure.order.config.TopicProperties;
import com.slotsure.order.entity.Notification;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.ProcessedEvent;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.PurchaseStatus;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.repository.NotificationRepository;
import com.slotsure.order.repository.OrderRepository;
import com.slotsure.order.repository.ProcessedEventRepository;
import com.slotsure.order.repository.PurchaseRepository;
import com.slotsure.order.repository.SagaInstanceRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mockito repositories backed by maps, so tests can run the real services. */
public class InMemoryStore {

    public static final TopicProperties TOPICS = new TopicProperties(
            "payment.requested", "payment.captured", "payment.failed",
            "slot.confirm", "slot.release", "slot.confirmed", "slot.confirm.failed",
            "refund.requested", "refund.completed",
            "delivery.assigned", "delivery.completed", "escrow.release", "cod.collect");

    public static final OrderProperties PROPERTIES = new OrderProperties(ZoneId.of("Asia/Kolkata"),
            new OrderProperties.DemoData(true, 40, 80, List.of("6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e01")));

    public final Map<String, Order> orders = new LinkedHashMap<>();
    public final Map<String, Purchase> purchases = new LinkedHashMap<>();
    public final Map<String, SagaInstance> sagas = new LinkedHashMap<>();
    public final Map<String, ProcessedEvent> processedEvents = new LinkedHashMap<>();
    public final Map<String, Notification> notifications = new LinkedHashMap<>();

    public final OrderRepository orderRepository = mock(OrderRepository.class);
    public final PurchaseRepository purchaseRepository = mock(PurchaseRepository.class);
    public final SagaInstanceRepository sagaRepository = mock(SagaInstanceRepository.class);
    public final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    public final NotificationRepository notificationRepository = mock(NotificationRepository.class);

    @SuppressWarnings("unchecked")
    public InMemoryStore() {
        when(orderRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(orders.get(inv.<String>getArgument(0))));
        when(orderRepository.findByIdempotencyKey(anyString())).thenAnswer(inv -> orders.values().stream()
                .filter(order -> order.getIdempotencyKey().equals(inv.getArgument(0))).findFirst());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            orders.put(order.getId(), order);
            return order;
        });
        when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv -> orders.values().stream()
                .filter(order -> order.getCustomerId().equals(inv.getArgument(0)))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed()).toList());
        when(orderRepository.findFirstByPurchaseIdAndStatusIn(anyString(), anyCollection())).thenAnswer(inv -> {
            Collection<OrderStatus> statuses = inv.getArgument(1);
            return orders.values().stream()
                    .filter(order -> inv.getArgument(0).equals(order.getPurchaseId()) && statuses.contains(order.getStatus()))
                    .findFirst();
        });

        when(purchaseRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(purchases.get(inv.<String>getArgument(0))));
        when(purchaseRepository.findByIdForUpdate(anyString())).thenAnswer(inv -> Optional.ofNullable(purchases.get(inv.<String>getArgument(0))));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> putPurchase(inv.getArgument(0)));
        when(purchaseRepository.saveAll(anyIterable())).thenAnswer(inv -> putPurchases(inv.getArgument(0)));
        when(purchaseRepository.saveAllAndFlush(anyIterable())).thenAnswer(inv -> putPurchases(inv.getArgument(0)));
        when(purchaseRepository.findByCustomerIdOrderByPlacedAtDesc(anyString())).thenAnswer(inv -> purchases.values().stream()
                .filter(purchase -> purchase.getCustomerId().equals(inv.getArgument(0)))
                .sorted(Comparator.comparing(Purchase::getPlacedAt).reversed()).toList());
        when(purchaseRepository.countByCustomerIdAndPaymentModeAndStatus(anyString(), any(PaymentMode.class), any(PurchaseStatus.class)))
                .thenAnswer(inv -> purchases.values().stream()
                        .filter(p -> p.getCustomerId().equals(inv.getArgument(0))
                                && p.getPaymentMode() == inv.getArgument(1) && p.getStatus() == inv.getArgument(2))
                        .count());

        when(sagaRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(sagas.get(inv.<String>getArgument(0))));
        when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(inv -> {
            SagaInstance saga = inv.getArgument(0);
            sagas.put(saga.getOrderId(), saga);
            return saga;
        });
        when(sagaRepository.findAllById(anyList())).thenAnswer(inv -> ((List<String>) inv.getArgument(0)).stream()
                .map(sagas::get).filter(saga -> saga != null).toList());

        when(processedEventRepository.existsById(anyString())).thenAnswer(inv -> processedEvents.containsKey(inv.<String>getArgument(0)));
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(inv -> {
            ProcessedEvent event = inv.getArgument(0);
            processedEvents.put(event.getEventId(), event);
            return event;
        });

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification notification = inv.getArgument(0);
            notifications.put(notification.getId(), notification);
            return notification;
        });
        when(notificationRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(notifications.get(inv.<String>getArgument(0))));
    }

    public Purchase addPurchase(String id, String customerId, String itemName, String amount, PaymentMode mode) {
        Purchase purchase = new Purchase(id, customerId, itemName, "Electronics", new BigDecimal(amount), mode,
                Instant.parse("2026-11-28T06:00:00Z"));
        purchases.put(id, purchase);
        return purchase;
    }

    public List<Notification> notificationsFor(String orderId) {
        return notifications.values().stream().filter(n -> n.getOrderId().equals(orderId)).toList();
    }

    private Purchase putPurchase(Purchase purchase) {
        purchases.put(purchase.getId(), purchase);
        return purchase;
    }

    private List<Purchase> putPurchases(Iterable<Purchase> items) {
        List<Purchase> saved = new ArrayList<>();
        items.forEach(purchase -> saved.add(putPurchase(purchase)));
        return saved;
    }
}
