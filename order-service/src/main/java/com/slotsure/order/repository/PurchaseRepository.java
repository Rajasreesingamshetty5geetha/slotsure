package com.slotsure.order.repository;

import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.PurchaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, String> {

    List<Purchase> findByCustomerIdOrderByPlacedAtDesc(String customerId);

    long countByCustomerIdAndPaymentModeAndStatus(String customerId, PaymentMode paymentMode, PurchaseStatus status);

    /** Row lock, so two orders for one purchase cannot both pass the "already scheduled" check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") String id);
}
