package com.slotsure.payment.service;

import com.slotsure.payment.dto.LedgerLine;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import com.slotsure.payment.entity.ProcessedEvent;
import com.slotsure.payment.event.CodCollectEvent;
import com.slotsure.payment.event.EscrowReleaseEvent;
import com.slotsure.payment.repository.LedgerEntryRepository;
import com.slotsure.payment.repository.PaymentAttemptRepository;
import com.slotsure.payment.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pays the merchant when an order is delivered (CONTRACT V2.3).
 * escrow.release: debit ESCROW, credit MERCHANT. A PREPAID order was paid at checkout, which this
 * service never saw, so its checkout capture (debit CUSTOMER, credit ESCROW) is recorded first.
 * cod.collect: debit CASH_COLLECTED, credit MERCHANT, plus a COD payment record.
 * Idempotent twice over: by eventId, and because an order whose MERCHANT leg exists is already settled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    static final Set<PaymentStatus> CAPTURES = EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.CAPTURED);
    static final String UNKNOWN_CUSTOMER = "UNKNOWN";

    private final LedgerService ledgerService;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void handleEscrowRelease(String eventId, EscrowReleaseEvent event) {
        String orderId = event.orderId();
        if (!markProcessed(eventId, orderId, "escrow.release") || alreadySettled(orderId)) {
            return;
        }
        Optional<PaymentAttempt> capture = paymentAttemptRepository.findFirstByOrderIdAndStatusIn(orderId, CAPTURES);
        if (capture.isPresent() && refunded(orderId, capture.get().getCustomerId())) {
            log.warn("Escrow release ignored orderId={}: the payment was refunded", orderId);
            return;
        }
        BigDecimal amount;
        if (capture.isEmpty()) {
            amount = LedgerService.money(event.amount());
            requirePositive(amount, orderId);
            String customerId = customerOrUnknown(event.customerId());
            PaymentAttempt checkout = paymentAttemptRepository.save(PaymentAttempt.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .gateway(PaymentAttempt.CHECKOUT)
                    .amount(amount)
                    .status(PaymentStatus.CAPTURED)
                    .createdAt(Instant.now())
                    .build());
            ledgerService.post(UUID.randomUUID().toString(), orderId, List.of(
                    LedgerLine.debit(LedgerEntry.customerAccount(customerId), amount),
                    LedgerLine.credit(LedgerEntry.ESCROW, amount)));
            log.info("Recorded checkout capture orderId={} paymentId={} amount={}", orderId, checkout.getId(), amount);
        } else {
            amount = LedgerService.money(capture.get().getAmount());
            if (event.amount() != null && LedgerService.money(event.amount()).compareTo(amount) != 0) {
                log.warn("Release amount {} differs from captured amount {}, releasing captured amount orderId={}",
                        event.amount(), amount, orderId);
            }
        }
        ledgerService.post(UUID.randomUUID().toString(), orderId, List.of(
                LedgerLine.debit(LedgerEntry.ESCROW, amount),
                LedgerLine.credit(LedgerEntry.MERCHANT, amount)));
        log.info("Escrow released to merchant orderId={} amount={}", orderId, amount);
    }

    @Transactional
    public void handleCodCollect(String eventId, CodCollectEvent event) {
        String orderId = event.orderId();
        if (!markProcessed(eventId, orderId, "cod.collect") || alreadySettled(orderId)) {
            return;
        }
        BigDecimal amount = LedgerService.money(event.amount());
        requirePositive(amount, orderId);
        PaymentAttempt collected = paymentAttemptRepository.save(PaymentAttempt.builder()
                .orderId(orderId)
                .customerId(customerOrUnknown(event.customerId()))
                .gateway(PaymentAttempt.COD)
                .amount(amount)
                .status(PaymentStatus.COLLECTED)
                .createdAt(Instant.now())
                .build());
        ledgerService.post(UUID.randomUUID().toString(), orderId, List.of(
                LedgerLine.debit(LedgerEntry.CASH_COLLECTED, amount),
                LedgerLine.credit(LedgerEntry.MERCHANT, amount)));
        log.info("Cash on delivery collected orderId={} paymentId={} amount={}", orderId, collected.getId(), amount);
    }

    private boolean alreadySettled(String orderId) {
        if (ledgerEntryRepository.existsByOrderIdAndAccount(orderId, LedgerEntry.MERCHANT)) {
            log.info("Order already settled to the merchant, skipping orderId={}", orderId);
            return true;
        }
        return false;
    }

    private boolean refunded(String orderId, String customerId) {
        return ledgerEntryRepository.existsByOrderIdAndAccount(orderId, LedgerEntry.walletAccount(customerId));
    }

    /** A bad amount can never succeed, so it is rejected (and ends in the DLT) instead of retried forever. */
    private static void requirePositive(BigDecimal amount, String orderId) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Settlement amount must be greater than 0, orderId=" + orderId);
        }
    }

    private static String customerOrUnknown(String customerId) {
        return customerId == null || customerId.isBlank() ? UNKNOWN_CUSTOMER : customerId;
    }

    /** Returns false (and logs) when the event was already handled. */
    private boolean markProcessed(String eventId, String orderId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate {} orderId={} eventId={}", eventType, orderId, eventId);
            return false;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
        return true;
    }
}
