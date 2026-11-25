package com.slotsure.slot.dto;

public enum PriceTag {
    GREEN,
    NORMAL,
    BUSY;

    /** GREEN above 60% remaining, BUSY below 20% remaining, otherwise NORMAL. */
    public static PriceTag of(long remaining, int capacity) {
        if (remaining * 100 > capacity * 60L) {
            return GREEN;
        }
        if (remaining * 100 < capacity * 20L) {
            return BUSY;
        }
        return NORMAL;
    }
}
