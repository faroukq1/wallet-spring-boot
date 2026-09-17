package com.wallet.wallet.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    // Hardcoded users for the scope of the test.
    // In production, this would query a `users` table via UserRepository.
    // Passwords are BCrypt-hashed, matching what a real registration would store.
    private final Map<String, String> encodedPasswords = Map.of(
            "alice", new BCryptPasswordEncoder().encode("password"),
            "admin", new BCryptPasswordEncoder().encode("admin")
    );

    // Maps a login username to the id of the Account this user owns.
    // Kept alongside the hardcoded users for the scope of the test.
    private static final Map<String, Long> OWNER_IDS = Map.of(
            "alice", 1L,
            "admin", 2L
    );

    public Long getOwnerIdForUsername(String username) {
        return OWNER_IDS.get(username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String encodedPassword = encodedPasswords.get(username);
        if (encodedPassword == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        // Build a fresh instance per lookup: Spring Security erases credentials on
        // the principal after a successful authentication, so reusing a cached
        // User instance would break all subsequent logins for that user.
        return User.builder()
                .username(username)
                .password(encodedPassword)
                .roles(username.equals("admin") ? "ADMIN" : "USER")
                .build();
    }
}
