package com.cdwater.ridego.contracts;

public class NotFoundException extends AppException {

    public NotFoundException() {
        this("not found");
    }

    public NotFoundException(String message) {
        super(ErrorKind.NOT_FOUND, message);
    }
}
