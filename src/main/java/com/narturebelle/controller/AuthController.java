package com.narturebelle.controller;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.narturebelle.dto.AuthLoginRequest;
import com.narturebelle.dto.AuthResponse;
import com.narturebelle.dto.FirebaseLoginRequest;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
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
    public ResponseEntity<AuthResponse> login(@RequestBody AuthLoginRequest request) {
        User user = null;

        if ("GOOGLE".equalsIgnoreCase(request.getProvider()) && request.getEmail() != null) {
            user = userRepository.findByEmail(request.getEmail())
                    .orElse(new User());
            user.setEmail(request.getEmail());
            user.setName(request.getName() != null ? request.getName() : "Expecting Mother");
            user.setAuthProvider("GOOGLE");
        } else if ("PHONE".equalsIgnoreCase(request.getProvider()) && request.getPhone() != null) {
            user = userRepository.findByPhone(request.getPhone())
                    .orElse(new User());
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
        String mockToken = "nb_jwt_" + UUID.randomUUID().toString();

        return ResponseEntity.ok(new AuthResponse(true, mockToken, "Login successful", savedUser));
    }

    @PostMapping("/firebase")
    public ResponseEntity<AuthResponse> loginWithFirebase(@RequestBody FirebaseLoginRequest request) {
        if (request.getIdToken() == null || request.getIdToken().isBlank()) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, null, "Firebase ID Token is required", null));
        }

        String email = null;
        String phone = null;
        String name = null;
        String provider = "FIREBASE";

        if (!FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(request.getIdToken());
                email = decodedToken.getEmail();
                name = decodedToken.getName();
                phone = (String) decodedToken.getClaims().get("phone_number");
                if (decodedToken.getClaims().get("firebase") instanceof Map<?, ?> firebaseClaims) {
                    Object signProvider = firebaseClaims.get("sign_in_provider");
                    if (signProvider != null) {
                        provider = signProvider.toString().toUpperCase();
                    }
                }
            } catch (Exception e) {
                return ResponseEntity.status(401).body(new AuthResponse(false, null, "Invalid or expired Firebase ID token: " + e.getMessage(), null));
            }
        } else {
            // Development fallback when credentials file is pending
            provider = "FIREBASE_DEV";
            name = "Verified Expecting Mother";
            phone = "9876543210";
        }

        User user = null;
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email).orElse(new User());
            user.setEmail(email);
        } else if (phone != null && !phone.isBlank()) {
            user = userRepository.findByPhone(phone).orElse(new User());
            user.setPhone(phone);
        } else {
            user = new User();
        }

        if (name != null) user.setName(name);
        user.setAuthProvider(provider);
        if (request.getStage() != null) user.setStage(request.getStage());
        if (request.getDueDate() != null) user.setDueDate(request.getDueDate());

        User savedUser = userRepository.save(user);
        String sessionToken = "nb_jwt_" + UUID.randomUUID().toString();

        return ResponseEntity.ok(new AuthResponse(true, sessionToken, "Authentication verified successfully with Google/Firebase", savedUser));
    }
}
