package com.slotsure.slot.event;

/** Payload of slot.released; reason is EXPIRED or COMPENSATION. */
public record SlotReleasedPayload(String holdId, String slotId, String reason) {

    public static final String EXPIRED = "EXPIRED";
    public static final String COMPENSATION = "COMPENSATION";
}
