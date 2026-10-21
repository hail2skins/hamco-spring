package com.hamco.notes;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data generates the SQL for these methods from their names.
 *
 * <p>Filtering {@code deletedAt IS NULL} here keeps soft-deleted historical
 * rows out of BOTH public reads. The ASP.NET JSON API does not promise a list
 * order, so we do not invent one at this stage.
 */
public interface NoteRepository extends JpaRepository<Note, Integer> {
    List<Note> findByDeletedAtIsNull();
    Optional<Note> findByIdAndDeletedAtIsNull(Integer id);
}
