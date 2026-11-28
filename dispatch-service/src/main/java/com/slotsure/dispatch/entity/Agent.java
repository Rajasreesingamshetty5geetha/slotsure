package com.slotsure.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A delivery agent based in one area. */
@Entity
@Table(name = "agents")
@Getter
public class Agent {

    @Id
    @Column(name = "agent_id", length = 36)
    private String agentId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(name = "home_area", nullable = false, length = 64)
    private String homeArea;

    @Setter
    @Column(nullable = false)
    private boolean active;

    /** True for seeded dummy rows, so they can be wiped. */
    @Setter
    @Column(nullable = false)
    private boolean demo;

    protected Agent() {
    }

    public Agent(String agentId, String name, String phone, String homeArea) {
        this.agentId = agentId;
        this.name = name;
        this.phone = phone;
        this.homeArea = homeArea;
        this.active = true;
    }
}
