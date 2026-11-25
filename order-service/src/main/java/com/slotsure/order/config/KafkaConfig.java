package com.slotsure.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Key/value serialisers (String/String) and the consumer group id come from application.yml.
 * Spring Boot wires this error handler into its default listener container factory:
 * a failing record is retried twice, one second apart, then published to "topic.DLT".
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                                 @Value("${slotsure.kafka.retry-interval-ms}") long retryIntervalMs,
                                                 @Value("${slotsure.kafka.retry-attempts}") long retryAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
    }
}
