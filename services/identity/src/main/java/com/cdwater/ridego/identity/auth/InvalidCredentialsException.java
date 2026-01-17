package com.cdwater.ridego.identity.auth;

import com.cdwater.ridego.contracts.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("invalid credentials");
    }
}
