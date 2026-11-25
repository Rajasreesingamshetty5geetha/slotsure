package com.slotsure.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka setup per CONTRACT 5.1.
 * String key/value (de)serialisers are configured in application.yml; JSON is built and parsed
 * with Jackson ObjectMapper, so no Spring Kafka type headers are used.
 * Spring Boot wires this error handler into the default listener container factory.
 * Failed records are retried, then sent to "&lt;topic&gt;.DLT" (the recoverer's default naming).
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${slotsure.kafka.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${slotsure.kafka.retry-attempts:2}") long retryAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Kafka delivery attempt {} failed topic={} key={}: {}",
                        attempt, record.topic(), record.key(), ex.getMessage()));
        return handler;
    }
}
