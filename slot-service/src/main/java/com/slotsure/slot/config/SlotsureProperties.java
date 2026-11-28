package com.slotsure.slot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;
import java.util.List;

@ConfigurationProperties(prefix = "slotsure")
public record SlotsureProperties(
        @DefaultValue SlotSettings slot,
        @DefaultValue HoldSettings hold,
        @DefaultValue Topics topics,
        List<Area> areas,
        @DefaultValue DemoData demoData) {

    public SlotsureProperties {
        areas = areas == null ? List.of() : List.copyOf(areas);
    }

    /** zone is where slot dates and hours live; it decides what "today" means for admin and demo data. */
    public record SlotSettings(
            @DefaultValue("10") int capacity,
            @DefaultValue("8") int firstHour,
            @DefaultValue("22") int lastHour,
            @DefaultValue("Asia/Kolkata") ZoneId zone) {
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

    /** A named locality; a slot takes the name of the locality nearest to its cell. */
    public record Area(String name, double lat, double lng) {
    }

    public record DemoData(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("40") int minBookingsPerDay,
            @DefaultValue("80") int maxBookingsPerDay) {
    }
}
