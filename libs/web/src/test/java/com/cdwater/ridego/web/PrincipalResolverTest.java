package com.cdwater.ridego.web;

import com.cdwater.ridego.auth.Principal;
import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrincipalResolverTest {

    private final PrincipalResolver resolver = new PrincipalResolver();

    @Test
    void supportsOnlyPrincipalParameters() throws Exception {
        assertTrue(resolver.supportsParameter(principalParameter()));
        MethodParameter stringParameter = new MethodParameter(
                Handler.class.getDeclaredMethod("handleString", String.class), 0);
        assertFalse(resolver.supportsParameter(stringParameter));
    }

    @Test
    void resolvesHeadersIntoPrincipal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalHeaders.USER_ID, "user-1");
        request.addHeader(InternalHeaders.ROLE, "DRIVER");
        request.addHeader(InternalHeaders.DRIVER_ID, "driver-1");

        Object resolved = resolver.resolveArgument(
                principalParameter(), null, new ServletWebRequest(request), null);

        assertEquals(new Principal("user-1", "DRIVER", "driver-1"), resolved);
    }

    @Test
    void missingUserHeaderIsUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(UnauthorizedException.class, () -> resolver.resolveArgument(
                principalParameter(), null, new ServletWebRequest(request), null));
    }

    private MethodParameter principalParameter() throws Exception {
        return new MethodParameter(
                Handler.class.getDeclaredMethod("handle", Principal.class), 0);
    }

    static class Handler {
        void handle(Principal principal) {
        }

        void handleString(String value) {
        }
    }
}
