package com.hamco.notes;

import java.time.Instant;

/**
 * The seven public JSON fields observed at the running ASP.NET endpoint.
 *
 * <p>A Java record is an immutable value object. Jackson serializes its
 * component names as camelCase JSON keys (for example {@code createdAt}),
 * matching the actual reference response rather than exposing an entity.
 */
public record NoteResponse(
        Integer id,
        String title,
        String slug,
        String content,
        String userId,
        Instant createdAt,
        Instant updatedAt) {

    static NoteResponse from(Note note) {
        return new NoteResponse(note.getId(), note.getTitle(), note.getSlug(),
                note.getContent(), note.getUserId(), note.getCreatedAt(), note.getUpdatedAt());
    }
}
