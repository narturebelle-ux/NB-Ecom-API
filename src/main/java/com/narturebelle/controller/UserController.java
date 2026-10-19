package com.narturebelle.controller;

import com.narturebelle.dto.UpdateProfileRequest;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** GET /api/v1/users/{id} — fetch profile */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProfile(@PathVariable Long id) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(opt.get());
    }

    /** PUT /api/v1/users/{id} — update profile fields */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProfile(@PathVariable Long id,
                                            @RequestBody UpdateProfileRequest req) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = opt.get();

        if (req.getFirstName() != null && !req.getFirstName().isBlank()) {
            user.setFirstName(req.getFirstName().trim());
        }
        if (req.getLastName() != null) {
            user.setLastName(req.getLastName().trim());
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            // check uniqueness before updating
            Optional<User> existingPhone = userRepository.findByPhone(req.getPhone());
            if (existingPhone.isPresent() && !existingPhone.get().getId().equals(id)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Phone number already in use by another account."));
            }
            user.setPhone(req.getPhone().trim());
        }
        if (req.getStage() != null && !req.getStage().isBlank()) {
            try {
                user.setStage(PregnancyStage.valueOf(req.getStage().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // keep existing stage if invalid value sent
            }
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Profile updated successfully.",
                "user", saved
        ));
    }
}
