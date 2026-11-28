package com.slotsure.order.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * PENDING -> CONFIRMED -> OUT_FOR_DELIVERY -> DELIVERED, or PENDING -> FAILED | CANCELLED.
 * CONFIRMED -> DELIVERED is allowed too, in case delivery.completed overtakes delivery.assigned.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    CANCELLED;

    /** Statuses that hold the purchase: a second order for it is refused. */
    public static final Set<OrderStatus> LIVE = EnumSet.of(PENDING, CONFIRMED, OUT_FOR_DELIVERY, DELIVERED);

    /** Statuses of a booking whose slot was confirmed. */
    public static final Set<OrderStatus> BOOKED = EnumSet.of(CONFIRMED, OUT_FOR_DELIVERY, DELIVERED);

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == FAILED || next == CANCELLED;
            case CONFIRMED -> next == OUT_FOR_DELIVERY || next == DELIVERED;
            case OUT_FOR_DELIVERY -> next == DELIVERED;
            case DELIVERED, FAILED, CANCELLED -> false;
        };
    }

    public boolean isFinal() {
        return this == DELIVERED || this == FAILED || this == CANCELLED;
    }
}
