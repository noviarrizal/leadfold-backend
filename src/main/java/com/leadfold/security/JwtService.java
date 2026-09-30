package com.leadfold.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

// Issues and verifies the JWT used for dashboard login sessions (NOT used for
// Zapier/external integrations — those use the static per-account API key
// instead, see ApiKeyAuthFilter).
@Service
public class JwtService {

    private static final long EXPIRATION_MILLIS = 30L * 24 * 60 * 60 * 1000; // 30 days

    private final SecretKey key;

    public JwtService(@Value("${leadfold.jwt-secret}") String secret) {
        // HS256 needs a key of at least 256 bits (32 chars) — see .env.example.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(EXPIRATION_MILLIS)))
                .signWith(key)
                .compact();
    }

    public UUID extractUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(subject);
    }
}
