package com.hamco.apikeys;

import java.time.Instant;

/** Summary and detail shape: intentionally excludes the hash and raw secret. */
public record ApiKeyResponse(String id, String name, String keyPrefix,
                             boolean isAdmin, boolean isActive,
                             Instant createdAt, Instant expiresAt) {
    static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(key.id(), key.name(), key.prefix(),
                key.admin(), key.active(), key.createdAt(), key.expiresAt());
    }
}
