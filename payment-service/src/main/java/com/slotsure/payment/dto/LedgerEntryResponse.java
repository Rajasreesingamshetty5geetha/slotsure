package com.slotsure.payment.dto;

import com.slotsure.payment.entity.LedgerEntry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record LedgerEntryResponse(
        String id,
        String txnId,
        String orderId,
        String account,
        BigDecimal debit,
        BigDecimal credit,
        Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(e.getId(), e.getTxnId(), e.getOrderId(), e.getAccount(),
                e.getDebit().setScale(2, RoundingMode.HALF_UP), e.getCredit().setScale(2, RoundingMode.HALF_UP),
                e.getCreatedAt());
    }
}
