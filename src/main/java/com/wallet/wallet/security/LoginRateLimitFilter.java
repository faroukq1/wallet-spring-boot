package com.wallet.wallet.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory token-bucket rate limiter for POST /auth/login (the
 * brute-force sensitive endpoint). Loopback clients are exempt so local
 * tests and tooling are not throttled.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    private final int maxAttemptsPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(
            @Value("${app.rate-limit.login-per-minute:10}") int maxAttemptsPerMinute) {
        this.maxAttemptsPerMinute = maxAttemptsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean isLoginAttempt = "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().equals("/auth/login");

        if (!isLoginAttempt || maxAttemptsPerMinute <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = clientKey(request);
        if (isLoopback(clientKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Coarse guard against unbounded map growth (single-instance demo).
        if (buckets.size() > 10_000) {
            buckets.clear();
        }

        Bucket bucket = buckets.computeIfAbsent(clientKey, key -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(maxAttemptsPerMinute)
                        .refillGreedy(maxAttemptsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build());

        if (!bucket.tryConsume(1)) {
            log.warn("Login rate limit exceeded for client {}", clientKey);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                    "{\"timestamp\":\"%s\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many login attempts. Try again later.\"}",
                    LocalDateTime.now()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address);
    }
}
