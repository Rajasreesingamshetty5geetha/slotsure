package com.slotsure.dispatch.repository;

import com.slotsure.dispatch.entity.Assignment;
import com.slotsure.dispatch.entity.AssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, String> {

    /** Row lock, so two concurrent "complete" calls for one order cannot both deliver it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.orderId = :orderId")
    Optional<Assignment> findByOrderIdForUpdate(@Param("orderId") String orderId);

    boolean existsByDateAndDemoTrue(LocalDate date);

    /** Deliveries per agent on a date, split into the given hour and the whole day. */
    @Query("""
            select a.agentId as agentId,
                   sum(case when a.startHour = :hour then 1 else 0 end) as hourLoad,
                   count(a) as dayLoad
            from Assignment a
            where a.date = :date
            group by a.agentId
            """)
    List<AgentLoad> loadsOn(@Param("date") LocalDate date, @Param("hour") int hour);

    @Query("""
            select a.agentId as agentId, a.status as status, count(a) as assignments
            from Assignment a
            where a.date = :date
            group by a.agentId, a.status
            """)
    List<AgentStatusCount> countsByAgentAndStatus(@Param("date") LocalDate date);

    @Query("""
            select a.areaName as areaName, count(distinct a.agentId) as agents, count(a) as deliveries
            from Assignment a
            where a.date = :date and a.areaName is not null
            group by a.areaName
            order by count(a) desc, a.areaName
            """)
    List<AreaCount> countsByArea(@Param("date") LocalDate date);

    interface AgentLoad {
        String getAgentId();

        long getHourLoad();

        long getDayLoad();
    }

    interface AgentStatusCount {
        String getAgentId();

        AssignmentStatus getStatus();

        long getAssignments();
    }

    interface AreaCount {
        String getAreaName();

        long getAgents();

        long getDeliveries();
    }
}
