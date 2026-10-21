package com.hamco.auth;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.hamco.PostgresTestConfiguration;
import com.hamco.apikeys.ApiKeyService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API keys enter at the security boundary, but cannot override an existing bearer identity. */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeySecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean ApiKeyService keys;

    @Test void validAdminKeyAuthorizesAndInvalidOrUserKeysDoNot() throws Exception {
        var admin = new UsernamePasswordAuthenticationToken("key-id", null, List.of(new SimpleGrantedAuthority("ROLE_Admin")));
        admin.setDetails("api_key");
        when(keys.authenticate("admin-key")).thenReturn(Optional.of(admin));
        mvc.perform(get("/api/admin/example").header("X-API-Key", "admin-key"))
                .andExpect(status().isNotFound());
        var user = new UsernamePasswordAuthenticationToken("key-id", null, List.of(new SimpleGrantedAuthority("ROLE_User")));
        user.setDetails("api_key");
        when(keys.authenticate("user-key")).thenReturn(Optional.of(user));
        mvc.perform(get("/api/admin/example").header("X-API-Key", "user-key"))
                .andExpect(status().isForbidden());
        when(keys.authenticate("revoked-key")).thenReturn(Optional.empty());
        mvc.perform(get("/api/admin/example").header("X-API-Key", "revoked-key"))
                .andExpect(status().isUnauthorized());
        var jwt = JwtClaimsSet.builder().subject(java.util.UUID.randomUUID().toString()).issuer("hamco-api")
                .audience(List.of("hamco-client")).issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(300)).claim("roles", List.of("User")).build();
        String bearer = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), jwt)).getTokenValue();
        mvc.perform(get("/api/admin/example").header("Authorization", "Bearer " + bearer)
                        .header("X-API-Key", "admin-key")).andExpect(status().isForbidden());
        verify(keys, times(1)).authenticate("admin-key");
    }
}
