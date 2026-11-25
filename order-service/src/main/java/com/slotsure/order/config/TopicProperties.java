package com.slotsure.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka topic names from application.yml (slotsure.topics.*).
 * The topic name is also used as the envelope's eventType.
 */
@ConfigurationProperties(prefix = "slotsure.topics")
public record TopicProperties(
        String paymentRequested,
        String paymentCaptured,
        String paymentFailed,
        String slotConfirm,
        String slotRelease,
        String slotConfirmed,
        String slotConfirmFailed,
        String refundRequested,
        String refundCompleted) {
}
