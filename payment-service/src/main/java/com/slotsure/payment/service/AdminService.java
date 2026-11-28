package com.slotsure.payment.service;

import com.slotsure.payment.config.PaymentProperties;
import com.slotsure.payment.dto.AdminPaymentsResponse;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.repository.LedgerEntryRepository;
import com.slotsure.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Money totals for one day, each from one SUM query over that day's rows. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final LedgerService ledgerService;
    private final PaymentProperties properties;

    public LocalDate today() {
        return LocalDate.now(properties.zone());
    }

    @Transactional(readOnly = true)
    public AdminPaymentsResponse summary(LocalDate date) {
        Instant from = date.atStartOfDay(properties.zone()).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(properties.zone()).toInstant();

        BigDecimal captured = paymentAttemptRepository.sumAmount(SettlementService.CAPTURES, from, to);
        BigDecimal refunds = ledgerEntryRepository.sumCreditOnPrefix(LedgerEntry.WALLET_PREFIX, from, to);
        // Escrow is debited by releases to the merchant and by refunds; the releases are the rest.
        BigDecimal escrowOut = ledgerEntryRepository.sumDebitOn(LedgerEntry.ESCROW, from, to);
        BigDecimal codCollected = ledgerEntryRepository.sumDebitOn(LedgerEntry.CASH_COLLECTED, from, to);
        BigDecimal escrowHeld = ledgerEntryRepository.balanceBefore(LedgerEntry.ESCROW, to);

        return new AdminPaymentsResponse(date,
                LedgerService.money(captured),
                LedgerService.money(escrowHeld),
                LedgerService.money(escrowOut.subtract(refunds)),
                LedgerService.money(codCollected),
                LedgerService.money(refunds),
                ledgerService.check().difference());
    }
}
