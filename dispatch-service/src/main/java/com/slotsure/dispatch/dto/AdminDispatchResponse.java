package com.slotsure.dispatch.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/admin/dispatch (CONTRACT V2.8). agentsActive counts the agents with at least one
 * assignment on the date; byArea counts, per area, those agents and their deliveries.
 */
public record AdminDispatchResponse(LocalDate date,
                                    long agentsTotal,
                                    long agentsActive,
                                    long assigned,
                                    long delivered,
                                    List<AreaLoad> byArea) {

    public record AreaLoad(String areaName, long agents, long deliveries) {
    }
}
