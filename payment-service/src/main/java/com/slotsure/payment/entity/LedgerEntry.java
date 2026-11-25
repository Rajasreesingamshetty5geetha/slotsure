package com.slotsure.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only double-entry ledger row. No setters, @Immutable and non-updatable columns:
 * rows are inserted through LedgerService.post and never updated or deleted.
 * Accounts: CUSTOMER:{customerId}, ESCROW, WALLET:{customerId}.
 */
@Entity
@Immutable
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_entries_order", columnList = "order_id"),
        @Index(name = "idx_ledger_entries_txn", columnList = "txn_id")
})
@Getter
@Builder
public class LedgerEntry {

    public static final String ESCROW = "ESCROW";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "txn_id", nullable = false, length = 36, updatable = false)
    private String txnId;

    @Column(name = "order_id", nullable = false, length = 36, updatable = false)
    private String orderId;

    @Column(nullable = false, length = 64, updatable = false)
    private String account;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal debit;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal credit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(String id, String txnId, String orderId, String account,
                        BigDecimal debit, BigDecimal credit, Instant createdAt) {
        this.id = id;
        this.txnId = txnId;
        this.orderId = orderId;
        this.account = account;
        this.debit = debit;
        this.credit = credit;
        this.createdAt = createdAt;
    }

    public static String customerAccount(String customerId) {
        return "CUSTOMER:" + customerId;
    }

    public static String walletAccount(String customerId) {
        return "WALLET:" + customerId;
    }
}
