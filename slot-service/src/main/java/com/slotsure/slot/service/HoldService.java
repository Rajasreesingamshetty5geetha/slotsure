package com.slotsure.slot.service;

import com.slotsure.slot.config.SlotsureProperties;
import com.slotsure.slot.dto.HoldRequest;
import com.slotsure.slot.dto.HoldResponse;
import com.slotsure.slot.dto.HoldStatusResponse;
import com.slotsure.slot.entity.Reservation;
import com.slotsure.slot.exception.ApiException;
import com.slotsure.slot.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HoldService {

    private static final String IDEMPOTENCY_PREFIX = "idem:";

    private final ReservationRepository reservationRepository;
    private final SlotCounterService slotCounterService;
    private final StringRedisTemplate redis;
    private final SlotsureProperties properties;

    public HoldResponse createHold(String idempotencyKey, HoldRequest request) {
        validate(idempotencyKey, request);

        String idemKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String existingHoldId = redis.opsForValue().get(idemKey);
        if (existingHoldId != null) {
            Reservation existing = reservationRepository.findById(existingHoldId)
                    .orElseThrow(() -> ApiException.holdNotFound(existingHoldId));
            log.info("Replaying hold {} for Idempotency-Key {}", existing.getId(), idempotencyKey);
            return toResponse(existing);
        }

        if (!slotCounterService.tryTake(request.slotId())) {
            log.info("Slot {} is full, hold rejected for customer {}", request.slotId(), request.customerId());
            throw ApiException.slotFull(request.slotId());
        }

        // Millisecond precision so a replayed response (read back from MySQL) matches the first one exactly.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Reservation reservation = new Reservation(request.slotId(), request.customerId(), idempotencyKey,
                now, now.plusSeconds(properties.hold().expirySeconds()));
        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            slotCounterService.giveBack(request.slotId());
            // Same Idempotency-Key raced in from another request and won: return its hold.
            Optional<Reservation> winner = reservationRepository.findByIdempotencyKey(idempotencyKey);
            if (winner.isPresent()) {
                log.info("Concurrent request with Idempotency-Key {} already created hold {}",
                        idempotencyKey, winner.get().getId());
                return toResponse(winner.get());
            }
            throw e;
        } catch (RuntimeException e) {
            slotCounterService.giveBack(request.slotId());
            log.error("Saving hold {} failed, place returned to slot {}", reservation.getId(), request.slotId());
            throw e;
        }

        redis.opsForValue().set(idemKey, reservation.getId(),
                Duration.ofHours(properties.hold().idempotencyTtlHours()));
        log.info("Hold {} HELD on slot {} for customer {} until {}",
                reservation.getId(), reservation.getSlotId(), reservation.getCustomerId(), reservation.getExpiresAt());
        return toResponse(reservation);
    }

    public HoldStatusResponse getHold(String holdId) {
        Reservation reservation = reservationRepository.findById(holdId)
                .orElseThrow(() -> ApiException.holdNotFound(holdId));
        return new HoldStatusResponse(reservation.getId(), reservation.getSlotId(),
                reservation.getStatus(), reservation.getExpiresAt());
    }

    private static void validate(String idempotencyKey, HoldRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Idempotency-Key header is required");
        }
        if (idempotencyKey.length() > 255) {
            throw ApiException.badRequest("Idempotency-Key must be at most 255 characters");
        }
        if (request == null || isBlank(request.slotId()) || isBlank(request.customerId())) {
            throw ApiException.badRequest("slotId and customerId are required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static HoldResponse toResponse(Reservation reservation) {
        return new HoldResponse(reservation.getId(), reservation.getSlotId(), reservation.getExpiresAt());
    }
}
