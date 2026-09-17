package com.wallet.wallet.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private final JwtService jwtService = new JwtService(SECRET, ONE_HOUR_MS);
    private final UserDetails alice = User.builder().username("alice").password("pw").roles("USER").build();
    private final UserDetails admin = User.builder().username("admin").password("pw").roles("ADMIN").build();

    @Test
    void generateToken_roundTripsUsernameAndRole() {
        String token = jwtService.generateToken(alice);

        assertEquals("alice", jwtService.extractUsername(token));
        assertEquals("ROLE_USER", jwtService.extractRole(token));
    }

    @Test
    void generateToken_assignsAdminRole() {
        String token = jwtService.generateToken(admin);

        assertEquals("ROLE_ADMIN", jwtService.extractRole(token));
    }

    @Test
    void token_isValidForItsOwner() {
        String token = jwtService.generateToken(alice);

        assertTrue(jwtService.isTokenValid(token, alice));
    }

    @Test
    void token_isInvalidForAnotherUser() {
        String token = jwtService.generateToken(alice);

        assertFalse(jwtService.isTokenValid(token, admin));
    }

    @Test
    void expiredToken_isInvalid() {
        JwtService alreadyExpired = new JwtService(SECRET, -1000L);
        String token = alreadyExpired.generateToken(alice);

        assertFalse(alreadyExpired.isTokenValid(token, alice));
    }

    @Test
    void tamperedToken_throwsOnExtract() {
        String token = jwtService.generateToken(alice);
        String[] parts = token.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        payload[0] ^= 0x01;
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "." + parts[2];

        assertThrows(JwtException.class, () -> jwtService.extractUsername(tampered));
    }
}
