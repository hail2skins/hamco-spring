package com.hamco.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hamco.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * First red-phase authentication contract against a disposable PostgreSQL DB.
 *
 * <p>The live site is never used for mutations. Requests go through the real
 * Spring MVC dispatch pipeline, proving that guards and routes exist before
 * we add the rest of the registration/login/token service implementation.
 */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired MockMvc mvc;

    @Test
    void registrationIsLockedByDefaultBeforeLookingAtCredentials() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"Art","email":"art@example.test","password":"a-secret-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Registration is currently disabled"));
    }

    @Test
    void loginRejectsUnknownCredentialsWithoutDisclosingTheAccount() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.test","password":"a-secret-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void unknownResetRequestHasTheSameGenericResponse() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If your email is registered, you will receive a password reset link shortly."));
    }

    @Test
    void invalidVerificationRedirectsToTheLoginScreen() throws Exception {
        mvc.perform(get("/api/auth/verify-email").param("token", "unknown"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void profileCannotBeReadWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/auth/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookieEndpointRejectsAnInvalidTokenRatherThanAcceptingAnArbitraryString() throws Exception {
        mvc.perform(post("/api/auth/cookie").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-jwt\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousWriteRoutesRequireAuthenticationButPublicNotesStayReadable() throws Exception {
        mvc.perform(get("/api/notes")).andExpect(status().isOk());
        mvc.perform(post("/api/notes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/slogans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/slogans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/api-keys")).andExpect(status().isUnauthorized());
    }
}
