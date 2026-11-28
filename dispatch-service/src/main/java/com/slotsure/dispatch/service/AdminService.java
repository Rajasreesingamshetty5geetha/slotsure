package com.slotsure.dispatch.service;

import com.slotsure.dispatch.config.DispatchProperties;
import com.slotsure.dispatch.dto.AdminDispatchResponse;
import com.slotsure.dispatch.dto.AgentResponse;
import com.slotsure.dispatch.entity.Agent;
import com.slotsure.dispatch.entity.AssignmentStatus;
import com.slotsure.dispatch.repository.AgentRepository;
import com.slotsure.dispatch.repository.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Agent and dispatch numbers for one date, from GROUP BY queries. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AgentRepository agentRepository;
    private final AssignmentRepository assignmentRepository;
    private final DispatchProperties properties;

    public LocalDate today() {
        return LocalDate.now(properties.zone());
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> agents(LocalDate date) {
        Map<String, long[]> counts = countsByAgent(date);
        return agentRepository.findAllByOrderByHomeAreaAscNameAsc().stream()
                .map(agent -> toResponse(agent, counts.getOrDefault(agent.getAgentId(), new long[2])))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDispatchResponse dispatch(LocalDate date) {
        Map<String, long[]> counts = countsByAgent(date);
        long assigned = counts.values().stream().mapToLong(c -> c[0]).sum();
        long delivered = counts.values().stream().mapToLong(c -> c[1]).sum();
        List<AdminDispatchResponse.AreaLoad> byArea = assignmentRepository.countsByArea(date).stream()
                .map(row -> new AdminDispatchResponse.AreaLoad(row.getAreaName(), row.getAgents(), row.getDeliveries()))
                .toList();
        return new AdminDispatchResponse(date, agentRepository.count(), counts.size(), assigned, delivered, byArea);
    }

    /** agentId to {assigned, delivered}; only agents with assignments on the date appear. */
    private Map<String, long[]> countsByAgent(LocalDate date) {
        Map<String, long[]> counts = new HashMap<>();
        for (AssignmentRepository.AgentStatusCount row : assignmentRepository.countsByAgentAndStatus(date)) {
            long[] agentCounts = counts.computeIfAbsent(row.getAgentId(), id -> new long[2]);
            agentCounts[0] += row.getAssignments();
            if (row.getStatus() == AssignmentStatus.DELIVERED) {
                agentCounts[1] += row.getAssignments();
            }
        }
        return counts;
    }

    private static AgentResponse toResponse(Agent agent, long[] counts) {
        return new AgentResponse(agent.getAgentId(), agent.getName(), agent.getPhone(), agent.getHomeArea(),
                counts[0], counts[1], agent.isActive());
    }
}
