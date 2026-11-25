package com.slotsure.slot.dto;

import java.time.Instant;

public record HoldResponse(String holdId, String slotId, Instant expiresAt) {
}
