package com.slotsure.payment.repository;

import com.slotsure.payment.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findByOrderIdOrderByCreatedAtAsc(String orderId);

    boolean existsByOrderIdAndAccount(String orderId, String account);

    @Query("select coalesce(sum(e.debit), 0) from LedgerEntry e")
    BigDecimal sumDebit();

    @Query("select coalesce(sum(e.credit), 0) from LedgerEntry e")
    BigDecimal sumCredit();

    @Query("""
            select coalesce(sum(e.debit), 0) from LedgerEntry e
            where e.account = :account and e.createdAt >= :from and e.createdAt < :to
            """)
    BigDecimal sumDebitOn(@Param("account") String account, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(e.credit), 0) from LedgerEntry e
            where e.account like concat(:prefix, '%') and e.createdAt >= :from and e.createdAt < :to
            """)
    BigDecimal sumCreditOnPrefix(@Param("prefix") String prefix, @Param("from") Instant from, @Param("to") Instant to);

    /** What the account holds at the given instant: credits minus debits before it. */
    @Query("""
            select coalesce(sum(e.credit), 0) - coalesce(sum(e.debit), 0) from LedgerEntry e
            where e.account = :account and e.createdAt < :to
            """)
    BigDecimal balanceBefore(@Param("account") String account, @Param("to") Instant to);
}
