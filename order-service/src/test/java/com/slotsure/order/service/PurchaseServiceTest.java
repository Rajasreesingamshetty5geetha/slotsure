package com.slotsure.order.service;

import com.slotsure.order.InMemoryStore;
import com.slotsure.order.dto.PurchaseResponse;
import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.PurchaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PurchaseServiceTest {

    private static final String DEMO_CUSTOMER = "6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e01";

    private final InMemoryStore store = new InMemoryStore();
    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        purchaseService = new PurchaseService(store.purchaseRepository,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), InMemoryStore.PROPERTIES);
    }

    @Test
    void aNewCustomerGetsThreeStarterPurchasesOnFirstLook() {
        List<PurchaseResponse> purchases = purchaseService.listForCustomer("customer-new");

        assertThat(purchases).hasSize(3);
        assertThat(purchases).extracting(PurchaseResponse::paymentMode)
                .containsExactlyInAnyOrder(PaymentMode.PREPAID, PaymentMode.COD, PaymentMode.COD);
        assertThat(purchases).allSatisfy(purchase -> {
            assertThat(purchase.customerId()).isEqualTo("customer-new");
            assertThat(purchase.status()).isEqualTo(PurchaseStatus.AWAITING_SLOT);
            assertThat(purchase.orderId()).isNull();
            assertThat(purchase.amount().scale()).isEqualTo(2);
            assertThat(purchase.amount()).isPositive();
        });
        assertThat(purchases).extracting(PurchaseResponse::itemName).doesNotHaveDuplicates();
        assertThat(purchases).extracting(PurchaseResponse::placedAt).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void starterPurchasesAreCreatedOnceWithStableIds() {
        List<String> first = purchaseService.listForCustomer("customer-new").stream().map(PurchaseResponse::purchaseId).toList();
        List<String> second = purchaseService.listForCustomer("customer-new").stream().map(PurchaseResponse::purchaseId).toList();

        assertThat(second).containsExactlyElementsOf(first);
        assertThat(store.purchases).hasSize(3);
    }

    @Test
    void customersDoNotSeeEachOthersPurchases() {
        purchaseService.listForCustomer("customer-a");

        assertThat(purchaseService.listForCustomer("customer-b"))
                .allSatisfy(p -> assertThat(p.customerId()).isEqualTo("customer-b"));
        assertThat(store.purchases).hasSize(6);
    }

    @Test
    void anExistingCustomerIsNotGivenMorePurchases() {
        store.addPurchase("p-1", "customer-old", "Yoga Mat", "899", PaymentMode.COD);

        assertThat(purchaseService.listForCustomer("customer-old")).singleElement()
                .satisfies(p -> assertThat(p.purchaseId()).isEqualTo("p-1"));
    }

    @Test
    void demoCustomersAreToppedUpToOnePrepaidAndTwoCodAwaitingASlot() {
        Purchase used = store.addPurchase("p-used", DEMO_CUSTOMER, "Smart Watch", "4999", PaymentMode.PREPAID);
        used.markScheduled();
        store.addPurchase("p-cod", DEMO_CUSTOMER, "Yoga Mat", "899", PaymentMode.COD);

        purchaseService.topUpDemoCustomers();

        List<Purchase> waiting = store.purchases.values().stream()
                .filter(p -> p.getStatus() == PurchaseStatus.AWAITING_SLOT).toList();
        assertThat(waiting).filteredOn(p -> p.getPaymentMode() == PaymentMode.PREPAID).hasSize(1);
        assertThat(waiting).filteredOn(p -> p.getPaymentMode() == PaymentMode.COD).hasSize(2);

        purchaseService.topUpDemoCustomers();
        assertThat(store.purchases).hasSize(4);
    }

    @Test
    void customerIdIsRequired() {
        assertThatThrownBy(() -> purchaseService.listForCustomer(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
