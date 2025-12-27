package com.cdwater.ridego.dispatch.web;

import com.cdwater.ridego.dispatch.lifecycle.OfferConflictException;
import com.cdwater.ridego.dispatch.lifecycle.OfferForbiddenException;
import com.cdwater.ridego.dispatch.lifecycle.OfferNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({NotFoundException.class, OfferNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound() {
        return Map.of("error", "offer not found");
    }

    @ExceptionHandler({ConflictException.class, OfferConflictException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(Exception e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, OfferForbiddenException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> forbidden() {
        return Map.of("error", "forbidden");
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized() {
        return Map.of("error", "unauthorized");
    }
}
