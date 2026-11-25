package com.slotsure.payment.repository;

import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {

    List<PaymentAttempt> findByOrderIdOrderByCreatedAtAsc(String orderId);

    Optional<PaymentAttempt> findFirstByOrderIdAndStatus(String orderId, PaymentStatus status);
}
