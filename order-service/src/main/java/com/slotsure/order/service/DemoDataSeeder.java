package com.slotsure.order.service;

import com.slotsure.order.config.OrderProperties;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.entity.SagaStep;
import com.slotsure.order.repository.OrderRepository;
import com.slotsure.order.repository.PurchaseRepository;
import com.slotsure.order.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds dummy bookings for yesterday and today so the admin dashboard is never empty (CONTRACT V2.10),
 * and makes sure the demo customers have purchases to book. A date is seeded once: when it already
 * has demo orders it is left alone. About 35% of purchases are COD (70% of those paid in cash).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {

    private final OrderRepository orderRepository;
    private final PurchaseRepository purchaseRepository;
    private final SagaInstanceRepository sagaRepository;
    private final PurchaseService purchaseService;
    private final TransactionTemplate transactionTemplate;
    private final OrderProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!properties.demoData().enabled()) {
            return;
        }
        try {
            purchaseService.topUpDemoCustomers();
        } catch (RuntimeException e) {
            log.error("Could not top up demo customers", e);
        }
        LocalDate today = LocalDate.now(properties.zone());
        for (LocalDate date : List.of(today.minusDays(1), today)) {
            try {
                seed(date, Instant.now());
            } catch (RuntimeException e) {
                log.error("Could not seed demo orders for {}", date, e);
            }
        }
    }

    /** Returns how many orders were seeded; 0 when the date already has demo orders. */
    public int seed(LocalDate date, Instant now) {
        if (orderRepository.existsBySlotDateAndDemoTrue(date)) {
            log.info("Demo orders for {} already exist, not seeding", date);
            return 0;
        }
        OrderProperties.DemoData demo = properties.demoData();
        List<Purchase> purchases = new ArrayList<>();
        List<Order> orders = new ArrayList<>();
        List<SagaInstance> sagas = new ArrayList<>();
        for (DemoPlan.Booking booking : DemoPlan.forDate(date, demo.minBookingsPerDay(), demo.maxBookingsPerDay())) {
            build(date, booking, now, purchases, orders, sagas);
        }
        transactionTemplate.executeWithoutResult(status -> {
            purchaseRepository.saveAll(purchases);
            orderRepository.saveAll(orders);
            sagaRepository.saveAll(sagas);
        });
        log.info("Seeded {} demo orders for {}", orders.size(), date);
        return orders.size();
    }

    private void build(LocalDate date, DemoPlan.Booking booking, Instant now,
                       List<Purchase> purchases, List<Order> orders, List<SagaInstance> sagas) {
        Random random = booking.random();
        PurchaseService.CatalogItem item = PurchaseService.CATALOG.get(booking.itemIndex());
        PaymentChoice choice = PaymentChoice.valueOf(booking.choice());
        boolean cod = choice != PaymentChoice.PREPAID;
        Instant slotStart = date.atTime(booking.startHour(), 0).atZone(properties.zone()).toInstant();
        Instant slotEnd = slotStart.plus(Duration.ofHours(1));
        Instant bookedAt = slotStart.minus(Duration.ofMinutes(booking.bookedMinutesBefore()));
        String key = date + ":" + booking.index();
        String customerId = DemoPlan.customerId(date, booking.index());

        Purchase purchase = new Purchase(DemoPlan.uuid("slotsure-demo-purchase:" + key), customerId, item.name(),
                item.category(), item.amount(), cod ? PaymentMode.COD : PaymentMode.PREPAID,
                bookedAt.minus(Duration.ofHours(2L + random.nextInt(46))));
        purchase.setDemo(true);

        Order order = new Order(booking.orderId(), customerId, DemoPlan.uuid("slotsure-demo-hold:" + key),
                item.amount(), "demo-" + booking.orderId());
        order.setPurchaseId(purchase.getId());
        order.setItemName(item.name());
        order.setPaymentMode(purchase.getPaymentMode());
        order.setPaymentChoice(choice);
        order.setAddress((12 + random.nextInt(480)) + ", " + (1 + random.nextInt(20)) + "th Cross, "
                + booking.areaName() + ", Bengaluru");
        order.setAreaName(booking.areaName());
        order.setSlotDate(date);
        order.setStartHour(booking.startHour());
        order.setEndHour(booking.startHour() + 1);
        order.setDemo(true);

        SagaInstance saga = new SagaInstance(order.getId(),
                choice.paysAtBooking() ? SagaStep.PAYMENT_REQUESTED : SagaStep.SLOT_CONFIRMING);
        Instant lastChange;
        if (booking.failed()) {
            order.moveTo(OrderStatus.FAILED);
            saga.moveTo(SagaStep.COMPENSATING);
            saga.recordError(choice.paysAtBooking() ? "CARD_DECLINED" : "HOLD_EXPIRED");
            saga.moveTo(SagaStep.DONE);
            lastChange = bookedAt.plusSeconds(20);
        } else {
            if (choice.paysAtBooking()) {
                saga.moveTo(SagaStep.SLOT_CONFIRMING);
            }
            saga.moveTo(SagaStep.DONE);
            order.moveTo(OrderStatus.CONFIRMED);
            purchase.attachOrder(order.getId());
            purchase.markScheduled();
            DemoPlan.Agent agent = booking.agent();
            order.assignAgent(agent.agentId(), agent.name(), agent.phone());
            order.moveTo(OrderStatus.OUT_FOR_DELIVERY);
            lastChange = bookedAt.plusSeconds(5);
            if (!now.isBefore(slotEnd)) {
                Instant deliveredAt = slotStart.plus(Duration.ofMinutes(booking.deliveredMinute()));
                order.markDelivered(deliveredAt);
                purchase.markDelivered();
                lastChange = deliveredAt;
            }
        }
        order.setCreatedAt(bookedAt);
        order.setUpdatedAt(lastChange);

        purchases.add(purchase);
        orders.add(order);
        sagas.add(saga);
    }
}
