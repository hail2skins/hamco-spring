package com.hamco.apikeys;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A key's plaintext is never a persistent field. BCrypt protects validation;
 * a separate SHA-256 digest of the high-entropy key provides indexed lookup.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {
    @Id
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 60)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(name = "lookup_digest", length = 64)
    private String lookupDigest;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by_user_id", nullable = false, length = 36)
    private String createdByUserId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ApiKey() {
        // JPA reconstructs database rows using this constructor.
    }

    ApiKey(String id, String name, String keyHash, String keyPrefix, String lookupDigest,
           boolean admin, Instant createdAt, String creator) {
        this.id = id;
        this.name = name;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.lookupDigest = lookupDigest;
        this.admin = admin;
        this.createdAt = createdAt;
        this.createdByUserId = creator;
        this.active = true;
    }

    void revoke() { active = false; }
    String id() { return id; }
    String name() { return name; }
    String hash() { return keyHash; }
    String prefix() { return keyPrefix; }
    boolean admin() { return admin; }
    Instant expiresAt() { return expiresAt; }
    Instant createdAt() { return createdAt; }
    String creator() { return createdByUserId; }
    boolean active() { return active; }
}
