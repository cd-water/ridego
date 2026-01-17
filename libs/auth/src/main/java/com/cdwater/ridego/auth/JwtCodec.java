package com.cdwater.ridego.auth;

import com.cdwater.ridego.contracts.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

public class JwtCodec {

    private final SecretKey key;
    private final Duration ttl;

    public JwtCodec(String secret, Duration ttl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = ttl;
    }

    public String issue(String userId, String role, String driverId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId)
                .claim(JwtClaims.ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (driverId != null) {
            builder.claim(JwtClaims.DRIVER_ID, driverId);
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    public Optional<Principal> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userId = claims.getSubject();
            String role = claims.get(JwtClaims.ROLE, String.class);
            if (userId == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new Principal(
                    userId, role, claims.get(JwtClaims.DRIVER_ID, String.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
