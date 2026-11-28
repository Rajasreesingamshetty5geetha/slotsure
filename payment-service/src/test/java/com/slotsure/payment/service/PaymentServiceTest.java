package com.slotsure.payment.service;

import com.slotsure.payment.config.TopicProperties;
import com.slotsure.payment.dto.LedgerCheckResponse;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.entity.PaymentStatus;
import com.slotsure.payment.event.EventPublisher;
import com.slotsure.payment.event.PaymentCapturedEvent;
import com.slotsure.payment.event.PaymentFailedEvent;
import com.slotsure.payment.event.PaymentRequestedEvent;
import com.slotsure.payment.event.RefundCompletedEvent;
import com.slotsure.payment.event.RefundRequestedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PaymentServiceTest {

    private static final TopicProperties TOPICS = new TopicProperties(
            "payment.requested", "payment.captured", "payment.failed", "refund.requested", "refund.completed",
            "escrow.release", "cod.collect");
    private static final BigDecimal AMOUNT = new BigDecimal("250.00");

    private final InMemoryStore store = new InMemoryStore();
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final LedgerService ledgerService = new LedgerService(store.ledgerEntryRepository);

    private PaymentService service(PaymentGateway... gateways) {
        List<PaymentGateway> list = List.of(gateways);
        GatewayRouter router = new GatewayRouter(list, list.stream().map(PaymentGateway::name).toList());
        return new PaymentService(router, ledgerService, store.paymentAttemptRepository,
                store.ledgerEntryRepository, store.processedEventRepository, publisher, TOPICS);
    }

    private static PaymentRequestedEvent request(String orderId, String customerId) {
        return new PaymentRequestedEvent(orderId, customerId, AMOUNT);
    }

    @Test
    void gatewayAAlwaysFailing_paymentSucceedsThroughB() {
        PaymentService service = service(new MockGatewayA(1.0, 0), new MockGatewayB(0.0, 0));
        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        service.handlePaymentRequested(UUID.randomUUID().toString(), request(orderId, customerId));

        assertThat(store.attempts).hasSize(2);
        assertThat(store.attempts.get(0).getGateway()).isEqualTo("A");
        assertThat(store.attempts.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(store.attempts.get(1).getGateway()).isEqualTo("B");
        assertThat(store.attempts.get(1).getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq("payment.captured"), eq("payment.captured"), eq(orderId), payload.capture());
        PaymentCapturedEvent captured = (PaymentCapturedEvent) payload.getValue();
        assertThat(captured.gateway()).isEqualTo("B");
        assertThat(captured.paymentId()).isEqualTo(store.attempts.get(1).getId());
        assertThat(captured.amount()).isEqualByComparingTo(AMOUNT);
        verify(publisher, never()).publish(eq("payment.failed"), anyString(), anyString(), any());

        assertThat(store.ledger).hasSize(2);
        LedgerEntry debit = store.ledger.stream().filter(e -> e.getDebit().signum() > 0).findFirst().orElseThrow();
        LedgerEntry credit = store.ledger.stream().filter(e -> e.getCredit().signum() > 0).findFirst().orElseThrow();
        assertThat(debit.getAccount()).isEqualTo("CUSTOMER:" + customerId);
        assertThat(credit.getAccount()).isEqualTo("ESCROW");
    }

    @Test
    void bothGatewaysAlwaysFailing_publishesPaymentFailedAndWritesNoLedgerRows() {
        PaymentService service = service(new MockGatewayA(1.0, 0), new MockGatewayB(1.0, 0));
        String orderId = UUID.randomUUID().toString();

        service.handlePaymentRequested(UUID.randomUUID().toString(), request(orderId, UUID.randomUUID().toString()));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq("payment.failed"), eq("payment.failed"), eq(orderId), payload.capture());
        PaymentFailedEvent failed = (PaymentFailedEvent) payload.getValue();
        assertThat(failed.reason()).contains("A:").contains("B:");
        verify(publisher, never()).publish(eq("payment.captured"), anyString(), anyString(), any());

        assertThat(store.attempts).hasSize(2).allMatch(a -> a.getStatus() == PaymentStatus.FAILED);
        assertThat(store.ledger).isEmpty();
    }

    @Test
    void sameEventIdTwice_chargesOnceAndPostsOneLedgerTransaction() {
        AtomicInteger charges = new AtomicInteger();
        PaymentGateway counting = new PaymentGateway() {
            @Override
            public String name() {
                return "A";
            }

            @Override
            public GatewayResult charge(String orderId, BigDecimal amount) {
                charges.incrementAndGet();
                return GatewayResult.ok();
            }
        };
        PaymentService service = service(counting);
        String eventId = UUID.randomUUID().toString();
        PaymentRequestedEvent request = request(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        service.handlePaymentRequested(eventId, request);
        service.handlePaymentRequested(eventId, request);

        assertThat(charges.get()).isEqualTo(1);
        assertThat(store.attempts).hasSize(1);
        assertThat(store.ledgerTransactions()).isEqualTo(1);
        verify(publisher, times(1)).publish(eq("payment.captured"), anyString(), anyString(), any());
    }

    @Test
    void newEventForAlreadyPaidOrder_doesNotChargeAgainButRepublishesCaptured() {
        AtomicInteger charges = new AtomicInteger();
        PaymentGateway counting = new PaymentGateway() {
            @Override
            public String name() {
                return "A";
            }

            @Override
            public GatewayResult charge(String orderId, BigDecimal amount) {
                charges.incrementAndGet();
                return GatewayResult.ok();
            }
        };
        PaymentService service = service(counting);
        PaymentRequestedEvent request = request(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        service.handlePaymentRequested(UUID.randomUUID().toString(), request);
        service.handlePaymentRequested(UUID.randomUUID().toString(), request);

        assertThat(charges.get()).isEqualTo(1);
        assertThat(store.ledgerTransactions()).isEqualTo(1);
        verify(publisher, times(2)).publish(eq("payment.captured"), anyString(), anyString(), any());
    }

    @Test
    void chargeThenRefund_totalDebitEqualsTotalCredit() {
        PaymentService service = service(new MockGatewayA(0.0, 0));
        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        service.handlePaymentRequested(UUID.randomUUID().toString(), request(orderId, customerId));
        service.handleRefundRequested(UUID.randomUUID().toString(),
                new RefundRequestedEvent(orderId, AMOUNT, "SLOT_CONFIRM_FAILED"));

        LedgerCheckResponse check = ledgerService.check();
        assertThat(check.totalDebit()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(check.totalCredit()).isEqualByComparingTo(check.totalDebit());
        assertThat(check.difference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(store.ledgerTransactions()).isEqualTo(2);
        assertThat(store.ledger).anyMatch(e -> e.getAccount().equals("WALLET:" + customerId)
                && e.getCredit().compareTo(AMOUNT) == 0);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq("refund.completed"), eq("refund.completed"), eq(orderId), payload.capture());
        assertThat(((RefundCompletedEvent) payload.getValue()).amount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void refundWithoutSuccessfulPayment_publishesZeroAmountAndWritesNoLedgerRows() {
        PaymentService service = service(new MockGatewayA(0.0, 0));
        String orderId = UUID.randomUUID().toString();

        service.handleRefundRequested(UUID.randomUUID().toString(), new RefundRequestedEvent(orderId, AMOUNT, "X"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq("refund.completed"), eq("refund.completed"), eq(orderId), payload.capture());
        RefundCompletedEvent completed = (RefundCompletedEvent) payload.getValue();
        assertThat(completed.amount()).isEqualTo(new BigDecimal(BigInteger.ZERO, 2));
        assertThat(store.ledger).isEmpty();
    }
}
