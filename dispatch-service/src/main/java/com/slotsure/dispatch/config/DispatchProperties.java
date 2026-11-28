package com.slotsure.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/** Everything configurable in dispatch-service, read from slotsure.* in application.yml. */
@ConfigurationProperties(prefix = "slotsure")
public record DispatchProperties(
        @DefaultValue("Asia/Kolkata") ZoneId zone,
        @DefaultValue Dispatch dispatch,
        @DefaultValue Topics topics,
        @DefaultValue DemoData demoData) {

    public record Dispatch(@DefaultValue("6") int maxDeliveriesPerAgentPerHour) {
    }

    public record Topics(
            @DefaultValue("slot.confirmed") String slotConfirmed,
            @DefaultValue("delivery.assigned") String deliveryAssigned,
            @DefaultValue("delivery.completed") String deliveryCompleted) {
    }

    public record DemoData(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("40") int minBookingsPerDay,
            @DefaultValue("80") int maxBookingsPerDay) {
    }
}
