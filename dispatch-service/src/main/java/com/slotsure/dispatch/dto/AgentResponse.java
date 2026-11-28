package com.slotsure.dispatch.dto;

/**
 * One agent with their counts for a date (CONTRACT V2.8). assigned counts every order given to the
 * agent that day, delivered the ones already handed over. active goes beyond the contract.
 */
public record AgentResponse(String agentId,
                            String name,
                            String phone,
                            String homeArea,
                            long assigned,
                            long delivered,
                            boolean active) {
}
