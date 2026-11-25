package com.slotsure.slot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "slotsure")
public record SlotsureProperties(
        @DefaultValue SlotSettings slot,
        @DefaultValue HoldSettings hold,
        @DefaultValue Topics topics) {

    public record SlotSettings(
            @DefaultValue("10") int capacity,
            @DefaultValue("8") int firstHour,
            @DefaultValue("22") int lastHour) {
    }

    public record HoldSettings(
            @DefaultValue("300") long expirySeconds,
            @DefaultValue("24") long idempotencyTtlHours,
            @DefaultValue("30000") long sweepIntervalMs) {
    }

    public record Topics(
            @DefaultValue("slot.confirm") String slotConfirm,
            @DefaultValue("slot.release") String slotRelease,
            @DefaultValue("slot.confirmed") String slotConfirmed,
            @DefaultValue("slot.confirm.failed") String slotConfirmFailed,
            @DefaultValue("slot.released") String slotReleased) {
    }
}
