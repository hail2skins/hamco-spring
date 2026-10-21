package com.hamco.slogans;

import java.time.Instant;

/** Public JSON shape for an admin slogan; no JPA internals are serialized. */
public record SloganResponse(Integer id, String text, boolean isActive,
                             Instant createdAt, String createdByUserId, Instant updatedAt) {
    static SloganResponse from(Slogan slogan) {
        return new SloganResponse(slogan.id(), slogan.text(), slogan.active(),
                slogan.createdAt(), slogan.createdByUserId(), slogan.updatedAt());
    }
}
