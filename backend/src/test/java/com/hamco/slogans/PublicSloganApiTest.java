package com.hamco.slogans;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Public site display contract. MockMvc exercises routing and the real security
 * filter chain; fixtures live only in the disposable Testcontainers PostgreSQL.
 */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PublicSloganApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearFixtures() {
        jdbc.update("DELETE FROM slogans");
    }

    @AfterEach
    void removeFixtures() {
        jdbc.update("DELETE FROM slogans");
    }

    @Test
    void anonymousReadFallsBackWhenThereAreNoActiveSlogans() throws Exception {
        // Inactive rows are not display candidates, just like an empty table.
        jdbc.update("INSERT INTO slogans (text, is_active, created_at) VALUES ('hidden', false, CURRENT_TIMESTAMP)");
        mvc.perform(get("/api/site/slogan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.text").value("Your trusted partner in Internet solutions"));
    }

    @Test
    void anonymousReadReturnsOnlyAnActiveSloganWithoutAdminFields() throws Exception {
        jdbc.update("""
                INSERT INTO slogans (text, is_active, created_at)
                VALUES ('hidden', false, CURRENT_TIMESTAMP),
                       ('visible', true, CURRENT_TIMESTAMP)
                """);
        mvc.perform(get("/api/site/slogan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.text").value("visible"));
    }

    @Test
    void multipleActiveSlogansAreEligibleButInactiveOnesNeverAre() throws Exception {
        // A random draw cannot predict which active row wins; membership is the stable contract.
        jdbc.update("""
                INSERT INTO slogans (text, is_active, created_at)
                VALUES ('first', true, CURRENT_TIMESTAMP),
                       ('second', true, CURRENT_TIMESTAMP),
                       ('hidden', false, CURRENT_TIMESTAMP)
                """);
        for (int draw = 0; draw < 10; draw++) {
            mvc.perform(get("/api/site/slogan"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.text", is(oneOf("first", "second"))));
        }
    }

    @Test
    void publicReadDoesNotOpenAnAnonymousWriteRoute() throws Exception {
        mvc.perform(post("/api/site/slogan"))
                .andExpect(status().isUnauthorized());
    }
}
