package com.hamco.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fixed-window limiter keyed by the direct peer address (never an untrusted forwarded header). */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> ROUTES = Set.of("/api/auth/register", "/api/auth/login",
            "/api/auth/forgot-password", "/api/auth/reset-password");
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    @Value("${hamco.auth.rate-limit:5}") private int limit;
    @Value("${hamco.auth.rate-window-seconds:900}") private long seconds;
    private record Window(Instant started, int requests) {}

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && ROUTES.contains(request.getRequestURI())) {
            Instant now = Instant.now();
            String peer = request.getRemoteAddr();
            Window window = windows.compute(peer, (key, previous) ->
                    previous == null || previous.started().plusSeconds(seconds).isBefore(now)
                            ? new Window(now, 1) : new Window(previous.started(), previous.requests() + 1));
            if (window.requests() > limit) {
                response.setStatus(429);
                long retryAfter = Math.max(1, Duration.between(now, window.started().plusSeconds(seconds)).toSeconds());
                response.setHeader("Retry-After", Long.toString(retryAfter));
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests\","
                        + "\"message\":\"Rate limit exceeded. Please try again later.\","
                        + "\"retryAfter\":" + retryAfter + "}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
