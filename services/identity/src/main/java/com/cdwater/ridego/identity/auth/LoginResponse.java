package com.cdwater.ridego.identity.auth;

public record LoginResponse(String token, String userId, String role, String driverId) {
}
