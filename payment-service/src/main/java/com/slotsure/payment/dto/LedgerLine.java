package com.slotsure.payment.dto;

import java.math.BigDecimal;

/**
 * One leg of a ledger transaction passed to LedgerService.post.
 */
public record LedgerLine(String account, BigDecimal debit, BigDecimal credit) {

    public static LedgerLine debit(String account, BigDecimal amount) {
        return new LedgerLine(account, amount, BigDecimal.ZERO);
    }

    public static LedgerLine credit(String account, BigDecimal amount) {
        return new LedgerLine(account, BigDecimal.ZERO, amount);
    }
}
