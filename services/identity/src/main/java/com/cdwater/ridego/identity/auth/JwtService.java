package com.cdwater.ridego.identity.auth;

import com.cdwater.ridego.contracts.JwtClaims;
import com.cdwater.ridego.identity.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(JwtProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttlSeconds = properties.ttlSeconds();
    }

    public String issue(Account account) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(account.id().toString())
                .claim(JwtClaims.ROLE, account.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)));
        if (account.driverId() != null) {
            builder.claim(JwtClaims.DRIVER_ID, account.driverId());
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }
}
