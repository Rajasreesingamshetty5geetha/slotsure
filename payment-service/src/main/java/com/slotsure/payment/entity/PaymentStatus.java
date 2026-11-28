package com.slotsure.payment.entity;

/**
 * SUCCESS / FAILED: a gateway charge while booking (ONLINE).
 * CAPTURED: money taken at checkout (gateway CHECKOUT) for a PREPAID order.
 * COLLECTED: cash or UPI taken by the agent on delivery (gateway COD).
 */
public enum PaymentStatus {
    SUCCESS,
    FAILED,
    CAPTURED,
    COLLECTED
}
