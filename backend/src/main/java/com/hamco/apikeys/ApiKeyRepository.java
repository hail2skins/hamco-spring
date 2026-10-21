package com.hamco.apikeys;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Scope metadata queries by creator: one administrator must not see or revoke
 * another's keys merely by knowing an ID. Validation separately checks active
 * keys; revoked or expired records never yield an authentication principal.
 */
interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    List<ApiKey> findByCreatedByUserIdOrderByCreatedAtDesc(String creator);
    Optional<ApiKey> findByIdAndCreatedByUserId(String id, String creator);
    Optional<ApiKey> findByLookupDigestAndActiveTrue(String lookupDigest);
    List<ApiKey> findByLookupDigestIsNullAndActiveTrue();
}
