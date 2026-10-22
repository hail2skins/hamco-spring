package com.hamco.slogans;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A narrow public read model; admin inventory and mutations remain private. */
@RestController
public class SiteSloganController {
    private final SloganService slogans;

    public SiteSloganController(SloganService slogans) {
        this.slogans = slogans;
    }

    @GetMapping("/api/site/slogan")
    public SiteSloganResponse slogan() {
        return new SiteSloganResponse(slogans.randomActiveText());
    }

    // A DTO prevents accidentally serializing IDs, status or creator metadata.
    public record SiteSloganResponse(String text) { }
}