package com.slotsure.slot.dto;

import com.slotsure.slot.entity.ReservationStatus;

import java.time.Instant;

public record HoldStatusResponse(String holdId, String slotId, ReservationStatus status, Instant expiresAt) {
}
