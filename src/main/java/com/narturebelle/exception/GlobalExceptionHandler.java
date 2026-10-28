package com.narturebelle.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleEmailNotVerified(EmailNotVerifiedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "EMAIL_NOT_VERIFIED", "message", e.getMessage()));
    }

    @ExceptionHandler(UserProfileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserProfileNotFound(UserProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "USER_NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(PhoneInUseException.class)
    public ResponseEntity<Map<String, String>> handlePhoneInUse(PhoneInUseException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "PHONE_IN_USE", "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("code", "VALIDATION_FAILED");
        body.put("message", "One or more fields are invalid.");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Spring Security's AccessDeniedException must NOT be swallowed by the catch-all below — it needs
     * to keep propagating to ExceptionTranslationFilter so RestAccessDeniedHandler's 403 FORBIDDEN
     * shape still fires (AuthorizationService.requireSelfOrAdmin relies on this). Since this handler
     * matches more specifically than the Exception catch-all, Spring resolves it here first; re-throwing
     * sends it back up past MVC's exception resolution exactly as if no handler had matched at all.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    /**
     * No route matched at all — a plain 404, not our 500 catch-all (more specific, so it wins).
     * Spring 6.1+ throws NoResourceFoundException for this in practice; NoHandlerFoundException is
     * kept too since its triggering condition (spring.mvc.throw-exception-if-no-handler-found) is a
     * config switch, not a Spring-version guarantee.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Void> handleNoHandlerFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "INTERNAL_ERROR", "message", "Something went wrong. Please try again."));
    }
}
