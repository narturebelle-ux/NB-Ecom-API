package com.narturebelle.security;

import com.narturebelle.exception.ConsentRequiredException;
import com.narturebelle.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class ConsentCheckInterceptor implements HandlerInterceptor {

    private final AuthorizationService authorizationService;
    private final ConsentService consentService;

    public ConsentCheckInterceptor(AuthorizationService authorizationService, ConsentService consentService) {
        this.authorizationService = authorizationService;
        this.consentService = consentService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getMethodAnnotation(RequireConsent.class) == null) {
            return true;
        }
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        List<String> pending = consentService.pendingConsentTypes(principal.userId());
        if (!pending.isEmpty()) {
            throw new ConsentRequiredException(pending);
        }
        return true;
    }
}
