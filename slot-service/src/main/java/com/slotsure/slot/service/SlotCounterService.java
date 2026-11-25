package com.slotsure.slot.service;

import com.slotsure.slot.entity.ReservationStatus;
import com.slotsure.slot.entity.Slot;
import com.slotsure.slot.exception.ApiException;
import com.slotsure.slot.repository.ReservationRepository;
import com.slotsure.slot.repository.SlotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the Redis counter slot:remaining:{slotId}, the fast source of truth for free places.
 * When a counter is missing it is rebuilt from MySQL: capacity minus HELD and CONFIRMED reservations.
 */
@Service
@Slf4j
public class SlotCounterService {

    private static final String KEY_PREFIX = "slot:remaining:";
    private static final EnumSet<ReservationStatus> ACTIVE = EnumSet.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED);

    private final StringRedisTemplate redis;
    private final RedisScript<Long> holdScript;
    private final RedisScript<Long> releaseScript;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;

    public SlotCounterService(StringRedisTemplate redis,
                              @Qualifier("holdScript") RedisScript<Long> holdScript,
                              @Qualifier("releaseScript") RedisScript<Long> releaseScript,
                              SlotRepository slotRepository,
                              ReservationRepository reservationRepository) {
        this.redis = redis;
        this.holdScript = holdScript;
        this.releaseScript = releaseScript;
        this.slotRepository = slotRepository;
        this.reservationRepository = reservationRepository;
    }

    public static String key(String slotId) {
        return KEY_PREFIX + slotId;
    }

    /**
     * Takes one place from the slot. Returns false when the slot is full.
     * Throws SLOT_NOT_FOUND when the counter is missing and the slot does not exist in MySQL.
     */
    public boolean tryTake(String slotId) {
        String key = key(slotId);
        if (runHoldScript(key)) {
            return true;
        }
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return false;
        }
        Slot slot = slotRepository.findById(slotId).orElseThrow(() -> ApiException.slotNotFound(slotId));
        initialise(slot);
        return runHoldScript(key);
    }

    /** Gives one place back. Skipped when the counter is missing, because it will be rebuilt from MySQL. */
    public void giveBack(String slotId) {
        Long result = redis.execute(releaseScript, List.of(key(slotId)));
        if (result == null || result < 0) {
            log.warn("Counter for slot {} is missing, it will be rebuilt from MySQL on next use", slotId);
        } else {
            log.info("Counter for slot {} incremented to {}", slotId, result);
        }
    }

    /** Returns remaining places for each slot, creating any missing counters with SETNX. */
    public Map<String, Long> remaining(List<Slot> slots) {
        List<String> keys = slots.stream().map(slot -> key(slot.getId())).toList();
        List<String> values = redis.opsForValue().multiGet(keys);
        Map<String, Long> remaining = new HashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            String value = values == null ? null : values.get(i);
            remaining.put(slot.getId(), value != null ? Long.parseLong(value) : initialise(slot));
        }
        return remaining;
    }

    /** Creates the counter with SETNX and returns its current value (which may come from a concurrent writer). */
    public long initialise(Slot slot) {
        String key = key(slot.getId());
        long active = reservationRepository.countBySlotIdAndStatusIn(slot.getId(), ACTIVE);
        long value = Math.max(0, slot.getCapacity() - active);
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, Long.toString(value)))) {
            log.info("Initialised counter for slot {} to {}", slot.getId(), value);
        }
        String current = redis.opsForValue().get(key);
        return current != null ? Long.parseLong(current) : value;
    }

    private boolean runHoldScript(String key) {
        Long result = redis.execute(holdScript, List.of(key));
        return result != null && result == 1L;
    }
}
