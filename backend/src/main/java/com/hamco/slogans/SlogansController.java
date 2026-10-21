package com.hamco.slogans;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only HTTP boundary for brand slogans. The filter chain authorizes all
 * methods under this path; this class then validates bodies and maps results
 * to the status codes and JSON shapes expected by the old API.
 */
@RestController
@RequestMapping("/api/slogans")
public class SlogansController {
    private final SloganService slogans;

    public SlogansController(SloganService slogans) {
        this.slogans = slogans;
    }

    @GetMapping
    public List<SloganResponse> list() {
        return slogans.list();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SloganRequest request, Authentication authentication) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Slogan text is required"));
        }
        if (request.text().trim().length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("message", "Slogan text is too long"));
        }
        String creator = "api_key".equals(authentication.getDetails()) ? null : authentication.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(slogans.create(request, creator));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody SloganRequest request) {
        if (request.text() != null && request.text().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Slogan text cannot be empty"));
        }
        if (request.text() != null && request.text().trim().length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("message", "Slogan text is too long"));
        }
        return slogans.update(id, request).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Slogan not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        return slogans.delete(id) ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Slogan not found"));
    }
}
