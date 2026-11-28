package com.slotsure.dispatch.service;

import com.slotsure.dispatch.config.DispatchProperties;
import com.slotsure.dispatch.dto.CompletionResponse;
import com.slotsure.dispatch.entity.Agent;
import com.slotsure.dispatch.entity.Assignment;
import com.slotsure.dispatch.entity.ProcessedEvent;
import com.slotsure.dispatch.event.DeliveryAssignedPayload;
import com.slotsure.dispatch.event.DeliveryCompletedPayload;
import com.slotsure.dispatch.event.SlotConfirmedPayload;
import com.slotsure.dispatch.exception.ApiException;
import com.slotsure.dispatch.repository.AgentRepository;
import com.slotsure.dispatch.repository.AssignmentRepository;
import com.slotsure.dispatch.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gives each confirmed order to an agent and records the hand-over.
 * Assignment picks the least-loaded active agent based in the order's area who still has room in that
 * hour (max deliveries per agent per hour, configurable); otherwise the least-loaded agent with room
 * anywhere; and if every agent is full, the least-loaded agent overall.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AgentRepository agentRepository;
    private final AssignmentRepository assignmentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EventPublisher eventPublisher;
    private final DispatchProperties properties;

    @Transactional
    public void assign(String eventId, SlotConfirmedPayload payload) {
        String orderId = payload.orderId();
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate slot.confirmed eventId={} orderId={}", eventId, orderId);
            return;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));

        Optional<Assignment> existing = assignmentRepository.findById(orderId);
        if (existing.isPresent()) {
            Agent agent = agentRepository.findById(existing.get().getAgentId()).orElseThrow();
            log.info("Order already assigned to {}, re-sending delivery.assigned orderId={}", agent.getName(), orderId);
            publishAssigned(existing.get(), agent);
            return;
        }

        if (payload.date() == null || payload.startHour() == null) {
            // Messages from before CONTRACT V2 carry no slot time, so there is no slot to deliver in.
            log.warn("slot.confirmed eventId={} orderId={} has no date or startHour, not assigning", eventId, orderId);
            return;
        }
        LocalDate date = payload.date();
        int hour = payload.startHour();
        Agent agent = chooseAgent(payload.areaName(), date, hour)
                .orElseThrow(() -> new IllegalStateException("No active agents to assign orderId=" + orderId));

        Assignment assignment = assignmentRepository.save(new Assignment(orderId, agent.getAgentId(), payload.areaName(),
                date, hour, Instant.now().truncatedTo(ChronoUnit.MILLIS)));
        log.info("Assigned orderId={} to agent {} ({}) for {} {}:00 in {}", orderId, agent.getName(),
                agent.getHomeArea(), date, hour, payload.areaName());
        publishAssigned(assignment, agent);
    }

    /**
     * Marks the order delivered and tells order-service. Calling it again returns the same deliveredAt
     * and sends delivery.completed again, which order-service ignores for an order it already delivered.
     */
    @Transactional
    public CompletionResponse complete(String orderId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Idempotency-Key header is required");
        }
        Assignment assignment = assignmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notAssigned(orderId));
        if (assignment.isDelivered()) {
            log.info("Order already delivered at {}, returning the same result orderId={}", assignment.getDeliveredAt(), orderId);
        } else {
            assignment.markDelivered(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            log.info("Delivered orderId={} by agent {} at {}", orderId, assignment.getAgentId(), assignment.getDeliveredAt());
        }
        eventPublisher.publish(properties.topics().deliveryCompleted(), orderId,
                new DeliveryCompletedPayload(orderId, assignment.getAgentId(), assignment.getDeliveredAt()));
        return new CompletionResponse(orderId, assignment.getDeliveredAt());
    }

    Optional<Agent> chooseAgent(String areaName, LocalDate date, int hour) {
        List<Agent> active = agentRepository.findByActiveTrue();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        Map<String, AssignmentRepository.AgentLoad> loads = assignmentRepository.loadsOn(date, hour).stream()
                .collect(Collectors.toMap(AssignmentRepository.AgentLoad::getAgentId, Function.identity()));
        int cap = properties.dispatch().maxDeliveriesPerAgentPerHour();
        Comparator<Agent> leastLoaded = Comparator
                .comparingLong((Agent agent) -> hourLoad(loads, agent))
                .thenComparingLong(agent -> dayLoad(loads, agent))
                .thenComparing(Agent::getName);

        Optional<Agent> home = active.stream()
                .filter(agent -> agent.getHomeArea().equals(areaName) && hourLoad(loads, agent) < cap)
                .min(leastLoaded);
        if (home.isPresent()) {
            return home;
        }
        Optional<Agent> anyWithRoom = active.stream().filter(agent -> hourLoad(loads, agent) < cap).min(leastLoaded);
        if (anyWithRoom.isPresent()) {
            log.info("No agent from {} has room at {}:00 on {}, using {} from {}", areaName, hour, date,
                    anyWithRoom.get().getName(), anyWithRoom.get().getHomeArea());
            return anyWithRoom;
        }
        Agent fallback = active.stream().min(leastLoaded).orElseThrow();
        log.warn("Every agent has {} deliveries at {}:00 on {}, overbooking {}", cap, hour, date, fallback.getName());
        return Optional.of(fallback);
    }

    private void publishAssigned(Assignment assignment, Agent agent) {
        eventPublisher.publish(properties.topics().deliveryAssigned(), assignment.getOrderId(),
                new DeliveryAssignedPayload(assignment.getOrderId(), agent.getAgentId(), agent.getName(), agent.getPhone(),
                        assignment.getDate(), assignment.getStartHour()));
    }

    private static long hourLoad(Map<String, AssignmentRepository.AgentLoad> loads, Agent agent) {
        AssignmentRepository.AgentLoad load = loads.get(agent.getAgentId());
        return load == null ? 0 : load.getHourLoad();
    }

    private static long dayLoad(Map<String, AssignmentRepository.AgentLoad> loads, Agent agent) {
        AssignmentRepository.AgentLoad load = loads.get(agent.getAgentId());
        return load == null ? 0 : load.getDayLoad();
    }
}
