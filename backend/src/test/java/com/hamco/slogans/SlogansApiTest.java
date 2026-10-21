package com.hamco.slogans;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Admin slogan contract from the pinned ASP.NET controller.
 *
 * <p>Mock principals isolate authorization policy from the separate tests
 * that prove real JWT signatures. SQL fixtures use disposable PostgreSQL and
 * deliberately include an inactive slogan: GET is an admin inventory, not
 * the future public random-slogan display service.
 */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SlogansApiTest {

    private static final String ADMIN_ID = "00000000-0000-4000-8000-000000000012";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetFixtures() {
        jdbc.update("DELETE FROM slogans");
    }

    @AfterEach
    void removeFixtures() {
        jdbc.update("DELETE FROM slogans");
    }

    @Test
    void anonymousAndRegularUsersCannotReadTheAdminInventory() throws Exception {
        mvc.perform(get("/api/slogans"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/slogans")
                        .with(user("ordinary-user").authorities(new SimpleGrantedAuthority("ROLE_User"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = ADMIN_ID, authorities = "ROLE_Admin")
    void listIncludesInactiveEntriesNewestFirst() throws Exception {
        jdbc.update("""
                INSERT INTO slogans (text, is_active, created_at)
                VALUES ('older', true, CURRENT_TIMESTAMP - INTERVAL '1 day'),
                       ('newer inactive', false, CURRENT_TIMESTAMP)
                """);
        mvc.perform(get("/api/slogans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].text").value("newer inactive"))
                .andExpect(jsonPath("$[0].isActive").value(false));
    }

    @Test
    @WithMockUser(username = ADMIN_ID, authorities = "ROLE_Admin")
    void createTrimsDefaultsActiveAndAllowsAStatusOnlyUpdate() throws Exception {
        mvc.perform(post("/api/slogans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  Hello World  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Hello World"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.createdByUserId").value(ADMIN_ID));
        int id = jdbc.queryForObject("SELECT id FROM slogans WHERE text = 'Hello World'", Integer.class);
        mvc.perform(put("/api/slogans/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Hello World"))
                .andExpect(jsonPath("$.isActive").value(false));
        mvc.perform(delete("/api/slogans/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = ADMIN_ID, authorities = "ROLE_Admin")
    void missingOrWhitespaceTextIsRejected() throws Exception {
        mvc.perform(post("/api/slogans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Slogan text is required"));
        mvc.perform(put("/api/slogans/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"new\"}"))
                .andExpect(status().isNotFound());
    }
}
