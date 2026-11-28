package com.slotsure.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Marks a Kafka eventId as handled (CONTRACT 5.4). isNew() is always true so save() INSERTs:
 * a concurrent duplicate fails on the primary key and rolls the assignment back.
 */
@Entity
@Table(name = "processed_events")
@Getter
public class ProcessedEvent implements Persistable<String> {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
