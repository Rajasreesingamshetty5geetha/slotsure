package com.slotsure.payment.service;

import com.slotsure.payment.dto.LedgerCheckResponse;
import com.slotsure.payment.dto.LedgerEntryResponse;
import com.slotsure.payment.dto.LedgerLine;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.exception.LedgerImbalanceException;
import com.slotsure.payment.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Double-entry ledger. post() is the only code path that writes ledger rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public void post(String txnId, String orderId, List<LedgerLine> entries) {
        if (entries == null || entries.size() < 2) {
            throw new LedgerImbalanceException("Ledger transaction " + txnId + " needs at least two entries");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (LedgerLine line : entries) {
            if (line.debit() == null || line.credit() == null
                    || line.debit().signum() < 0 || line.credit().signum() < 0) {
                throw new LedgerImbalanceException("Ledger transaction " + txnId
                        + " has a missing or negative amount on account " + line.account());
            }
            totalDebit = totalDebit.add(money(line.debit()));
            totalCredit = totalCredit.add(money(line.credit()));
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            log.warn("Refused unbalanced ledger txn orderId={} txnId={} debit={} credit={}",
                    orderId, txnId, totalDebit, totalCredit);
            throw new LedgerImbalanceException("Ledger transaction " + txnId + " is unbalanced: debit "
                    + totalDebit + " != credit " + totalCredit);
        }

        Instant now = Instant.now();
        List<LedgerEntry> rows = entries.stream()
                .map(line -> LedgerEntry.builder()
                        .txnId(txnId)
                        .orderId(orderId)
                        .account(line.account())
                        .debit(money(line.debit()))
                        .credit(money(line.credit()))
                        .createdAt(now)
                        .build())
                .toList();
        ledgerEntryRepository.saveAll(rows);
        log.info("Posted ledger txn orderId={} txnId={} amount={} legs={}", orderId, txnId, totalDebit, rows.size());
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> entriesForOrder(String orderId) {
        return ledgerEntryRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LedgerCheckResponse check() {
        BigDecimal totalDebit = money(ledgerEntryRepository.sumDebit());
        BigDecimal totalCredit = money(ledgerEntryRepository.sumCredit());
        return new LedgerCheckResponse(totalDebit, totalCredit, totalDebit.subtract(totalCredit));
    }

    static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
