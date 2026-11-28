package com.slotsure.payment.service;

import com.slotsure.payment.config.PaymentProperties;
import com.slotsure.payment.dto.AdminPaymentsResponse;
import com.slotsure.payment.dto.LedgerLine;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);
    /** 12:00 in Bengaluru on DATE. */
    private static final Instant NOON = Instant.parse("2026-11-28T06:30:00Z");

    private final InMemoryStore store = new InMemoryStore();
    private final LedgerService ledgerService = new LedgerService(store.ledgerEntryRepository);
    private final AdminService adminService = new AdminService(store.ledgerEntryRepository,
            store.paymentAttemptRepository, ledgerService,
            new PaymentProperties(ZoneId.of("Asia/Kolkata"), new PaymentProperties.DemoData(false, 40, 80)));

    @Test
    void summarisesOneDayInTheConfiguredZone() {
        capture("o-1", "1000.00", NOON);
        capture("o-2", "500.00", NOON);
        capture("o-old", "700.00", NOON.minusSeconds(86_400));
        post("o-1", LedgerEntry.ESCROW, LedgerEntry.MERCHANT, "1000.00", NOON.plusSeconds(3600));
        post("o-2", LedgerEntry.ESCROW, LedgerEntry.walletAccount("c-1"), "500.00", NOON.plusSeconds(3600));
        post("o-3", LedgerEntry.CASH_COLLECTED, LedgerEntry.MERCHANT, "300.00", NOON.plusSeconds(7200));
        // 23:59 IST on the 27th is the day before; 00:10 IST on the 29th is the day after.
        post("o-4", LedgerEntry.CASH_COLLECTED, LedgerEntry.MERCHANT, "99.00", Instant.parse("2026-11-27T18:29:00Z"));
        post("o-5", LedgerEntry.CASH_COLLECTED, LedgerEntry.MERCHANT, "88.00", Instant.parse("2026-11-28T18:40:00Z"));

        AdminPaymentsResponse summary = adminService.summary(DATE);

        assertThat(summary.date()).isEqualTo(DATE);
        assertThat(summary.prepaidCaptured()).isEqualByComparingTo("1500.00");
        assertThat(summary.escrowReleased()).isEqualByComparingTo("1000.00");
        assertThat(summary.refunds()).isEqualByComparingTo("500.00");
        assertThat(summary.codCollected()).isEqualByComparingTo("300.00");
        assertThat(summary.escrowHeld()).isEqualByComparingTo("700.00");
        assertThat(summary.ledgerDifference()).isEqualByComparingTo("0");
        assertThat(summary.prepaidCaptured().scale()).isEqualTo(2);
    }

    private void capture(String orderId, String amount, Instant at) {
        store.paymentAttemptRepository.save(PaymentAttempt.builder().orderId(orderId).customerId("c-1")
                .gateway("A").amount(new BigDecimal(amount)).status(PaymentStatus.SUCCESS).createdAt(at).build());
        post(orderId, LedgerEntry.customerAccount("c-1"), LedgerEntry.ESCROW, amount, at);
    }

    private void post(String orderId, String debitAccount, String creditAccount, String amount, Instant at) {
        ledgerService.post(orderId + "-" + at, orderId, List.of(
                LedgerLine.debit(debitAccount, new BigDecimal(amount)),
                LedgerLine.credit(creditAccount, new BigDecimal(amount))), at, false);
    }
}
