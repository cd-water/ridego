package com.cdwater.ridego.contracts;

public class AppException extends RuntimeException {

    private final ErrorKind kind;

    public AppException(ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ErrorKind kind() {
        return kind;
    }
}
