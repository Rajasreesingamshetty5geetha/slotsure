package com.slotsure.dispatch.service;

import com.slotsure.dispatch.dto.AdminDispatchResponse;
import com.slotsure.dispatch.dto.AgentResponse;
import com.slotsure.dispatch.entity.Assignment;
import com.slotsure.dispatch.entity.AssignmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoDataSeederTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private final InMemoryStore store = new InMemoryStore();
    private final DemoDataSeeder seeder = new DemoDataSeeder(store.agentRepository, store.assignmentRepository,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), InMemoryStore.PROPERTIES);
    private final AdminService adminService = new AdminService(store.agentRepository, store.assignmentRepository,
            InMemoryStore.PROPERTIES);

    @Test
    void seedsTwelveAgentsAcrossTheTenAreasOnce() {
        assertThat(seeder.seedAgents()).isEqualTo(12);
        assertThat(seeder.seedAgents()).isZero();

        assertThat(store.agents).hasSize(12);
        assertThat(store.agents.values().stream().map(a -> a.getHomeArea()).distinct()).hasSize(10);
        assertThat(store.agents.values()).allSatisfy(agent -> {
            assertThat(agent.isActive()).isTrue();
            assertThat(agent.getPhone()).startsWith("+91 ");
        });
    }

    @Test
    void aPastDayIsAllDeliveredForTheSharedDemoOrders() {
        seeder.seedAgents();

        int seeded = seeder.seed(DATE, Instant.parse("2026-11-29T03:00:00Z"));

        List<DemoPlan.Booking> plan = DemoPlan.forDate(DATE, 40, 80);
        assertThat(seeded).isEqualTo(plan.stream().filter(b -> !b.failed()).count());
        assertThat(store.assignments.values()).allSatisfy(assignment -> {
            assertThat(assignment.isDemo()).isTrue();
            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);
            assertThat(store.agents).containsKey(assignment.getAgentId());
        });
        assertThat(store.assignments.keySet()).isSubsetOf(plan.stream().map(DemoPlan.Booking::orderId).toList());

        AdminDispatchResponse dispatch = adminService.dispatch(DATE);
        assertThat(dispatch.agentsTotal()).isEqualTo(12);
        assertThat(dispatch.assigned()).isEqualTo(seeded);
        assertThat(dispatch.delivered()).isEqualTo(seeded);
        assertThat(dispatch.byArea()).isNotEmpty();
        assertThat(dispatch.byArea().stream().mapToLong(AdminDispatchResponse.AreaLoad::deliveries).sum()).isEqualTo(seeded);
        List<AgentResponse> agents = adminService.agents(DATE);
        assertThat(agents).hasSize(12);
        assertThat(agents.stream().mapToLong(AgentResponse::assigned).sum()).isEqualTo(seeded);
    }

    @Test
    void laterSlotsTodayAreStillAssigned() {
        seeder.seedAgents();

        // 14:00 in Bengaluru.
        seeder.seed(DATE, Instant.parse("2026-11-28T08:30:00Z"));

        assertThat(store.assignments.values()).filteredOn(a -> a.getStartHour() >= 14)
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED));
        assertThat(store.assignments.values()).filteredOn(a -> a.getStartHour() < 13)
                .allSatisfy(a -> assertThat(a.getDeliveredAt()).isNotNull());
    }

    @Test
    void aSeededDateIsLeftAlone() {
        seeder.seedAgents();
        seeder.seed(DATE, Instant.parse("2026-11-29T03:00:00Z"));
        int count = store.assignments.size();

        assertThat(seeder.seed(DATE, Instant.parse("2026-11-29T03:00:00Z"))).isZero();
        assertThat(store.assignments).hasSize(count);
        assertThat(store.assignments.values()).extracting(Assignment::getDate).containsOnly(DATE);
    }
}
