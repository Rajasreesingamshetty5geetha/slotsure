package com.slotsure.slot.service;

import com.slotsure.slot.config.SlotsureProperties;
import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Names the area a cell belongs to: the configured locality nearest to the centre of the cell's
 * resolution-8 parent. Every resolution-9 cell under one parent therefore gets the same name.
 */
@Service
@RequiredArgsConstructor
public class AreaResolver {

    static final int AREA_RESOLUTION = 8;
    public static final String UNKNOWN = "Unknown";
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final H3Core h3Core;
    private final SlotsureProperties properties;

    public String resolve(String h3Cell) {
        if (h3Cell == null || !h3Core.isValidCell(h3Cell)) {
            throw new IllegalArgumentException("Not a valid H3 cell: " + h3Cell);
        }
        String areaCell = h3Core.getResolution(h3Cell) > AREA_RESOLUTION
                ? h3Core.cellToParentAddress(h3Cell, AREA_RESOLUTION)
                : h3Cell;
        LatLng centre = h3Core.cellToLatLng(areaCell);
        return nearest(centre.lat, centre.lng);
    }

    /** The nearest configured locality, or "Unknown" when none are configured. */
    public String nearest(double lat, double lng) {
        String best = UNKNOWN;
        double bestKm = Double.MAX_VALUE;
        for (SlotsureProperties.Area area : properties.areas()) {
            double km = distanceKm(lat, lng, area.lat(), area.lng());
            if (km < bestKm) {
                bestKm = km;
                best = area.name();
            }
        }
        return best;
    }

    /** Great-circle distance (haversine). */
    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
