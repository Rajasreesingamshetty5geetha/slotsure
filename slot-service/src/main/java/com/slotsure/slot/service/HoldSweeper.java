package com.slotsure.slot.service;

import com.slotsure.slot.entity.ReservationStatus;
import com.slotsure.slot.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Releases HELD reservations whose expiry has passed. Each release runs in its own transaction. */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldSweeper {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${slotsure.hold.sweep-interval-ms:30000}",
            initialDelayString = "${slotsure.hold.sweep-interval-ms:30000}")
    public void sweep() {
        releaseExpiredHolds();
    }

    /** Returns how many holds were released. */
    public int releaseExpiredHolds() {
        List<String> holdIds = reservationRepository.findIdsByStatusAndExpiresAtBefore(ReservationStatus.HELD, Instant.now());
        int released = 0;
        for (String holdId : holdIds) {
            try {
                if (reservationService.expire(holdId)) {
                    released++;
                }
            } catch (OptimisticLockingFailureException e) {
                log.info("Hold {} changed while expiring (probably confirmed), skipped", holdId);
            } catch (RuntimeException e) {
                log.error("Could not expire hold {}", holdId, e);
            }
        }
        if (released > 0) {
            log.info("Sweeper released {} expired holds", released);
        }
        return released;
    }
}
