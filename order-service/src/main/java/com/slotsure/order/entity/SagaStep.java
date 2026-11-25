package com.slotsure.order.entity;

public enum SagaStep {
    PAYMENT_REQUESTED,
    SLOT_CONFIRMING,
    COMPENSATING,
    DONE;

    /**
     * Happy path:   PAYMENT_REQUESTED -> SLOT_CONFIRMING -> DONE
     * Compensation: PAYMENT_REQUESTED or SLOT_CONFIRMING -> COMPENSATING -> DONE
     */
    public boolean canMoveTo(SagaStep next) {
        return switch (this) {
            case PAYMENT_REQUESTED -> next == SLOT_CONFIRMING || next == COMPENSATING;
            case SLOT_CONFIRMING -> next == DONE || next == COMPENSATING;
            case COMPENSATING -> next == DONE;
            case DONE -> false;
        };
    }
}
