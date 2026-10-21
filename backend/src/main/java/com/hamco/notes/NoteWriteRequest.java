package com.hamco.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A client may set only the two editable fields, never the database ID,
 * timestamps, slug or author. Spring's {@code @Valid} executes these Bean
 * Validation constraints before entering the controller method.
 */
public record NoteWriteRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content) {
}
