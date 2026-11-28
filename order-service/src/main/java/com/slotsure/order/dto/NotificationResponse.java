package com.slotsure.order.dto;

import com.slotsure.order.entity.Notification;
import com.slotsure.order.entity.NotificationType;

import java.time.Instant;

public record NotificationResponse(String id,
                                   String orderId,
                                   NotificationType type,
                                   String title,
                                   String body,
                                   Instant createdAt,
                                   boolean read) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getOrderId(), notification.getType(),
                notification.getTitle(), notification.getBody(), notification.getCreatedAt(), notification.isRead());
    }
}
