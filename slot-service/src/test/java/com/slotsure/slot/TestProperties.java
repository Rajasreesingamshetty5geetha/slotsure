package com.slotsure.slot;

import com.slotsure.slot.config.SlotsureProperties;

import java.time.ZoneId;
import java.util.List;

/** The same values as application.yml, for unit tests that build services by hand. */
public final class TestProperties {

    public static final List<SlotsureProperties.Area> BENGALURU = List.of(
            new SlotsureProperties.Area("Koramangala", 12.9352, 77.6245),
            new SlotsureProperties.Area("HSR Layout", 12.9116, 77.6474),
            new SlotsureProperties.Area("Indiranagar", 12.9784, 77.6408),
            new SlotsureProperties.Area("BTM Layout", 12.9166, 77.6101),
            new SlotsureProperties.Area("Jayanagar", 12.9250, 77.5938),
            new SlotsureProperties.Area("Whitefield", 12.9698, 77.7500),
            new SlotsureProperties.Area("Marathahalli", 12.9591, 77.6974),
            new SlotsureProperties.Area("Electronic City", 12.8452, 77.6602),
            new SlotsureProperties.Area("Malleshwaram", 13.0035, 77.5647),
            new SlotsureProperties.Area("Hebbal", 13.0358, 77.5970));

    private TestProperties() {
    }

    public static SlotsureProperties defaults() {
        return withAreas(BENGALURU);
    }

    public static SlotsureProperties withAreas(List<SlotsureProperties.Area> areas) {
        return new SlotsureProperties(
                new SlotsureProperties.SlotSettings(10, 8, 22, ZoneId.of("Asia/Kolkata")),
                new SlotsureProperties.HoldSettings(300, 24, 30000),
                new SlotsureProperties.Topics("slot.confirm", "slot.release", "slot.confirmed",
                        "slot.confirm.failed", "slot.released"),
                areas,
                new SlotsureProperties.DemoData(true, 40, 80));
    }
}
