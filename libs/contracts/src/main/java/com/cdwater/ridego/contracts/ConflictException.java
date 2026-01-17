package com.cdwater.ridego.contracts;

public class ConflictException extends AppException {

    public ConflictException() {
        this("conflict");
    }

    public ConflictException(String message) {
        super(ErrorKind.CONFLICT, message);
    }
}
