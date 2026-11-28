package com.slotsure.order.entity;

/** How an order is paid (CONTRACT V2.3). Only ONLINE takes a payment while booking. */
public enum PaymentChoice {
    PREPAID,
    ONLINE,
    CASH;

    public boolean paysAtBooking() {
        return this == ONLINE;
    }

    /** Orders from before payment choices existed ran the payment saga, like ONLINE. */
    public static PaymentChoice orDefault(PaymentChoice choice) {
        return choice != null ? choice : ONLINE;
    }
}
