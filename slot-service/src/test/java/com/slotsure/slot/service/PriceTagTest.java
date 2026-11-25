package com.slotsure.slot.service;

import com.slotsure.slot.dto.PriceTag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTagTest {

    @ParameterizedTest
    @CsvSource({
            "10, 10, GREEN",
            "7, 10, GREEN",
            "6, 10, NORMAL",   // exactly 60% is not above 60%
            "2, 10, NORMAL",   // exactly 20% is not below 20%
            "1, 10, BUSY",
            "0, 10, BUSY"
    })
    void picksTagFromRemainingShare(long remaining, int capacity, PriceTag expected) {
        assertThat(PriceTag.of(remaining, capacity)).isEqualTo(expected);
    }
}
