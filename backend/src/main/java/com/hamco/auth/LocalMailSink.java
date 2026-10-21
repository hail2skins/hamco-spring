package com.hamco.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

/** Local-only outbox: tests can inspect links without delivering mail or logging bearer secrets. */
@Component
@ConditionalOnProperty(name = "hamco.mail.mode", havingValue = "local", matchIfMissing = true)
public class LocalMailSink implements TransactionalMail {
    private final Map<String, String> links = new ConcurrentHashMap<>();

    public LocalMailSink(Environment environment) {
        // Local mode is an intentional development/test outbox, never a production fallback.
        if (java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"))
                || environment.getProperty("hamco.auth.production", Boolean.class, false))
            throw new IllegalStateException("Production requires hamco.mail.mode=mailjet and configured Mailjet credentials");
    }

    @Override public void send(String email, String subject, String link) {
        links.put(email, link);
    }

    public String latestLink(String email) { return links.get(email); }
}
