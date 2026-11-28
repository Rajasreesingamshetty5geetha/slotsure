package com.slotsure.order.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    @Test
    void windowsReadLikePeopleSayThem() {
        assertThat(NotificationService.window(18, 19)).isEqualTo("6–7 PM");
        assertThat(NotificationService.window(9, 10)).isEqualTo("9–10 AM");
        assertThat(NotificationService.window(11, 12)).isEqualTo("11 AM–12 PM");
        assertThat(NotificationService.window(12, 13)).isEqualTo("12–1 PM");
        assertThat(NotificationService.window(21, null)).isEqualTo("9–10 PM");
    }

    @Test
    void rupeesUseIndianGrouping() {
        assertThat(NotificationService.rupees(new BigDecimal("2499.00"))).isEqualTo("₹2,499");
        assertThat(NotificationService.rupees(new BigDecimal("123450.50"))).isEqualTo("₹1,23,450.50");
        assertThat(NotificationService.rupees(new BigDecimal("899"))).isEqualTo("₹899");
    }
}
