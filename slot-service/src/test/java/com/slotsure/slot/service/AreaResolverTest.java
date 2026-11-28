package com.slotsure.slot.service;

import com.slotsure.slot.TestProperties;
import com.slotsure.slot.config.SlotsureProperties;
import com.uber.h3core.H3Core;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AreaResolverTest {

    static List<SlotsureProperties.Area> localities() {
        return TestProperties.BENGALURU;
    }

    private static H3Core h3Core;
    private static AreaResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        h3Core = H3Core.newInstance();
        resolver = new AreaResolver(h3Core, TestProperties.defaults());
    }

    @ParameterizedTest
    @MethodSource("localities")
    void eachLocalityCentroidCellIsNamedAfterThatLocality(SlotsureProperties.Area area) {
        String cell = h3Core.latLngToCellAddress(area.lat(), area.lng(), 9);

        assertThat(resolver.resolve(cell)).isEqualTo(area.name());
    }

    @Test
    void pointsNearALocalityTakeItsName() {
        // About 800 m south-east of the Koramangala centroid, and 1 km west of the Whitefield one.
        assertThat(resolver.resolve(h3Core.latLngToCellAddress(12.9300, 77.6300, 9))).isEqualTo("Koramangala");
        assertThat(resolver.resolve(h3Core.latLngToCellAddress(12.9700, 77.7410, 9))).isEqualTo("Whitefield");
    }

    @Test
    void picksTheNearestWhenBetweenTwoLocalities() {
        // Jayanagar (12.9250, 77.5938) and BTM Layout (12.9166, 77.6101) are about 2 km apart.
        assertThat(resolver.nearest(12.9240, 77.5960)).isEqualTo("Jayanagar");
        assertThat(resolver.nearest(12.9175, 77.6080)).isEqualTo("BTM Layout");
    }

    @Test
    void allCellsUnderOneResolutionEightParentShareOneName() {
        String parent = h3Core.latLngToCellAddress(12.9600, 77.6700, 8);
        List<String> children = h3Core.cellToChildren(parent, 9);
        String expected = resolver.resolve(parent);

        assertThat(children).hasSize(7);
        assertThat(children).allSatisfy(child -> assertThat(resolver.resolve(child)).isEqualTo(expected));
    }

    @Test
    void namesUnknownWhenNoLocalitiesAreConfigured() {
        AreaResolver empty = new AreaResolver(h3Core, TestProperties.withAreas(List.of()));

        assertThat(empty.resolve(h3Core.latLngToCellAddress(12.9352, 77.6245, 9))).isEqualTo(AreaResolver.UNKNOWN);
    }

    @Test
    void rejectsAnInvalidCell() {
        assertThatThrownBy(() -> resolver.resolve("not-a-cell")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void haversineDistanceIsRoughlyRight() {
        // Koramangala to Whitefield is about 14 km in a straight line.
        assertThat(AreaResolver.distanceKm(12.9352, 77.6245, 12.9698, 77.7500)).isBetween(13.5, 14.6);
    }
}
