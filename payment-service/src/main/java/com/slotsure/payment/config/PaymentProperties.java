package com.slotsure.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/** zone decides which instants fall on an admin "date"; demo data seeding settings (CONTRACT V2.10). */
@ConfigurationProperties(prefix = "slotsure")
public record PaymentProperties(
        @DefaultValue("Asia/Kolkata") ZoneId zone,
        @DefaultValue DemoData demoData) {

    public record DemoData(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("40") int minBookingsPerDay,
            @DefaultValue("80") int maxBookingsPerDay) {
    }
}
