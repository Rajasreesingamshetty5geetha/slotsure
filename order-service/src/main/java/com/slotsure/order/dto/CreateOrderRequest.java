package com.slotsure.order.dto;

import com.slotsure.order.entity.PaymentChoice;

import java.time.LocalDate;

/** POST /api/orders (CONTRACT V2.6). The amount and payment mode come from the purchase, not from here. */
public record CreateOrderRequest(
        String customerId,
        String purchaseId,
        String holdId,
        PaymentChoice paymentChoice,
        Double lat,
        Double lng,
        String address,
        String areaName,
        LocalDate slotDate,
        Integer startHour,
        Integer endHour) {
}
