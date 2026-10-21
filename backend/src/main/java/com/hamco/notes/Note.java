package com.hamco.notes;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Database model for a blog note, mapped to the table created by Flyway.
 *
 * <p>JPA entities model persistence, not public JSON. The API uses a separate
 * {@link NoteResponse} so future internal columns cannot accidentally leak
 * into a response. Field annotations are explicit: the source database uses
 * snake_case, while Java properties normally use camelCase.
 */
@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Note() {
        // Hibernate needs a no-argument constructor to materialize SQL rows.
        // Application callers should use services, not instantiate DB records.
    }

    public Integer getId() { return id; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getContent() { return content; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
