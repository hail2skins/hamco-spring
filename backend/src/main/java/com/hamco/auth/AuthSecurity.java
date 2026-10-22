package com.hamco.auth;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.hamco.apikeys.ApiKeyService;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/** Central security boundary: signed JWTs only, no sessions and no anonymous writes. */
@Configuration
public class AuthSecurity {
    private final String issuer, audience;
    private final byte[] key;

    public AuthSecurity(@Value("${JWT_KEY:}") String secret,
                        @Value("${JWT_ISSUER:hamco-api}") String issuer,
                        @Value("${JWT_AUDIENCE:hamco-client}") String audience,
                        @Value("${hamco.auth.production:false}") boolean production, Environment environment) {
        boolean productionProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("production") || p.equalsIgnoreCase("prod"));
        if ((production || productionProfile) && secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("Production requires explicit JWT_KEY of at least 32 bytes");
        // A different random key on each local startup avoids embedding a reusable development secret.
        if (secret.isBlank()) {
            key = new byte[32]; new SecureRandom().nextBytes(key);
        } else {
            key = secret.getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) throw new IllegalStateException("JWT_KEY must be at least 32 bytes");
        }
        this.issuer = issuer; this.audience = audience;
    }

    @Bean JwtEncoder jwtEncoder() { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean JwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        // Default Spring JWT validation tolerates clock drift; the source accepts no grace period.
        var timestamp = new JwtTimestampValidator(Duration.ZERO);
        var issuerValidator = new org.springframework.security.oauth2.jwt.JwtIssuerValidator(issuer);
        decoder.setJwtValidator(jwt -> {
            var lifetime = timestamp.validate(jwt);
            if (lifetime.hasErrors()) return lifetime;
            var origin = issuerValidator.validate(jwt);
            if (origin.hasErrors()) return origin;
            if (!jwt.getAudience().contains(audience))
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Wrong audience", null));
            return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
        });
        return decoder;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, ApiKeyService apiKeys) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/profile").authenticated()
                        // Permit precisely the site display read, never public slogan mutations.
                        .requestMatchers(HttpMethod.GET, "/api/site/slogan", "/api/site/config").permitAll()
                        .requestMatchers("/api/admin/**", "/api/slogans", "/api/slogans/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.POST, "/api/notes", "/api/notes/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/notes", "/api/notes/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.PATCH, "/api/notes", "/api/notes/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/notes", "/api/notes/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.GET, "/api/notes", "/api/notes/**").permitAll()
                        .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, ex) -> response.sendError(403)))
                .oauth2ResourceServer(o -> o.bearerTokenResolver(cookieOrBearer())
                        .jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(jwt -> {
                            List<String> roles = jwt.getClaimAsStringList("roles");
                            var authorities = (roles == null ? List.<String>of() : roles).stream()
                                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                            return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                                    jwt, authorities, jwt.getSubject());
                        })))
                .addFilterAfter(new ApiKeyAuthenticationFilter(apiKeys), BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean BearerTokenResolver cookieOrBearer() {
        var bearer = new DefaultBearerTokenResolver();
        return request -> {
            String header = bearer.resolve(request);
            if (header != null) return header;
            // Cookies authenticate safe reads only. Mutations need an explicit Authorization
            // bearer (or later the API-key filter); this avoids cookie-backed CSRF writes.
            if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return null;
            Cookie[] cookies = request.getCookies();
            return cookies == null ? null : Arrays.stream(cookies)
                    .filter(c -> "AuthToken".equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
        };
    }

    String issuer() { return issuer; }
    String audience() { return audience; }
}
