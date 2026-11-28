package com.slotsure.dispatch.service;

import com.slotsure.dispatch.config.DispatchProperties;
import com.slotsure.dispatch.entity.Agent;
import com.slotsure.dispatch.entity.Assignment;
import com.slotsure.dispatch.entity.AssignmentStatus;
import com.slotsure.dispatch.entity.ProcessedEvent;
import com.slotsure.dispatch.repository.AgentRepository;
import com.slotsure.dispatch.repository.AssignmentRepository;
import com.slotsure.dispatch.repository.ProcessedEventRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mockito repositories backed by maps, so the services run for real without MySQL. */
public class InMemoryStore {

    public static final DispatchProperties PROPERTIES = new DispatchProperties(ZoneId.of("Asia/Kolkata"),
            new DispatchProperties.Dispatch(6),
            new DispatchProperties.Topics("slot.confirmed", "delivery.assigned", "delivery.completed"),
            new DispatchProperties.DemoData(true, 40, 80));

    public final Map<String, Agent> agents = new LinkedHashMap<>();
    public final Map<String, Assignment> assignments = new LinkedHashMap<>();
    public final Map<String, ProcessedEvent> processedEvents = new LinkedHashMap<>();

    public final AgentRepository agentRepository = mock(AgentRepository.class);
    public final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    public final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);

    @SuppressWarnings("unchecked")
    public InMemoryStore() {
        when(agentRepository.findByActiveTrue()).thenAnswer(inv -> agents.values().stream().filter(Agent::isActive).toList());
        when(agentRepository.findAllByOrderByHomeAreaAscNameAsc()).thenAnswer(inv -> agents.values().stream()
                .sorted(Comparator.comparing(Agent::getHomeArea).thenComparing(Agent::getName)).toList());
        when(agentRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(agents.get(inv.<String>getArgument(0))));
        when(agentRepository.existsById(anyString())).thenAnswer(inv -> agents.containsKey(inv.<String>getArgument(0)));
        when(agentRepository.count()).thenAnswer(inv -> (long) agents.size());
        when(agentRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            List<Agent> saved = new ArrayList<>();
            ((Iterable<Agent>) inv.getArgument(0)).forEach(agent -> {
                agents.put(agent.getAgentId(), agent);
                saved.add(agent);
            });
            return saved;
        });

        when(assignmentRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(assignments.get(inv.<String>getArgument(0))));
        when(assignmentRepository.findByOrderIdForUpdate(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(assignments.get(inv.<String>getArgument(0))));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(inv -> {
            Assignment assignment = inv.getArgument(0);
            assignments.put(assignment.getOrderId(), assignment);
            return assignment;
        });
        when(assignmentRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            List<Assignment> saved = new ArrayList<>();
            ((Iterable<Assignment>) inv.getArgument(0)).forEach(assignment -> {
                assignments.put(assignment.getOrderId(), assignment);
                saved.add(assignment);
            });
            return saved;
        });
        when(assignmentRepository.existsByDateAndDemoTrue(any(LocalDate.class))).thenAnswer(inv -> assignments.values().stream()
                .anyMatch(a -> a.isDemo() && a.getDate().equals(inv.getArgument(0))));
        when(assignmentRepository.loadsOn(any(LocalDate.class), anyInt())).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            int hour = inv.getArgument(1);
            return onDate(date).collect(Collectors.groupingBy(Assignment::getAgentId)).entrySet().stream()
                    .map(entry -> load(entry.getKey(),
                            entry.getValue().stream().filter(a -> a.getStartHour() == hour).count(),
                            entry.getValue().size()))
                    .toList();
        });
        when(assignmentRepository.countsByAgentAndStatus(any(LocalDate.class))).thenAnswer(inv -> onDate(inv.getArgument(0))
                .collect(Collectors.groupingBy(a -> a.getAgentId() + "|" + a.getStatus(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> statusCount(entry.getKey().split("\\|")[0],
                        AssignmentStatus.valueOf(entry.getKey().split("\\|")[1]), entry.getValue()))
                .toList());
        when(assignmentRepository.countsByArea(any(LocalDate.class))).thenAnswer(inv -> onDate(inv.getArgument(0))
                .collect(Collectors.groupingBy(Assignment::getAreaName)).entrySet().stream()
                .map(entry -> areaCount(entry.getKey(),
                        entry.getValue().stream().map(Assignment::getAgentId).distinct().count(), entry.getValue().size()))
                .toList());

        when(processedEventRepository.existsById(anyString())).thenAnswer(inv -> processedEvents.containsKey(inv.<String>getArgument(0)));
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(inv -> {
            ProcessedEvent event = inv.getArgument(0);
            processedEvents.put(event.getEventId(), event);
            return event;
        });
    }

    public Agent addAgent(String id, String name, String homeArea) {
        Agent agent = new Agent(id, name, "+91 98450 00000", homeArea);
        agents.put(id, agent);
        return agent;
    }

    private java.util.stream.Stream<Assignment> onDate(LocalDate date) {
        return assignments.values().stream().filter(a -> a.getDate().equals(date));
    }

    private static AssignmentRepository.AgentLoad load(String agentId, long hourLoad, long dayLoad) {
        return new AssignmentRepository.AgentLoad() {
            public String getAgentId() {
                return agentId;
            }

            public long getHourLoad() {
                return hourLoad;
            }

            public long getDayLoad() {
                return dayLoad;
            }
        };
    }

    private static AssignmentRepository.AgentStatusCount statusCount(String agentId, AssignmentStatus status, long count) {
        return new AssignmentRepository.AgentStatusCount() {
            public String getAgentId() {
                return agentId;
            }

            public AssignmentStatus getStatus() {
                return status;
            }

            public long getAssignments() {
                return count;
            }
        };
    }

    private static AssignmentRepository.AreaCount areaCount(String areaName, long agents, long deliveries) {
        return new AssignmentRepository.AreaCount() {
            public String getAreaName() {
                return areaName;
            }

            public long getAgents() {
                return agents;
            }

            public long getDeliveries() {
                return deliveries;
            }
        };
    }
}
