package com.slotsure.order.repository;

import com.slotsure.order.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    List<Notification> findByCustomerIdAndCreatedAtAfterOrderByCreatedAtDesc(String customerId, Instant after,
                                                                             Pageable pageable);
}
