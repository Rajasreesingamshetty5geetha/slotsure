package com.slotsure.slot.event;

/** Payload of slot.confirm.failed; reason is HOLD_EXPIRED or HOLD_NOT_FOUND. */
public record SlotConfirmFailedPayload(String orderId, String holdId, String reason) {

    public static final String HOLD_EXPIRED = "HOLD_EXPIRED";
    public static final String HOLD_NOT_FOUND = "HOLD_NOT_FOUND";
}
