package com.cdwater.ridego.trip.web;

import com.cdwater.ridego.trip.lifecycle.TripBadRequestException;
import com.cdwater.ridego.trip.lifecycle.TripConflictException;
import com.cdwater.ridego.trip.lifecycle.TripForbiddenException;
import com.cdwater.ridego.trip.lifecycle.TripNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TripNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound() {
        return Map.of("error", "trip not found");
    }

    @ExceptionHandler({TripForbiddenException.class, ForbiddenException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> forbidden() {
        return Map.of("error", "forbidden");
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized() {
        return Map.of("error", "unauthorized");
    }

    @ExceptionHandler(TripBadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(TripBadRequestException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(TripConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(TripConflictException e) {
        return Map.of("error", e.getMessage());
    }
}
