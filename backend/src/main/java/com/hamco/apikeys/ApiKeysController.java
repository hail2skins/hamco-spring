package com.hamco.apikeys;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin management routes. The security chain enforces ROLE_Admin first;
 * management additionally requires a PERSON's JWT, not an admin automation
 * key (whose ID is not a users-table ID). Revocation is owner-scoped.
 */
@RestController
@RequestMapping("/api/admin/api-keys")
public class ApiKeysController {
    private final ApiKeyService keys;

    public ApiKeysController(ApiKeyService keys) {
        this.keys = keys;
    }

    public record GenerateKeyRequest(String name, boolean isAdmin) {
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        if ("api_key".equals(authentication.getDetails())) return ResponseEntity.status(401).build();
        List<ApiKeyResponse> result = keys.list(authentication.getName());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable String id, Authentication authentication) {
        if ("api_key".equals(authentication.getDetails())) return ResponseEntity.status(401).build();
        return keys.detail(id, authentication.getName()).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("error", "API key not found.")));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody GenerateKeyRequest request,
                                     Authentication authentication) {
        if ("api_key".equals(authentication.getDetails())) return ResponseEntity.status(401).build();
        if (request.name() == null || request.name().isBlank() || request.name().trim().length() > 255) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name is required."));
        }
        ApiKeyService.IssuedKey issued = keys.issue(request.name(), request.isAdmin(), authentication.getName());
        ApiKeyResponse metadata = issued.metadata();
        // This is the sole response containing the raw key. List/detail return
        // DTOs with no secret or hash field, even to the creator.
        return ResponseEntity.created(URI.create("/api/admin/api-keys/" + metadata.id()))
                .body(Map.of("key", issued.secret(), "id", metadata.id(),
                        "name", metadata.name(), "prefix", metadata.keyPrefix(),
                        "isAdmin", metadata.isAdmin(), "createdAt", metadata.createdAt(),
                        "message", "Save this key securely. You won't see it again!"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> revoke(@PathVariable String id, Authentication authentication) {
        if ("api_key".equals(authentication.getDetails())) return ResponseEntity.status(401).build();
        return keys.revoke(id, authentication.getName())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(404).body(Map.of("error", "API key not found."));
    }
}
