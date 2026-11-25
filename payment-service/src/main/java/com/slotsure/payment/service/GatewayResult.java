package com.slotsure.payment.service;

/**
 * Outcome of one gateway call. error is null on success.
 */
public record GatewayResult(boolean success, String error) {

    public static GatewayResult ok() {
        return new GatewayResult(true, null);
    }

    public static GatewayResult failed(String error) {
        return new GatewayResult(false, error);
    }
}
