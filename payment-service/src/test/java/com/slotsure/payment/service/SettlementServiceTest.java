package com.slotsure.payment.service;

import com.slotsure.payment.dto.LedgerLine;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import com.slotsure.payment.event.CodCollectEvent;
import com.slotsure.payment.event.EscrowReleaseEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("2499.00");
    private static final String CUSTOMER = "6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e01";

    private final InMemoryStore store = new InMemoryStore();
    private final LedgerService ledgerService = new LedgerService(store.ledgerEntryRepository);
    private final SettlementService settlementService = new SettlementService(ledgerService,
            store.paymentAttemptRepository, store.ledgerEntryRepository, store.processedEventRepository);

    @Test
    void escrowReleaseOfAnOnlinePaymentMovesEscrowToTheMerchantAndBalances() {
        String orderId = capturedOnline();

        settlementService.handleEscrowRelease(eventId(), new EscrowReleaseEvent(orderId, AMOUNT, CUSTOMER));

        assertThat(store.balance(LedgerEntry.ESCROW)).isEqualByComparingTo("0");
        assertThat(store.balance(LedgerEntry.MERCHANT)).isEqualByComparingTo(AMOUNT);
        assertThat(ledgerService.check().difference()).isEqualByComparingTo("0");
        assertThat(store.attempts).hasSize(1);
    }

    @Test
    void escrowReleaseOfAPrepaidOrderFirstRecordsTheCheckoutCapture() {
        String orderId = UUID.randomUUID().toString();

        settlementService.handleEscrowRelease(eventId(), new EscrowReleaseEvent(orderId, AMOUNT, CUSTOMER));

        assertThat(store.attempts).singleElement().satisfies(attempt -> {
            assertThat(attempt.getGateway()).isEqualTo("CHECKOUT");
            assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(attempt.getCustomerId()).isEqualTo(CUSTOMER);
            assertThat(attempt.getAmount()).isEqualByComparingTo(AMOUNT);
        });
        assertThat(store.ledgerTransactions()).isEqualTo(2);
        assertThat(store.balance(LedgerEntry.customerAccount(CUSTOMER))).isEqualByComparingTo(AMOUNT.negate());
        assertThat(store.balance(LedgerEntry.ESCROW)).isEqualByComparingTo("0");
        assertThat(store.balance(LedgerEntry.MERCHANT)).isEqualByComparingTo(AMOUNT);
        assertThat(ledgerService.check().difference()).isEqualByComparingTo("0");
    }

    @Test
    void codCollectRecordsTheCashAndPaysTheMerchant() {
        String orderId = UUID.randomUUID().toString();

        settlementService.handleCodCollect(eventId(), new CodCollectEvent(orderId, AMOUNT, CUSTOMER));

        assertThat(store.attempts).singleElement().satisfies(attempt -> {
            assertThat(attempt.getGateway()).isEqualTo("COD");
            assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.COLLECTED);
        });
        assertThat(store.balance(LedgerEntry.CASH_COLLECTED)).isEqualByComparingTo(AMOUNT.negate());
        assertThat(store.balance(LedgerEntry.MERCHANT)).isEqualByComparingTo(AMOUNT);
        assertThat(store.balance(LedgerEntry.ESCROW)).isEqualByComparingTo("0");
        assertThat(ledgerService.check().difference()).isEqualByComparingTo("0");
    }

    @Test
    void theSameEventTwiceIsSkipped() {
        String orderId = UUID.randomUUID().toString();
        String eventId = eventId();

        settlementService.handleCodCollect(eventId, new CodCollectEvent(orderId, AMOUNT, CUSTOMER));
        settlementService.handleCodCollect(eventId, new CodCollectEvent(orderId, AMOUNT, CUSTOMER));

        assertThat(store.attempts).hasSize(1);
        assertThat(store.ledgerTransactions()).isEqualTo(1);
    }

    @Test
    void aResentReleaseWithANewEventIdDoesNotPayTheMerchantTwice() {
        String orderId = capturedOnline();

        settlementService.handleEscrowRelease(eventId(), new EscrowReleaseEvent(orderId, AMOUNT, CUSTOMER));
        settlementService.handleEscrowRelease(eventId(), new EscrowReleaseEvent(orderId, AMOUNT, CUSTOMER));
        settlementService.handleCodCollect(eventId(), new CodCollectEvent(orderId, AMOUNT, CUSTOMER));

        assertThat(store.balance(LedgerEntry.MERCHANT)).isEqualByComparingTo(AMOUNT);
        assertThat(store.attempts).hasSize(1);
    }

    @Test
    void releaseUsesTheCapturedAmountWhenTheEventDisagrees() {
        String orderId = capturedOnline();

        settlementService.handleEscrowRelease(eventId(), new EscrowReleaseEvent(orderId, new BigDecimal("1.00"), CUSTOMER));

        assertThat(store.balance(LedgerEntry.MERCHANT)).isEqualByComparingTo(AMOUNT);
        assertThat(store.balance(LedgerEntry.ESCROW)).isEqualByComparingTo("0");
    }

    @Test
    void aRefundedOrderIsNotReleased() {
        String orderId = capturedOnline();
        ledgerService.post("txn-refund", orderId, List.of(
                LedgerLine.debit(LedgerEntry.ESCROW, AMOUNT),
                LedgerLine.credit(LedgerEntry.walletAccount(CUSTOMER), AMOUNT)));

        settlementService.handleEscrowRelease(eventId(), new EscrowReleaseEvent(orderId, AMOUNT, CUSTOMER));

        assertThat(store.balance(LedgerEntry.MERCHANT)).isEqualByComparingTo("0");
    }

    @Test
    void aMissingCustomerIdIsRecordedAsUnknown() {
        settlementService.handleEscrowRelease(eventId(), new EscrowReleaseEvent(UUID.randomUUID().toString(), AMOUNT, null));

        assertThat(store.attempts.get(0).getCustomerId()).isEqualTo("UNKNOWN");
        assertThat(ledgerService.check().difference()).isEqualByComparingTo("0");
    }

    @Test
    void aZeroAmountIsRejected() {
        assertThatThrownBy(() -> settlementService.handleCodCollect(eventId(),
                new CodCollectEvent(UUID.randomUUID().toString(), BigDecimal.ZERO, CUSTOMER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** An ONLINE order whose gateway charge put the money in escrow. */
    private String capturedOnline() {
        String orderId = UUID.randomUUID().toString();
        store.paymentAttemptRepository.save(PaymentAttempt.builder()
                .orderId(orderId).customerId(CUSTOMER).gateway("A").amount(AMOUNT)
                .status(PaymentStatus.SUCCESS).createdAt(Instant.now()).build());
        ledgerService.post("txn-capture-" + orderId, orderId, List.of(
                LedgerLine.debit(LedgerEntry.customerAccount(CUSTOMER), AMOUNT),
                LedgerLine.credit(LedgerEntry.ESCROW, AMOUNT)));
        return orderId;
    }

    private static String eventId() {
        return UUID.randomUUID().toString();
    }
}
