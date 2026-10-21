package com.hamco.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.hamco.PostgresTestConfiguration;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Exercises real persistence, security filters and local mail sink without touching the developer DB. */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"ALLOW_REGISTRATION=true", "hamco.mail.mode=local", "hamco.auth.rate-limit=100"})
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired LocalMailSink mail;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired JdbcTemplate db;
    private final java.util.List<String> registered = new java.util.ArrayList<>();

    @BeforeEach void firstAdminFixtureStartsEmpty() {
        // This context has a dedicated Testcontainers database and no retained account fixtures.
        assertThat(db.queryForObject("select count(*) from users", Long.class)).isZero();
    }

    @AfterEach void removeOnlyThisTestsAccounts() {
        registered.forEach(email -> db.update("delete from users where email=?", email));
    }

    private MvcResult postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private String extractToken(String link) {
        return URI.create(link).getRawQuery().substring("token=".length());
    }

    @Test
    void registrationVerificationLoginCookieProfileResetAndReplayProtection() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        registered.add(email);
        String account = "{\"username\":\"Art\",\"email\":\"  " + email.toUpperCase() + "  \",\"password\":\"password123\"}";
        assertThat(postJson("/api/auth/register", account).getResponse().getStatus()).isEqualTo(200);
        String storedHash = db.queryForObject("select password_hash from users where email=?", String.class, email);
        assertThat(storedHash).startsWith("$2a$12$");
        assertThat(storedHash).doesNotContain("password123");
        String verification = extractToken(mail.latestLink(email));
        assertThat(postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"password123\"}").getResponse().getStatus()).isEqualTo(403);
        mvc.perform(get("/api/auth/verify-email").param("token", verification))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Location", "/auth/login?verified=1"));
        mvc.perform(get("/api/auth/verify-email").param("token", verification))
                .andExpect(header().string("Location", "/auth/login?error=expired_or_invalid_verification_token"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(account))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Email already exists"));
        MvcResult login = postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"password123\"}");
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String response = login.getResponse().getContentAsString();
        String jwt = JsonPath.read(response, "$.token");
        assertThat(UUID.fromString(JsonPath.read(response, "$.userId"))).isNotNull();
        assertThat(Instant.parse(JsonPath.read(response, "$.expiresAt"))).isAfter(Instant.now());
        assertThat((java.util.List<String>) JsonPath.read(response, "$.roles")).containsExactly("Admin");
        // Even when another test registered first, the admin bit must originate in the DB.
        String other = UUID.randomUUID() + "@example.test";
        registered.add(other);
        assertThat(postJson("/api/auth/register", "{\"username\":\"Other\",\"email\":\"" + other + "\",\"password\":\"password123\"}").getResponse().getStatus()).isEqualTo(200);
        mvc.perform(get("/api/auth/verify-email").param("token", extractToken(mail.latestLink(other))))
                .andExpect(status().is3xxRedirection());
        String otherJwt = JsonPath.read(postJson("/api/auth/login", "{\"email\":\"" + other + "\",\"password\":\"password123\"}")
                .getResponse().getContentAsString(), "$.token");
        mvc.perform(get("/api/admin/example").header("Authorization", "Bearer " + otherJwt))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/example").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/profile").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        MvcResult cookie = postJson("/api/auth/cookie", "{\"token\":\"" + jwt + "\"}");
        assertThat(cookie.getResponse().getStatus()).isEqualTo(200);
        assertThat(cookie.getResponse().getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Strict");
        mvc.perform(get("/api/auth/profile").cookie(cookie.getResponse().getCookies()))
                .andExpect(status().isOk());
        // A browser cookie is accepted for GET profile but cannot authorize a write.
        mvc.perform(post("/api/notes").cookie(cookie.getResponse().getCookies()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/logout")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"));
        assertThat(postJson("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}").getResponse().getStatus()).isEqualTo(200);
        String reset = extractToken(mail.latestLink(email));
        assertThat(postJson("/api/auth/reset-password", "{\"token\":\"" + reset + "\",\"newPassword\":\"newpass123\"}").getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/auth/reset-password", "{\"token\":\"" + reset + "\",\"newPassword\":\"newpass456\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"password123\"}").getResponse().getStatus()).isEqualTo(401);
        assertThat(postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"newpass123\"}").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMalformedPayloadsAndTamperedBearerAndCookie() throws Exception {
        assertThat(postJson("/api/auth/register", "{\"username\":\"\",\"email\":\"bad\",\"password\":\"123\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson("/api/auth/login", "{\"email\":\"bad\",\"password\":\"123\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson("/api/auth/reset-password", "{\"token\":\"x\",\"newPassword\":\"123\"}").getResponse().getStatus()).isEqualTo(400);
        mvc.perform(get("/api/auth/profile").header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/profile").cookie(new jakarta.servlet.http.Cookie("AuthToken", "invalid.jwt.token")))
                .andExpect(status().isUnauthorized());
    }

    @Test void jwtValidatorRejectsExpiredWrongIssuerAndWrongAudience() {
        for (String type : java.util.List.of("expired", "issuer", "audience")) {
            Instant now = Instant.now();
            var claims = JwtClaimsSet.builder().subject(UUID.randomUUID().toString())
                    .issuer(type.equals("issuer") ? "foreign-issuer" : "hamco-api")
                    .audience(java.util.List.of(type.equals("audience") ? "foreign-client" : "hamco-client"))
                    .issuedAt(now.minusSeconds(300))
                    // ASP.NET sets ClockSkew.Zero: five seconds past expiry must already fail.
                    .expiresAt(type.equals("expired") ? now.minusSeconds(5) : now.plusSeconds(300)).build();
            String signed = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> decoder.decode(signed))
                    .isInstanceOf(JwtException.class);
        }
    }
}
