package com.slotsure.dispatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Serialisers and the consumer group come from application.yml (String key and value, group "dispatch-service").
 * Spring Boot wires this error handler into the default listener container factory:
 * a failing record is retried twice, one second apart, then published to "topic.DLT".
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                                 @Value("${slotsure.kafka.retry-interval-ms:1000}") long retryIntervalMs,
                                                 @Value("${slotsure.kafka.retry-attempts:2}") long retryAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
        // A malformed message can never succeed, so it goes straight to the DLT.
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }
}
