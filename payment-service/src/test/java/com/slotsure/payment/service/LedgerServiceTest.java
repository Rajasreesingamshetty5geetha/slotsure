package com.slotsure.payment.service;

import com.slotsure.payment.dto.LedgerLine;
import com.slotsure.payment.exception.LedgerImbalanceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceTest {

    private final InMemoryStore store = new InMemoryStore();
    private final LedgerService ledgerService = new LedgerService(store.ledgerEntryRepository);

    @Test
    void refusesUnbalancedTransaction() {
        assertThatThrownBy(() -> ledgerService.post("txn-1", "order-1", List.of(
                LedgerLine.debit("CUSTOMER:c1", new BigDecimal("100.00")),
                LedgerLine.credit("ESCROW", new BigDecimal("99.99")))))
                .isInstanceOf(LedgerImbalanceException.class)
                .hasMessageContaining("unbalanced");

        assertThat(store.ledger).isEmpty();
    }

    @Test
    void refusesSingleLegAndNegativeAmounts() {
        assertThatThrownBy(() -> ledgerService.post("txn-2", "order-1", List.of(
                LedgerLine.debit("CUSTOMER:c1", new BigDecimal("10.00")))))
                .isInstanceOf(LedgerImbalanceException.class);
        assertThatThrownBy(() -> ledgerService.post("txn-3", "order-1", List.of(
                LedgerLine.debit("CUSTOMER:c1", new BigDecimal("-10.00")),
                LedgerLine.credit("ESCROW", new BigDecimal("-10.00")))))
                .isInstanceOf(LedgerImbalanceException.class);

        assertThat(store.ledger).isEmpty();
    }

    @Test
    void postsBalancedTransactionWithScaleTwo() {
        ledgerService.post("txn-4", "order-1", List.of(
                LedgerLine.debit("CUSTOMER:c1", new BigDecimal("100")),
                LedgerLine.credit("ESCROW", new BigDecimal("100.00"))));

        assertThat(store.ledger).hasSize(2).allMatch(e -> e.getTxnId().equals("txn-4")
                && e.getDebit().scale() == 2 && e.getCredit().scale() == 2);
        assertThat(ledgerService.check().difference()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
