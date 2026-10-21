package com.hamco.notes;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hamco.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP contract for the first useful slice of the HAMCO API.
 *
 * <p>Unlike a controller unit test with a mocked repository, this test sends
 * real HTTP-shaped requests through Spring MVC and reads rows from disposable
 * PostgreSQL. It therefore proves routing, JSON serialization, SQL filtering,
 * and the Flyway migration together. We wrote it before the notes controller
 * and repository: the first run should return 404 instead of the expected 200.
 */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotesApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetNotes() {
        // Flyway owns the schema; tests own only their fixture rows. A fresh
        // container keeps this cleanup away from both local and live databases.
        jdbc.update("DELETE FROM notes");
    }

    @Test
    void publicListReturnsAnEmptyJsonArray() throws Exception {
        mvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void publicListIncludesAStoredNoteInTheAspNetResponseShape() throws Exception {
        int id = insertNote("A sample note", "hello **Markdown**");

        mvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].title").value("A sample note"))
                .andExpect(jsonPath("$[0].slug").value("a-sample-note"))
                .andExpect(jsonPath("$[0].content").value("hello **Markdown**"))
                .andExpect(jsonPath("$[0].userId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists());
    }

    @Test
    void publicDetailReturnsTheStoredNote() throws Exception {
        int id = insertNote("One post", "body");

        mvc.perform(get("/api/notes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("One post"));
    }

    @Test
    void deletedNotesAreNotVisibleInEitherPublicRoute() throws Exception {
        int id = insertNote("Hidden post", "body");
        jdbc.update("UPDATE notes SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id);

        mvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/notes/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownNoteReturnsNotFound() throws Exception {
        mvc.perform(get("/api/notes/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    private int insertNote(String title, String content) {
        // JDBC fixtures make the database state explicit and independent of
        // the repository implementation the production endpoint will use.
        return jdbc.queryForObject("""
                INSERT INTO notes (title, slug, content)
                VALUES (?, ?, ?) RETURNING id
                """, Integer.class, title, title.toLowerCase().replace(' ', '-'), content);
    }
}
