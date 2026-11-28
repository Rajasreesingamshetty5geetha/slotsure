package com.slotsure.dispatch.repository;

import com.slotsure.dispatch.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository extends JpaRepository<Agent, String> {

    List<Agent> findByActiveTrue();

    List<Agent> findAllByOrderByHomeAreaAscNameAsc();
}
