package com.slotsure.order.dto;

import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.PurchaseStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseResponse(String purchaseId,
                               String customerId,
                               String itemName,
                               String category,
                               BigDecimal amount,
                               PaymentMode paymentMode,
                               Instant placedAt,
                               PurchaseStatus status,
                               String orderId) {

    public static PurchaseResponse of(Purchase purchase) {
        return new PurchaseResponse(purchase.getId(), purchase.getCustomerId(), purchase.getItemName(),
                purchase.getCategory(), purchase.getAmount(), purchase.getPaymentMode(), purchase.getPlacedAt(),
                purchase.getStatus(), purchase.getOrderId());
    }
}
