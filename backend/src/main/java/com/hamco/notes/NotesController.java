package com.hamco.notes;

import java.util.List;
import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for public note reads.
 *
 * <p>{@code @RestController} returns JSON bodies instead of rendering a view.
 * The annotations preserve the ASP.NET route paths exactly; Spring converts
 * the integer path segment and reports a missing note as HTTP 404. The
 * security filter chain guards each write route before this class is called.
 */
@RestController
@RequestMapping("/api/notes")
public class NotesController {

    private final NoteQueryService queries;
    private final NoteWriteService writes;

    public NotesController(NoteQueryService queries, NoteWriteService writes) {
        this.queries = queries;
        this.writes = writes;
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

    @PostMapping
    public ResponseEntity<NoteResponse> create(@Valid @RequestBody NoteWriteRequest request,
                                                Authentication authentication) {
        // The creator comes from a verified principal, never client JSON. An
        // API key represents automation, not a user row, so its notes have no
        // author foreign key (the source database permits NULL here).
        String userId = "api_key".equals(authentication.getDetails()) ? null : authentication.getName();
        NoteResponse created = writes.create(request, userId);
        return ResponseEntity.created(URI.create("/api/notes/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> update(@PathVariable Integer id,
                                                @Valid @RequestBody NoteWriteRequest request) {
        return writes.update(id, request).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return writes.delete(id) ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
