package com.slotsure.payment.service;

import com.slotsure.payment.config.PaymentProperties;
import com.slotsure.payment.dto.AdminPaymentsResponse;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoDataSeederTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);
    private static final PaymentProperties PROPERTIES = new PaymentProperties(ZoneId.of("Asia/Kolkata"),
            new PaymentProperties.DemoData(true, 40, 80));

    private final InMemoryStore store = new InMemoryStore();
    private final LedgerService ledgerService = new LedgerService(store.ledgerEntryRepository);
    private final DemoDataSeeder seeder = new DemoDataSeeder(store.paymentAttemptRepository, ledgerService,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), PROPERTIES);

    @Test
    void aPastDayIsFullySettledAndTheLedgerBalances() {
        seeder.seed(DATE, Instant.parse("2026-11-29T03:00:00Z"));

        assertThat(ledgerService.check().difference()).isEqualByComparingTo("0");
        assertThat(store.attempts).isNotEmpty().allMatch(PaymentAttempt::isDemo);
        assertThat(store.ledger).allMatch(LedgerEntry::isDemo);
        assertThat(store.attempts).extracting(PaymentAttempt::getGateway).contains("CHECKOUT", "COD");

        // Every booking that did not fail was delivered: what came into escrow has gone to the merchant.
        List<DemoPlan.Booking> plan = DemoPlan.forDate(DATE, 40, 80);
        BigDecimal settled = plan.stream()
                .filter(b -> !b.failed())
                .map(DemoPlan.Booking::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(store.balance(LedgerEntry.MERCHANT)).isEqualByComparingTo(settled);
        BigDecimal failedPrepaid = plan.stream()
                .filter(b -> b.failed() && b.choice().equals("PREPAID"))
                .map(DemoPlan.Booking::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(store.balance(LedgerEntry.ESCROW)).isEqualByComparingTo(failedPrepaid);
    }

    @Test
    void paymentsBelongToTheSharedDemoOrders() {
        seeder.seed(DATE, Instant.parse("2026-11-29T03:00:00Z"));

        List<String> planOrderIds = DemoPlan.forDate(DATE, 40, 80).stream().map(DemoPlan.Booking::orderId).toList();
        assertThat(store.attempts).allSatisfy(attempt -> assertThat(planOrderIds).contains(attempt.getOrderId()));
        assertThat(store.attempts).filteredOn(a -> a.getStatus() == PaymentStatus.FAILED)
                .allSatisfy(a -> assertThat(a.getGateway()).isIn("A", "B"));
    }

    @Test
    void laterSlotsTodayStillHoldTheirMoneyInEscrow() {
        // 14:00 in Bengaluru.
        seeder.seed(DATE, Instant.parse("2026-11-28T08:30:00Z"));

        assertThat(store.balance(LedgerEntry.ESCROW)).isPositive();
        assertThat(ledgerService.check().difference()).isEqualByComparingTo("0");
        AdminPaymentsResponse summary = new AdminService(store.ledgerEntryRepository, store.paymentAttemptRepository,
                ledgerService, PROPERTIES).summary(DATE);
        assertThat(summary.escrowHeld()).isEqualByComparingTo(store.balance(LedgerEntry.ESCROW));
        assertThat(summary.ledgerDifference()).isEqualByComparingTo("0");
    }

    @Test
    void aSeededDateIsLeftAlone() {
        seeder.seed(DATE, Instant.parse("2026-11-29T03:00:00Z"));
        int rows = store.ledger.size();

        assertThat(seeder.seed(DATE, Instant.parse("2026-11-29T03:00:00Z"))).isZero();
        assertThat(store.ledger).hasSize(rows);
    }
}
