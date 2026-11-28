package com.slotsure.order.entity;

/** How a purchase was placed at checkout (CONTRACT V2.3). */
public enum PaymentMode {
    PREPAID,
    COD;

    /** A PREPAID purchase is booked with PREPAID; a COD purchase with ONLINE or CASH. */
    public boolean allows(PaymentChoice choice) {
        return switch (this) {
            case PREPAID -> choice == PaymentChoice.PREPAID;
            case COD -> choice == PaymentChoice.ONLINE || choice == PaymentChoice.CASH;
        };
    }
}
