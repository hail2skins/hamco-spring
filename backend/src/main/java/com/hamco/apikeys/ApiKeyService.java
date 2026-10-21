package com.hamco.apikeys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates and validates automation credentials independently of user JWTs.
 *
 * <p>BCrypt uses only the first 72 input bytes on many implementations. Our
 * key is 73 ASCII bytes ({@code hamco_sk_} + 64 hex chars), so BCrypt of the
 * raw key would IGNORE its last character. Hashing its SHA-256 digest first
 * ensures every random byte participates, while retaining BCrypt at rest.
 * An indexed digest narrows new keys to one BCrypt check. A migration cannot
 * derive digests for previously issued keys, so only those legacy rows need
 * a fallback scan until rotated. No positive cache delays revocation.
 */
@Service
@Transactional
public class ApiKeyService {
    private final ApiKeyRepository keys;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final Clock clock = Clock.systemUTC();

    public ApiKeyService(ApiKeyRepository keys) {
        this.keys = keys;
    }

    public record IssuedKey(String secret, ApiKeyResponse metadata) {
    }

    public IssuedKey issue(String name, boolean isAdmin, String creator) {
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String secret = "hamco_sk_" + HexFormat.of().formatHex(randomBytes);
        String lookup = digest(secret);
        ApiKey stored = new ApiKey(UUID.randomUUID().toString(), name.trim(),
                encoder.encode(lookup), secret.substring(0, 8), lookup, isAdmin,
                clock.instant(), creator);
        return new IssuedKey(secret, ApiKeyResponse.from(keys.save(stored)));
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(String creator) {
        return keys.findByCreatedByUserIdOrderByCreatedAtDesc(creator).stream()
                .map(ApiKeyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ApiKeyResponse> detail(String id, String creator) {
        return keys.findByIdAndCreatedByUserId(id, creator).map(ApiKeyResponse::from);
    }

    public boolean revoke(String id, String creator) {
        return keys.findByIdAndCreatedByUserId(id, creator).map(key -> {
            key.revoke();
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<Authentication> authenticate(String secret) {
        if (secret == null || !secret.matches("hamco_sk_[a-f0-9]{64}")) {
            return Optional.empty();
        }
        String candidate = digest(secret);
        // The display prefix is constant for all keys. Query by the digest of
        // the complete 256-bit secret, then check BCrypt before trusting it.
        var indexed = keys.findByLookupDigestAndActiveTrue(candidate).stream();
        var legacy = keys.findByLookupDigestIsNullAndActiveTrue().stream();
        for (ApiKey stored : java.util.stream.Stream.concat(indexed, legacy).toList()) {
            if (stored.expiresAt() != null && !clock.instant().isBefore(stored.expiresAt())) {
                continue;
            }
            if (!encoder.matches(candidate, stored.hash())) {
                continue;
            }
            var role = new SimpleGrantedAuthority(stored.admin() ? "ROLE_Admin" : "ROLE_User");
            var principal = new UsernamePasswordAuthenticationToken(stored.id(), null, List.of(role));
            principal.setDetails("api_key");
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    private static String digest(String secret) {
        try {
            byte[] sha256 = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha256);
        } catch (NoSuchAlgorithmException impossibleOnJava25) {
            throw new IllegalStateException("SHA-256 is unavailable", impossibleOnJava25);
        }
    }
}
