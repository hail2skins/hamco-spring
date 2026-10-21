package com.hamco.apikeys;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hamco.PostgresTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import org.springframework.test.web.servlet.MvcResult;
import com.jayway.jsonpath.JsonPath;

/**
 * API keys have a public secret shown exactly once and persisted only hashed.
 *
 * <p>The source mistakenly used the constant first eight characters of every
 * key as if it were a unique prefix; these tests specify the API contract,
 * while additional validation tests will catch collisions and revocation.
 * No key is sent to the production site or written to a fixture log.
 */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeysApiTest {

    private static final String ADMIN_ID = "00000000-0000-4000-8000-000000000013";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetFixtures() {
        jdbc.update("DELETE FROM api_keys");
        jdbc.update("DELETE FROM users WHERE id = ?", ADMIN_ID);
        jdbc.update("""
                INSERT INTO users (id, username, email, password_hash, is_admin, is_email_verified)
                VALUES (?, 'Key Admin', 'key-admin@example.test', 'fixture-only', true, true)
                """, ADMIN_ID);
    }

    @AfterEach
    void removeFixtures() {
        // Clean up between classes as well as methods; auth tests elect the
        // first registered user and must not see this synthetic admin.
        jdbc.update("DELETE FROM notes");
        jdbc.update("DELETE FROM api_keys");
        jdbc.update("DELETE FROM users WHERE id = ?", ADMIN_ID);
    }

    @Test
    void anonymousCannotListOrCreateKeys() throws Exception {
        mvc.perform(get("/api/admin/api-keys"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/api-keys")
                        .with(user("ordinary-user").authorities(new SimpleGrantedAuthority("ROLE_User"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/api-keys").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bot\",\"isAdmin\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidOrEmptyPresentedKeyIsRejectedEvenOnPublicReads() throws Exception {
        // A deliberately presented credential must not silently become an
        // anonymous public request; source middleware short-circuits with 401.
        mvc.perform(get("/api/notes").header("X-API-Key", ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("API key is empty."));
        mvc.perform(get("/api/notes").header("X-API-Key", "hamco_sk_not-a-valid-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or revoked API key."));
    }

    @Test
    @WithMockUser(username = ADMIN_ID, authorities = "ROLE_Admin")
    void generationReturnsSecretOnceAndPersistsOnlyItsBcryptDigest() throws Exception {
        mvc.perform(post("/api/admin/api-keys").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bot\",\"isAdmin\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(matchesPattern("hamco_sk_[a-f0-9]{64}")))
                .andExpect(jsonPath("$.name").value("Bot"));
        org.junit.jupiter.api.Assertions.assertTrue(
                jdbc.queryForObject("SELECT key_hash FROM api_keys WHERE name='Bot'", String.class)
                        .startsWith("$2"));
        mvc.perform(get("/api/admin/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").doesNotExist())
                .andExpect(jsonPath("$[0].keyHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = ADMIN_ID, authorities = "ROLE_Admin")
    void unknownKeyCannotBeRevokedAndInvalidNameIsRejected() throws Exception {
        mvc.perform(post("/api/admin/api-keys").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/api-keys/no-such-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void twoKeysWithTheSameConstantDisplayPrefixAuthenticateIndependently() throws Exception {
        // The ASP.NET source stores the FIRST eight chars of "hamco_sk_...";
        // they are identical for every key. Never treat that prefix as a
        // unique lookup or stop after checking just one candidate.
        String first = issueWithMockAdmin("First");
        String second = issueWithMockAdmin("Second");
        for (String secret : new String[] {first, second}) {
            mvc.perform(post("/api/notes").header("X-API-Key", secret)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"From a key\",\"content\":\"body\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    @Test
    void keyLookupIsIndexedByTheFullSecretRatherThanTheSharedDisplayPrefix() throws Exception {
        String secret = issueWithMockAdmin("Indexed key");
        // The entire randomly generated key is high entropy. An indexed SHA-256
        // digest selects one candidate before BCrypt verification; scanning
        // every active key would make an attacker-controlled header expensive.
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8)));
        org.junit.jupiter.api.Assertions.assertEquals(expected,
                jdbc.queryForObject("SELECT lookup_digest FROM api_keys WHERE name='Indexed key'", String.class));
        mvc.perform(post("/api/notes").header("X-API-Key", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Indexed\",\"content\":\"body\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void revocationTakesEffectOnTheVeryNextRequest() throws Exception {
        String secret = issueWithMockAdmin("Revoke me");
        String id = jdbc.queryForObject("SELECT id FROM api_keys WHERE name='Revoke me'", String.class);
        mvc.perform(delete("/api/admin/api-keys/{id}", id)
                        .with(user(ADMIN_ID).authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/notes").header("X-API-Key", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Blocked\",\"content\":\"body\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatorCannotRevokeAnotherAdministratorsKey() throws Exception {
        issueWithMockAdmin("Owned by first admin");
        String id = jdbc.queryForObject("SELECT id FROM api_keys WHERE name='Owned by first admin'", String.class);
        mvc.perform(delete("/api/admin/api-keys/{id}", id)
                        .with(user("a-different-admin").authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertTrue(
                jdbc.queryForObject("SELECT is_active FROM api_keys WHERE id=?", Boolean.class, id));
    }

    private String issueWithMockAdmin(String name) throws Exception {
        // Per-request principal avoids accidentally treating the key-based
        // follow-up requests as a still-logged-in administrator.
        MvcResult issued = mvc.perform(post("/api/admin/api-keys")
                        .with(user(ADMIN_ID).authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"isAdmin\":true}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(issued.getResponse().getContentAsString(), "$.key");
    }
}
