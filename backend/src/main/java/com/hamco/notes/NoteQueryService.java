package com.hamco.notes;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only application logic between HTTP and persistence.
 *
 * <p>The transaction keeps the database interaction inside a well-defined
 * unit of work. We map entities to response records before leaving this layer
 * rather than relying on a database session during JSON serialization.
 */
@Service
@Transactional(readOnly = true)
public class NoteQueryService {

    private final NoteRepository notes;

    public NoteQueryService(NoteRepository notes) {
        // Constructor injection makes dependencies explicit and testable.
        this.notes = notes;
    }

    public List<NoteResponse> listPublished() {
        return notes.findByDeletedAtIsNull().stream()
                .map(NoteResponse::from)
                .toList();
    }

    public Optional<NoteResponse> findPublished(Integer id) {
        return notes.findByIdAndDeletedAtIsNull(id).map(NoteResponse::from);
    }
}
