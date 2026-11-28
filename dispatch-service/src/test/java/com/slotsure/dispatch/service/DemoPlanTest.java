package com.slotsure.dispatch.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPlanTest {

    /** Proves this copy of DemoPlan produces exactly what order-service's copy produces. */
    @Test
    void fingerprintMatchesOrderService() {
        assertThat(DemoPlanFingerprint.of(DemoPlan.forDate(LocalDate.of(2026, 11, 28), 40, 80)))
                .isEqualTo(DemoPlanFingerprint.EXPECTED_2026_11_28);
    }
}
