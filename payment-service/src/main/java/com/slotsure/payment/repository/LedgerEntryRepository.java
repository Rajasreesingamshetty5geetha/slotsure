package com.slotsure.payment.repository;

import com.slotsure.payment.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findByOrderIdOrderByCreatedAtAsc(String orderId);

    boolean existsByOrderIdAndAccount(String orderId, String account);

    @Query("select coalesce(sum(e.debit), 0) from LedgerEntry e")
    BigDecimal sumDebit();

    @Query("select coalesce(sum(e.credit), 0) from LedgerEntry e")
    BigDecimal sumCredit();
}
