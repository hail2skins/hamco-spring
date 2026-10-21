package com.hamco.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Explicit production adapter; failed delivery is an error rather than a silent account lockout. */
@Component
@ConditionalOnProperty(name = "hamco.mail.mode", havingValue = "mailjet")
public class MailjetMail implements TransactionalMail {
    private final String key, secret, from, fromName;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public MailjetMail(@Value("${MAILJET_API_KEY:}") String key,
                       @Value("${MAILJET_API_SECRET:}") String secret,
                       @Value("${DEFAULT_FROM_EMAIL:}") String from,
                       @Value("${DEFAULT_FROM_NAME:HAMCO}") String fromName, ObjectMapper mapper) {
        this(key, secret, from, fromName, mapper, HttpClient.newHttpClient());
    }

    // Package-visible constructor allows tests to inspect requests through a fake transport.
    MailjetMail(String key, String secret, String from, String fromName, ObjectMapper mapper, HttpClient client) {
        if (key.isBlank() || secret.isBlank() || from.isBlank())
            throw new IllegalStateException("Mailjet credentials and sender are required in mailjet mode");
        this.key = key; this.secret = secret; this.from = from; this.fromName = fromName; this.mapper = mapper;
        this.client = client;
    }

    @Override public void send(String email, String subject, String link) {
        try {
            // Jackson escapes user-controlled addresses and links; never concatenate JSON manually.
            var payload = java.util.Map.of("Messages", java.util.List.of(java.util.Map.of(
                    "From", java.util.Map.of("Email", from, "Name", fromName),
                    "To", java.util.List.of(java.util.Map.of("Email", email)),
                    "Subject", subject, "TextPart", "HAMCO: " + link)));
            String credentials = Base64.getEncoder().encodeToString((key + ":" + secret).getBytes(StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(URI.create("https://api.mailjet.com/v3.1/send"))
                    .header("Authorization", "Basic " + credentials).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("Mailjet delivery failed: HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Mailjet delivery interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Mailjet delivery failed", e);
        }
    }
}
