package com.hamco.auth;

import com.hamco.apikeys.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Optional API-key identity after bearer validation; bearer claims always take precedence. */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final ApiKeyService keys;
    public ApiKeyAuthenticationFilter(ApiKeyService keys) { this.keys = keys; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String secret = request.getHeader("X-API-Key");
        var context = SecurityContextHolder.getContext();
        if (secret != null && context.getAuthentication() == null) {
            // A presented credential must fail closed, including on public
            // reads. The source short-circuits instead of silently treating
            // an invalid credential as an anonymous visitor.
            var authentication = secret.isBlank() ? java.util.Optional.<org.springframework.security.core.Authentication>empty()
                    : keys.authenticate(secret);
            if (authentication.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(secret.isBlank() ? "{\"error\":\"API key is empty.\"}"
                        : "{\"error\":\"Invalid or revoked API key.\"}");
                return;
            }
            // A fresh context avoids mutating an existing principal on pooled threads.
            var next = SecurityContextHolder.createEmptyContext();
            next.setAuthentication(authentication.get());
            SecurityContextHolder.setContext(next);
        }
        chain.doFilter(request, response);
    }
}
