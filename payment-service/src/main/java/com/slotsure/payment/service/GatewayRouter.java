package com.slotsure.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tries gateways in the order from slotsure.gateway.order and stops at the first success.
 * Returns every call made, so the caller can store each attempt.
 */
@Slf4j
@Component
public class GatewayRouter {

    private final List<PaymentGateway> orderedGateways;

    public GatewayRouter(List<PaymentGateway> gateways,
                         @Value("${slotsure.gateway.order:A,B}") List<String> order) {
        Map<String, PaymentGateway> byName = gateways.stream()
                .collect(Collectors.toMap(g -> g.name().toUpperCase(), Function.identity()));
        List<PaymentGateway> ordered = new ArrayList<>();
        for (String name : order) {
            PaymentGateway gateway = byName.get(name.trim().toUpperCase());
            if (gateway == null) {
                throw new IllegalStateException("Unknown gateway '" + name + "' in slotsure.gateway.order");
            }
            ordered.add(gateway);
        }
        if (ordered.isEmpty()) {
            throw new IllegalStateException("slotsure.gateway.order must name at least one gateway");
        }
        this.orderedGateways = List.copyOf(ordered);
    }

    public Outcome charge(String orderId, BigDecimal amount) {
        List<Call> calls = new ArrayList<>();
        for (PaymentGateway gateway : orderedGateways) {
            GatewayResult result;
            try {
                result = gateway.charge(orderId, amount);
            } catch (RuntimeException e) {
                result = GatewayResult.failed("gateway " + gateway.name() + " error: " + e.getMessage());
            }
            calls.add(new Call(gateway.name(), result));
            if (result.success()) {
                log.info("Payment routed orderId={} gateway={} after {} call(s)", orderId, gateway.name(), calls.size());
                return new Outcome(calls);
            }
            log.warn("Gateway failed orderId={} gateway={} error={}", orderId, gateway.name(), result.error());
        }
        return new Outcome(calls);
    }

    /** One call to one gateway. */
    public record Call(String gateway, GatewayResult result) {
    }

    /** All calls made for one charge; successful when the last call succeeded. */
    public record Outcome(List<Call> calls) {

        public boolean success() {
            return !calls.isEmpty() && calls.get(calls.size() - 1).result().success();
        }

        public String combinedError() {
            return calls.stream()
                    .filter(c -> !c.result().success())
                    .map(c -> c.gateway() + ": " + c.result().error())
                    .collect(Collectors.joining("; "));
        }
    }
}
