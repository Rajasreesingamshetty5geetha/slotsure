package com.slotsure.dispatch.event;

import java.time.LocalDate;

/** delivery.assigned (dispatch-service to order-service). */
public record DeliveryAssignedPayload(String orderId,
                                      String agentId,
                                      String agentName,
                                      String agentPhone,
                                      LocalDate date,
                                      int startHour) {
}
