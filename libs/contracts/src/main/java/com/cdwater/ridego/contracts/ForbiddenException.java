package com.cdwater.ridego.contracts;

public class ForbiddenException extends AppException {

    public ForbiddenException() {
        this("forbidden");
    }

    public ForbiddenException(String message) {
        super(ErrorKind.FORBIDDEN, message);
    }
}
