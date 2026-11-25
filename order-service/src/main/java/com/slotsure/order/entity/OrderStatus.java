package com.slotsure.order.entity;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED;

    /** Only PENDING can move, and only to one of the final statuses. */
    public boolean canMoveTo(OrderStatus next) {
        return this == PENDING && next != PENDING;
    }

    public boolean isFinal() {
        return this != PENDING;
    }
}
