package com.slotsure.dispatch.service;

import com.slotsure.dispatch.config.DispatchProperties;
import com.slotsure.dispatch.entity.Agent;
import com.slotsure.dispatch.entity.Assignment;
import com.slotsure.dispatch.repository.AgentRepository;
import com.slotsure.dispatch.repository.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the 12 agents, and (when demo data is on) the assignments behind order-service's demo orders
 * for yesterday and today (CONTRACT V2.10): delivered once the slot has ended, assigned otherwise.
 * A date is seeded once: when it already has demo assignments it is left alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {

    private final AgentRepository agentRepository;
    private final AssignmentRepository assignmentRepository;
    private final TransactionTemplate transactionTemplate;
    private final DispatchProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        try {
            seedAgents();
        } catch (RuntimeException e) {
            log.error("Could not seed agents", e);
        }
        if (!properties.demoData().enabled()) {
            return;
        }
        LocalDate today = LocalDate.now(properties.zone());
        for (LocalDate date : List.of(today.minusDays(1), today)) {
            try {
                seed(date, Instant.now());
            } catch (RuntimeException e) {
                log.error("Could not seed demo assignments for {}", date, e);
            }
        }
    }

    /** Adds any of the 12 roster agents that are missing; returns how many were added. */
    public int seedAgents() {
        List<Agent> missing = new ArrayList<>();
        for (DemoPlan.Agent planned : DemoPlan.AGENTS) {
            if (!agentRepository.existsById(planned.agentId())) {
                Agent agent = new Agent(planned.agentId(), planned.name(), planned.phone(), planned.homeArea());
                agent.setDemo(true);
                missing.add(agent);
            }
        }
        if (!missing.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> agentRepository.saveAll(missing));
            log.info("Seeded {} agents", missing.size());
        }
        return missing.size();
    }

    /** Returns how many assignments were seeded; 0 when the date already has demo assignments. */
    public int seed(LocalDate date, Instant now) {
        if (assignmentRepository.existsByDateAndDemoTrue(date)) {
            log.info("Demo assignments for {} already exist, not seeding", date);
            return 0;
        }
        DispatchProperties.DemoData demo = properties.demoData();
        List<Assignment> assignments = new ArrayList<>();
        for (DemoPlan.Booking booking : DemoPlan.forDate(date, demo.minBookingsPerDay(), demo.maxBookingsPerDay())) {
            if (booking.failed()) {
                continue;
            }
            Instant slotStart = date.atTime(booking.startHour(), 0).atZone(properties.zone()).toInstant();
            Instant bookedAt = slotStart.minus(Duration.ofMinutes(booking.bookedMinutesBefore()));
            Assignment assignment = new Assignment(booking.orderId(), booking.agent().agentId(), booking.areaName(),
                    date, booking.startHour(), bookedAt.plusSeconds(5));
            if (!now.isBefore(slotStart.plus(Duration.ofHours(1)))) {
                assignment.markDelivered(slotStart.plus(Duration.ofMinutes(booking.deliveredMinute())));
            }
            assignment.setDemo(true);
            assignments.add(assignment);
        }
        transactionTemplate.executeWithoutResult(status -> assignmentRepository.saveAll(assignments));
        log.info("Seeded {} demo assignments for {}", assignments.size(), date);
        return assignments.size();
    }
}
