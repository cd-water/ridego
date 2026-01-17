package com.cdwater.ridego.contracts;

public class UnauthorizedException extends AppException {

    public UnauthorizedException() {
        this("unauthorized");
    }

    public UnauthorizedException(String message) {
        super(ErrorKind.UNAUTHORIZED, message);
    }
}
