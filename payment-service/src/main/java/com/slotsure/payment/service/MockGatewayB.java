package com.slotsure.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fake provider "B": slower but more reliable than A by default.
 */
@Slf4j
@Component
public class MockGatewayB implements PaymentGateway {

    private final double failureRate;
    private final long latencyMs;

    public MockGatewayB(@Value("${slotsure.gateway.b.failure-rate:0.1}") double failureRate,
                        @Value("${slotsure.gateway.b.latency-ms:400}") long latencyMs) {
        this.failureRate = failureRate;
        this.latencyMs = latencyMs;
    }

    @Override
    public String name() {
        return "B";
    }

    @Override
    public GatewayResult charge(String orderId, BigDecimal amount) {
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return GatewayResult.failed("gateway B interrupted");
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            log.info("Gateway B declined orderId={} amount={}", orderId, amount);
            return GatewayResult.failed("gateway B declined the charge");
        }
        log.info("Gateway B charged orderId={} amount={}", orderId, amount);
        return GatewayResult.ok();
    }
}
