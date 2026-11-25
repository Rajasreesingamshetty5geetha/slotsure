package com.slotsure.slot.repository;

import com.slotsure.slot.entity.Reservation;
import com.slotsure.slot.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    long countBySlotIdAndStatusIn(String slotId, Collection<ReservationStatus> statuses);

    @Query("select r.id from Reservation r where r.status = :status and r.expiresAt < :now")
    List<String> findIdsByStatusAndExpiresAtBefore(@Param("status") ReservationStatus status,
                                                   @Param("now") Instant now);
}
