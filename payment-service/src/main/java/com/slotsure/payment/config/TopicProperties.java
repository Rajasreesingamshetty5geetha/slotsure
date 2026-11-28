package com.slotsure.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka topic names, read from slotsure.topics.* in application.yml.
 */
@ConfigurationProperties(prefix = "slotsure.topics")
public record TopicProperties(
        String paymentRequested,
        String paymentCaptured,
        String paymentFailed,
        String refundRequested,
        String refundCompleted,
        String escrowRelease,
        String codCollect) {
}
