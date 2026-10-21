package com.hamco.notes;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hamco.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Red-phase HTTP contracts for the admin portion of the notes API.
 *
 * <p>Mock users test authorization decisions, not token signing. Separate
 * authentication tests exercise real signed JWTs. Fixtures use disposable
 * PostgreSQL so mistakes cannot touch either the live site or local dev rows.
 */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotesWriteApiTest {

    private static final String ADMIN_ID = "00000000-0000-4000-8000-000000000011";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetFixtures() {
        jdbc.update("DELETE FROM notes");
        jdbc.update("DELETE FROM users WHERE id = ?", ADMIN_ID);
        jdbc.update("""
                INSERT INTO users (id, username, email, password_hash, is_admin, is_email_verified)
                VALUES (?, 'Test Admin', 'test-admin@example.test', 'fixture-only', true, true)
                """, ADMIN_ID);
    }

    @AfterEach
    void removeFixtures() {
        // The container can be shared across classes; do not reserve the
        // first-user admin slot for an unrelated registration test.
        jdbc.update("DELETE FROM notes WHERE user_id = ?", ADMIN_ID);
        jdbc.update("DELETE FROM users WHERE id = ?", ADMIN_ID);
    }

    @Test
    void anonymousVisitorsCannotCreateNotes() throws Exception {
        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\",\"content\":\"body\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notes")
                        .with(user("ordinary-user").authorities(new SimpleGrantedAuthority("ROLE_User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Still nope\",\"content\":\"body\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = ADMIN_ID, authorities = "ROLE_Admin")
    void anAdminCanCreateAStoredNoteWithTheGeneratedSlugAndLocation() throws Exception {
        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Hello, Spring!  \",\"content\":\"# Markdown\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/notes/")))
                .andExpect(jsonPath("$.title").value("  Hello, Spring!  "))
                .andExpect(jsonPath("$.slug").value("hello-spring"))
                .andExpect(jsonPath("$.content").value("# Markdown"))
                .andExpect(jsonPath("$.userId").value(ADMIN_ID));
    }

    @Test
    @WithMockUser(username = ADMIN_ID, authorities = "ROLE_Admin")
    void updateRegeneratesTheSlugAndDeleteRemovesTheRow() throws Exception {
        int id = jdbc.queryForObject("""
                INSERT INTO notes (title, slug, content, user_id)
                VALUES ('Before', 'before', 'old body', ?) RETURNING id
                """, Integer.class, ADMIN_ID);

        mvc.perform(put("/api/notes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"After Title\",\"content\":\"new body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("after-title"))
                .andExpect(jsonPath("$.content").value("new body"));

        mvc.perform(delete("/api/notes/{id}", id))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbc.queryForObject("SELECT count(*) FROM notes WHERE id = ?", Integer.class, id));
    }

    @Test
    @WithMockUser(username = ADMIN_ID, authorities = "ROLE_Admin")
    void invalidTitleAndMissingResourceHavePreciseErrors() throws Exception {
        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"body\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/notes/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Valid\",\"content\":\"body\"}"))
                .andExpect(status().isNotFound());
    }
}
