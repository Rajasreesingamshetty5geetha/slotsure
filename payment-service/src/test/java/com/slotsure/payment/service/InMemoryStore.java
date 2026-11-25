package com.slotsure.payment.service;

import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import com.slotsure.payment.entity.ProcessedEvent;
import com.slotsure.payment.repository.LedgerEntryRepository;
import com.slotsure.payment.repository.PaymentAttemptRepository;
import com.slotsure.payment.repository.ProcessedEventRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Repository mocks backed by plain lists, so service logic can be tested without MySQL.
 */
class InMemoryStore {

    final List<PaymentAttempt> attempts = new ArrayList<>();
    final List<LedgerEntry> ledger = new ArrayList<>();
    final Set<String> processedEventIds = new HashSet<>();

    final PaymentAttemptRepository paymentAttemptRepository = mock(PaymentAttemptRepository.class);
    final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);

    @SuppressWarnings("unchecked")
    InMemoryStore() {
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(inv -> {
            PaymentAttempt a = inv.getArgument(0);
            a.setId(UUID.randomUUID().toString());
            attempts.add(a);
            return a;
        });
        when(paymentAttemptRepository.findFirstByOrderIdAndStatus(anyString(), any(PaymentStatus.class)))
                .thenAnswer(inv -> attempts.stream()
                        .filter(a -> a.getOrderId().equals(inv.getArgument(0)) && a.getStatus() == inv.getArgument(1))
                        .findFirst());
        when(paymentAttemptRepository.findByOrderIdOrderByCreatedAtAsc(anyString()))
                .thenAnswer(inv -> attempts.stream().filter(a -> a.getOrderId().equals(inv.getArgument(0))).toList());

        when(ledgerEntryRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            List<LedgerEntry> rows = new ArrayList<>();
            ((Iterable<LedgerEntry>) inv.getArgument(0)).forEach(rows::add);
            ledger.addAll(rows);
            return rows;
        });
        when(ledgerEntryRepository.existsByOrderIdAndAccount(anyString(), anyString()))
                .thenAnswer(inv -> ledger.stream().anyMatch(e ->
                        e.getOrderId().equals(inv.getArgument(0)) && e.getAccount().equals(inv.getArgument(1))));
        when(ledgerEntryRepository.sumDebit())
                .thenAnswer(inv -> ledger.stream().map(LedgerEntry::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add));
        when(ledgerEntryRepository.sumCredit())
                .thenAnswer(inv -> ledger.stream().map(LedgerEntry::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add));

        when(processedEventRepository.existsById(anyString()))
                .thenAnswer(inv -> processedEventIds.contains((String) inv.getArgument(0)));
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(inv -> {
            ProcessedEvent e = inv.getArgument(0);
            if (!processedEventIds.add(e.getEventId())) {
                throw new IllegalStateException("duplicate key " + e.getEventId());
            }
            return e;
        });
    }

    long ledgerTransactions() {
        return ledger.stream().map(LedgerEntry::getTxnId).distinct().count();
    }
}
