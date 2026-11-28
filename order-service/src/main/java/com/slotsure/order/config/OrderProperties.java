package com.slotsure.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;
import java.util.List;

/**
 * zone is where slot dates and hours live: it decides "today" and the times in notification text.
 * demoCustomers are the fixed demo users of CONTRACT V2.2.
 */
@ConfigurationProperties(prefix = "slotsure")
public record OrderProperties(
        @DefaultValue("Asia/Kolkata") ZoneId zone,
        @DefaultValue DemoData demoData) {

    public record DemoData(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("40") int minBookingsPerDay,
            @DefaultValue("80") int maxBookingsPerDay,
            List<String> customers) {

        public DemoData {
            customers = customers == null ? List.of() : List.copyOf(customers);
        }
    }
}
