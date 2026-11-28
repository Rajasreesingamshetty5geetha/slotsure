package com.slotsure.dispatch.service;

import java.util.List;
import java.util.stream.Collectors;

/** A compact text form of a plan; order-service and dispatch-service pin the same value for their copies. */
final class DemoPlanFingerprint {

    /** Same string as in order-service and dispatch-service. */
    static final String EXPECTED_2026_11_28 = "57:b705b31d:63dfbab0-be41-3d25-955b-5916dcff3a3b";

    private DemoPlanFingerprint() {
    }

    static String of(List<DemoPlan.Booking> bookings) {
        String body = bookings.stream()
                .map(b -> b.areaName().substring(0, 3) + b.startHour() + (b.failed() ? "F" : "")
                        + b.agent().name().charAt(0) + b.deliveredMinute() + b.choice().charAt(0) + b.itemIndex()
                        + "/" + b.bookedMinutesBefore())
                .collect(Collectors.joining(","));
        return bookings.size() + ":" + Integer.toHexString(body.hashCode()) + ":" + bookings.get(0).orderId();
    }
}
