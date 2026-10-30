package com.narturebelle.controller;

import com.narturebelle.dto.AuthLoginRequest;
import com.narturebelle.dto.AuthResponse;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Pre-Firebase mock login/OTP endpoints, kept only for local/dev convenience.
 * Does not exist as a bean at all in prod/stage (safer than a runtime gate) —
 * real auth is Firebase-only; there is no app-issued session token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Profile("!prod & !stage")
public class LegacyAuthController {

    private final UserRepository userRepository;

    public LegacyAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "OTP sent successfully to " + phone + ". (Demo verification code: 123456)");
        response.put("simulatedOtp", "123456");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        User user;

        if ("GOOGLE".equalsIgnoreCase(request.getProvider()) && request.getEmail() != null) {
            user = userRepository.findByEmail(request.getEmail()).orElse(new User());
            user.setEmail(request.getEmail());
            user.setName(request.getName() != null ? request.getName() : "Expecting Mother");
            user.setAuthProvider("GOOGLE");
        } else if ("PHONE".equalsIgnoreCase(request.getProvider()) && request.getPhone() != null) {
            user = userRepository.findByPhone(request.getPhone()).orElse(new User());
            user.setPhone(request.getPhone());
            user.setName(request.getName() != null ? request.getName() : "Mother (" + request.getPhone() + ")");
            user.setAuthProvider("PHONE");
        } else {
            user = new User();
            user.setName(request.getName() != null ? request.getName() : "Valued Member");
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            user.setAuthProvider(request.getProvider() != null ? request.getProvider() : "LOCAL");
        }

        if (request.getStage() != null) {
            user.setStage(request.getStage());
        }
        if (request.getDueDate() != null) {
            user.setDueDate(request.getDueDate());
        }

        User savedUser = userRepository.save(user);

        // No app-issued session token — this endpoint is dev-only and Firebase is the real auth.
        return ResponseEntity.ok(new AuthResponse(true, null, "Login successful (dev mock — not valid outside local/dev)", savedUser));
    }
}
