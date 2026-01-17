package com.cdwater.ridego.web;

import com.cdwater.ridego.auth.Principal;
import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class PrincipalResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Principal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(InternalHeaders.USER_ID);
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return new Principal(
                userId,
                webRequest.getHeader(InternalHeaders.ROLE),
                webRequest.getHeader(InternalHeaders.DRIVER_ID));
    }
}
