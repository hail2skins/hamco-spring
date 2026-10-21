package com.hamco.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** ASP.NET-compatible auth routes; only token hashes and BCrypt digests enter PostgreSQL. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record Register(@NotBlank String username, @NotBlank @Email String email,
                           @NotBlank @Size(min = 6) String password) {
        // Record binding normalizes surrounding whitespace before Bean Validation runs.
        public Register { if (email != null) email = email.trim(); }
    }
    public record Login(@NotBlank @Email String email, @NotBlank String password) {}
    public record Forgot(@NotBlank @Email String email) {}
    public record Reset(@NotBlank String token, @NotBlank @Size(min = 6) String newPassword) {}
    public record SetCookie(@NotBlank String token) {}
    private record Account(String id, String email, String hash, boolean admin, boolean verified) {}

    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final TransactionalMail mail;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthSecurity config;
    private final Environment environment;
    // Match the source cost factor: BCrypt embeds $2a$12$ in each digest for future verification.
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    @Value("${ALLOW_REGISTRATION:false}") private boolean allowRegistration;
    @Value("${APP_BASE_URL:http://localhost:8080}") private String baseUrl;
    @Value("${hamco.auth.cookie-secure:false}") private boolean secureCookie;

    public AuthController(JdbcTemplate db, org.springframework.transaction.PlatformTransactionManager transactionManager,
                          TransactionalMail mail, JwtEncoder encoder, JwtDecoder decoder, AuthSecurity config,
                          Environment environment) {
        this.db = db; this.tx = new TransactionTemplate(transactionManager);
        this.mail = mail; this.encoder = encoder; this.decoder = decoder; this.config = config;
        this.environment = environment;
    }

    private List<Account> find(String email) {
        return db.query("select id,email,password_hash,is_admin,is_email_verified from users where email=?", (rs, n) ->
                new Account(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5)), email);
    }
    private static String normalized(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }
    private static String token() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String digest(String token) {
        try { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String link(String path, String token) {
        return baseUrl.replaceAll("/$", "") + "/auth/" + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody Register request) {
        if (!allowRegistration) return ResponseEntity.status(403).body(Map.of("message", "Registration is currently disabled"));
        String email = normalized(request.email());
        // PostgreSQL transaction-level advisory lock serializes the first-account election and duplicate check.
        // Only the SHA-256 digest of the emailed token is persisted; the original exists in this call only.
        String verification = token();
        int registrationResult = tx.execute(status -> {
            db.execute("select pg_advisory_xact_lock(48414)");
            var existing = find(email);
            if (!existing.isEmpty()) {
                if (existing.getFirst().verified()) return 2;
                db.update("update users set email_verification_token_hash=?,email_verification_token_expires_at=? where id=?",
                        digest(verification), java.sql.Timestamp.from(Instant.now().plus(20, ChronoUnit.MINUTES)), existing.getFirst().id());
                return 1;
            }
            boolean admin = db.queryForObject("select count(*) from users", Long.class) == 0;
            db.update("insert into users (id,username,email,password_hash,is_admin,is_email_verified,email_verification_token_hash,email_verification_token_expires_at) values (?,?,?,?,?,false,?,?)",
                    UUID.randomUUID().toString(), request.username().trim(), email, passwords.encode(request.password()), admin,
                    digest(verification), java.sql.Timestamp.from(Instant.now().plus(20, ChronoUnit.MINUTES)));
            return 0;
        });
        if (registrationResult == 2)
            return ResponseEntity.status(409).body(Map.of("message", "Email already exists"));
        mail.send(email, "Verify your HAMCO account", link("verify-email", verification));
        return ResponseEntity.ok(Map.of("message", registrationResult == 1
                ? "Account already exists but is not verified. We sent a new verification email."
                : "Registration successful. Please verify your email.", "requiresEmailVerification", true, "email", email));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verify(@RequestParam(required = false) String token) {
        if (token == null || token.isBlank()) return redirect("/auth/login?error=invalid_verification_token");
        int changed = db.update("update users set is_email_verified=true,email_verification_token_hash=null,email_verification_token_expires_at=null where email_verification_token_hash=? and email_verification_token_expires_at > ?",
                digest(token), java.sql.Timestamp.from(Instant.now()));
        return redirect(changed == 1 ? "/auth/login?verified=1" : "/auth/login?error=expired_or_invalid_verification_token");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Login request) {
        var matches = find(normalized(request.email()));
        if (matches.isEmpty() || !passwords.matches(request.password(), matches.getFirst().hash()))
            return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password"));
        var user = matches.getFirst();
        if (!user.verified()) return ResponseEntity.status(403).body(Map.of("message", "Please verify your email before logging in."));
        Instant expiry = Instant.now().plus(60, ChronoUnit.MINUTES);
        var claims = JwtClaimsSet.builder().issuer(config.issuer()).audience(List.of(config.audience()))
                .subject(user.id()).issuedAt(Instant.now()).expiresAt(expiry).id(UUID.randomUUID().toString())
                .claim("email", user.email()).claim("roles", user.admin() ? List.of("Admin") : List.of()).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return ResponseEntity.ok(Map.of("token", jwt, "userId", user.id(), "email", user.email(),
                "roles", user.admin() ? List.of("Admin") : List.of(), "expiresAt", expiry));
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgot(@Valid @RequestBody Forgot request) {
        String email = normalized(request.email());
        if (!find(email).isEmpty()) {
            String reset = token();
            db.update("update users set password_reset_token_hash=?, password_reset_token_expires_at=? where email=?",
                    digest(reset), java.sql.Timestamp.from(Instant.now().plus(20, ChronoUnit.MINUTES)), email);
            mail.send(email, "Reset your HAMCO password", link("reset-password", reset));
        }
        return Map.of("message", "If your email is registered, you will receive a password reset link shortly.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> reset(@Valid @RequestBody Reset request) {
        int count = db.update("update users set password_hash=?,password_reset_token_hash=null,password_reset_token_expires_at=null where password_reset_token_hash=? and password_reset_token_expires_at > ?",
                passwords.encode(request.newPassword()), digest(request.token()), java.sql.Timestamp.from(Instant.now()));
        return count == 1 ? ResponseEntity.ok(Map.of("message", "Password reset successful. You can now log in."))
                : ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired reset token"));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile(Authentication authentication) {
        var users = db.query("select id,email,is_admin from users where id=? and is_email_verified=true", (rs, i) ->
                Map.of("userId", rs.getString(1), "email", rs.getString(2), "roles", rs.getBoolean(3) ? List.of("Admin") : List.of(),
                        "token", "", "expiresAt", Instant.EPOCH), authentication.getName());
        return users.isEmpty() ? ResponseEntity.status(401).body(Map.of("message", "User not found")) : ResponseEntity.ok(users.getFirst());
    }

    @PostMapping("/cookie")
    public ResponseEntity<?> cookie(@Valid @RequestBody SetCookie request, HttpServletResponse response) {
        try {
            var jwt = decoder.decode(request.token());
            if (db.queryForObject("select count(*) from users where id=? and is_email_verified=true", Long.class, jwt.getSubject()) == 0)
                return ResponseEntity.status(401).body(Map.of("message", "Invalid token"));
        } catch (JwtException ex) { return ResponseEntity.status(401).body(Map.of("message", "Invalid token")); }
        response.addHeader(HttpHeaders.SET_COOKIE, "AuthToken=" + request.token() + "; Max-Age=3600; Path=/; HttpOnly; SameSite=Strict" + (requiresSecureCookie(secureCookie, environment) ? "; Secure" : ""));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.status(302).header(HttpHeaders.SET_COOKIE,
                        "AuthToken=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict" + (requiresSecureCookie(secureCookie, environment) ? "; Secure" : ""))
                .header(HttpHeaders.LOCATION, "/").build();
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
    }

    static boolean requiresSecureCookie(boolean configured, Environment environment) {
        // Production profiles cannot accidentally downgrade cookie transport over HTTP.
        return configured || java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"))
                || environment.getProperty("hamco.auth.production", Boolean.class, false);
    }
}
