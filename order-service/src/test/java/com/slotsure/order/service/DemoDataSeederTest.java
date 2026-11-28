package com.slotsure.order.service;

import com.slotsure.order.InMemoryStore;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.entity.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoDataSeederTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private final InMemoryStore store = new InMemoryStore();
    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        PurchaseService purchaseService = new PurchaseService(store.purchaseRepository, transactionTemplate,
                InMemoryStore.PROPERTIES);
        seeder = new DemoDataSeeder(store.orderRepository, store.purchaseRepository, store.sagaRepository,
                purchaseService, transactionTemplate, InMemoryStore.PROPERTIES);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPastDayIsDeliveredOrFailedWithARealisticPaymentMix() {
        ArgumentCaptor<List<Order>> orders = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<SagaInstance>> sagas = ArgumentCaptor.forClass(List.class);
        when(store.orderRepository.saveAll(orders.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(store.sagaRepository.saveAll(sagas.capture())).thenAnswer(inv -> inv.getArgument(0));

        int seeded = seeder.seed(DATE, Instant.parse("2026-11-29T03:00:00Z"));

        List<Order> saved = orders.getValue();
        assertThat(saved).hasSize(seeded);
        assertThat(seeded).isBetween(40, 80);
        assertThat(saved).allSatisfy(order -> {
            assertThat(order.isDemo()).isTrue();
            assertThat(order.getSlotDate()).isEqualTo(DATE);
            assertThat(order.getStatus()).isIn(OrderStatus.DELIVERED, OrderStatus.FAILED);
            assertThat(order.getCreatedAt()).isBefore(Instant.parse("2026-11-28T16:30:00Z"));
        });
        assertThat(saved).filteredOn(o -> o.getStatus() == OrderStatus.DELIVERED)
                .allSatisfy(order -> {
                    assertThat(order.getAgentName()).isNotNull();
                    assertThat(order.getDeliveredAt()).isNotNull();
                });
        long cod = saved.stream().filter(o -> o.getPaymentChoice() != PaymentChoice.PREPAID).count();
        assertThat(cod * 100 / seeded).as("COD share").isBetween(15L, 55L);
        assertThat(sagas.getValue()).allSatisfy(saga -> assertThat(saga.getStep()).isEqualTo(SagaStep.DONE));
        assertThat(saved).extracting(Order::getId).containsExactlyElementsOf(
                DemoPlan.forDate(DATE, 40, 80).stream().map(DemoPlan.Booking::orderId).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void laterSlotsTodayAreOutForDelivery() {
        ArgumentCaptor<List<Order>> orders = ArgumentCaptor.forClass(List.class);
        when(store.orderRepository.saveAll(orders.capture())).thenAnswer(inv -> inv.getArgument(0));

        // 14:00 in Bengaluru: morning slots are done, evening ones are on their way.
        seeder.seed(DATE, Instant.parse("2026-11-28T08:30:00Z"));

        assertThat(orders.getValue()).filteredOn(o -> o.getStartHour() >= 15 && o.getStatus() != OrderStatus.FAILED)
                .isNotEmpty()
                .allSatisfy(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY));
        assertThat(orders.getValue()).filteredOn(o -> o.getStartHour() < 13 && o.getStatus() != OrderStatus.FAILED)
                .allSatisfy(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED));
    }

    @Test
    void aDateWithDemoOrdersIsLeftAlone() {
        when(store.orderRepository.existsBySlotDateAndDemoTrue(DATE)).thenReturn(true);

        assertThat(seeder.seed(DATE, Instant.now())).isZero();
        verify(store.orderRepository, never()).saveAll(anyList());
    }
}
