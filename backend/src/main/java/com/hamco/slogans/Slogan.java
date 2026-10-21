package com.hamco.slogans;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model for the Flyway-owned slogans table, not a response DTO. */
@Entity
@Table(name = "slogans")
public class Slogan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by_user_id", length = 36)
    private String createdByUserId;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Slogan() {
        // JPA materializes rows with this constructor; callers use the service.
    }

    Slogan(String text, boolean active, String createdByUserId, Instant createdAt) {
        this.text = text;
        this.active = active;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    void update(String text, Boolean active, Instant now) {
        // Null means “not supplied” for PUT's partial-update semantics.
        if (text != null) this.text = text;
        if (active != null) this.active = active;
        updatedAt = now;
    }

    Integer id() { return id; }
    String text() { return text; }
    boolean active() { return active; }
    Instant createdAt() { return createdAt; }
    String createdByUserId() { return createdByUserId; }
    Instant updatedAt() { return updatedAt; }
}
