package com.cdwater.ridego.contracts;

public class BadRequestException extends AppException {

    public BadRequestException() {
        this("bad request");
    }

    public BadRequestException(String message) {
        super(ErrorKind.BAD_REQUEST, message);
    }
}
