package com.slotsure.payment.repository;

import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {

    List<PaymentAttempt> findByOrderIdOrderByCreatedAtAsc(String orderId);

    Optional<PaymentAttempt> findFirstByOrderIdAndStatus(String orderId, PaymentStatus status);

    Optional<PaymentAttempt> findFirstByOrderIdAndStatusIn(String orderId, Collection<PaymentStatus> statuses);

    boolean existsByDemoTrueAndOrderIdIn(Collection<String> orderIds);

    @Query("""
            select coalesce(sum(a.amount), 0) from PaymentAttempt a
            where a.status in :statuses and a.createdAt >= :from and a.createdAt < :to
            """)
    BigDecimal sumAmount(@Param("statuses") Collection<PaymentStatus> statuses,
                         @Param("from") Instant from, @Param("to") Instant to);
}
