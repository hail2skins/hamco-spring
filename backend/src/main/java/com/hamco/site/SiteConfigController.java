package com.hamco.site;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes only harmless presentation flags to the React client. The server,
 * not Vite's build-time environment, remains authoritative for registration.
 */
@RestController
public class SiteConfigController {
    private final boolean registrationEnabled;

    public SiteConfigController(@Value("${ALLOW_REGISTRATION:false}") boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    @GetMapping("/api/site/config")
    public Map<String, Boolean> config() {
        return Map.of("registrationEnabled", registrationEnabled);
    }
}
