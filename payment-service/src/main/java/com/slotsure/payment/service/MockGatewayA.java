package com.slotsure.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fake provider "A": waits latency-ms, then fails with probability failure-rate.
 */
@Slf4j
@Component
public class MockGatewayA implements PaymentGateway {

    private final double failureRate;
    private final long latencyMs;

    public MockGatewayA(@Value("${slotsure.gateway.a.failure-rate:0.2}") double failureRate,
                        @Value("${slotsure.gateway.a.latency-ms:200}") long latencyMs) {
        this.failureRate = failureRate;
        this.latencyMs = latencyMs;
    }

    @Override
    public String name() {
        return "A";
    }

    @Override
    public GatewayResult charge(String orderId, BigDecimal amount) {
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return GatewayResult.failed("gateway A interrupted");
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            log.info("Gateway A declined orderId={} amount={}", orderId, amount);
            return GatewayResult.failed("gateway A declined the charge");
        }
        log.info("Gateway A charged orderId={} amount={}", orderId, amount);
        return GatewayResult.ok();
    }
}
