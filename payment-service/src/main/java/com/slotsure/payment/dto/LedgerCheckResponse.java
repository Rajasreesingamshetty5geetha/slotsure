package com.slotsure.payment.dto;

import java.math.BigDecimal;

/**
 * Whole-ledger balance check; difference must be 0.00.
 */
public record LedgerCheckResponse(BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal difference) {
}
