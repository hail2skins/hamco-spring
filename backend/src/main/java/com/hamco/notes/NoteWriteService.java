package com.hamco.notes;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Note mutations live in a transaction so a failed database operation rolls
 * back the entire request. The HTTP controller deals only with status codes;
 * this layer owns the content rules and the JPA repository owns persistence.
 */
@Service
@Transactional
public class NoteWriteService {
    private final NoteRepository notes;
    private final Clock clock;

    public NoteWriteService(NoteRepository notes) {
        this.notes = notes;
        this.clock = Clock.systemUTC();
    }

    public NoteResponse create(NoteWriteRequest request, String userId) {
        Instant now = clock.instant();
        Note note = new Note(request.title(), Slugifier.fromTitle(request.title()),
                request.content(), userId, now);
        return NoteResponse.from(notes.save(note));
    }

    public Optional<NoteResponse> update(Integer id, NoteWriteRequest request) {
        return notes.findByIdAndDeletedAtIsNull(id).map(note -> {
            note.revise(request.title(), Slugifier.fromTitle(request.title()),
                    request.content(), clock.instant());
            // No explicit save needed: JPA dirty checking writes changed fields
            // when the enclosing @Transactional method commits.
            return NoteResponse.from(note);
        });
    }

    public boolean delete(Integer id) {
        Optional<Note> existing = notes.findByIdAndDeletedAtIsNull(id);
        if (existing.isEmpty()) {
            return false;
        }
        // Source behavior is a hard delete. Soft-deleted historical rows are
        // treated as absent, matching the public read filters.
        notes.delete(existing.get());
        return true;
    }
}
