package com.cdwater.ridego.web;

import com.cdwater.ridego.contracts.BadRequestException;
import com.cdwater.ridego.contracts.ConflictException;
import com.cdwater.ridego.contracts.ForbiddenException;
import com.cdwater.ridego.contracts.NotFoundException;
import com.cdwater.ridego.contracts.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppExceptionHandlerTest {

    private final AppExceptionHandler handler = new AppExceptionHandler();

    @Test
    void mapsEveryKindToItsStatus() {
        assertEquals(404, handler.handle(new NotFoundException()).getStatusCode().value());
        assertEquals(403, handler.handle(new ForbiddenException()).getStatusCode().value());
        assertEquals(401, handler.handle(new UnauthorizedException()).getStatusCode().value());
        assertEquals(400, handler.handle(new BadRequestException()).getStatusCode().value());
        assertEquals(409, handler.handle(new ConflictException()).getStatusCode().value());
    }
}
