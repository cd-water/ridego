package com.cdwater.ridego.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtCodecTest {

    private static final String SECRET = "0123456789012345678901234567890123456789";

    private final JwtCodec codec = new JwtCodec(SECRET, Duration.ofHours(1));

    @Test
    void issueThenVerifyReturnsPrincipal() {
        String token = codec.issue("user-1", "DRIVER", "driver-1");

        Optional<Principal> principal = codec.verify(token);
        assertTrue(principal.isPresent());
        assertEquals(new Principal("user-1", "DRIVER", "driver-1"), principal.get());
    }

    @Test
    void riderTokenHasNoDriverId() {
        String token = codec.issue("user-2", "RIDER", null);

        Optional<Principal> principal = codec.verify(token);
        assertTrue(principal.isPresent());
        assertEquals("RIDER", principal.get().role());
        assertEquals(null, principal.get().driverId());
    }

    @Test
    void differentSecretCannotVerify() {
        JwtCodec other = new JwtCodec("abcdefghijklmnopqrstuvwxyz0123456789", Duration.ofHours(1));
        String token = codec.issue("user-1", "RIDER", null);

        assertFalse(other.verify(token).isPresent());
    }

    @Test
    void garbageTokenIsRejected() {
        assertFalse(codec.verify("not-a-token").isPresent());
    }
}
