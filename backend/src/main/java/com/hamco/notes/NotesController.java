package com.hamco.notes;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for public note reads.
 *
 * <p>{@code @RestController} returns JSON bodies instead of rendering a view.
 * The annotations preserve the ASP.NET route paths exactly; Spring converts
 * the integer path segment and reports a missing note as HTTP 404. Write
 * endpoints will be added only after their own failing authorization tests.
 */
@RestController
@RequestMapping("/api/notes")
public class NotesController {

    private final NoteQueryService queries;

    public NotesController(NoteQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<NoteResponse> list() {
        // Returning a List lets Spring/Jackson produce [] when there are no rows.
        return queries.listPublished();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> detail(@PathVariable Integer id) {
        return queries.findPublished(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
