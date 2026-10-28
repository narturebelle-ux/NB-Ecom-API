package com.narturebelle.service;

import com.narturebelle.dto.UpdateProfileRequest;
import com.narturebelle.exception.PhoneInUseException;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Shared by MeController and the deprecated UserController /me wrappers — same rules, one place. */
@Component
public class UserProfileService {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public UserProfileService(UserRepository userRepository, AuthorizationService authorizationService) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    public User loadSelfOrThrow() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        if (principal.userId() == null) {
            throw notCreatedYet();
        }
        return userRepository.findById(principal.userId()).orElseThrow(this::notCreatedYet);
    }

    public User applyProfileUpdate(User user, UpdateProfileRequest req) {
        if (req.getFirstName() != null && !req.getFirstName().isBlank()) {
            user.setFirstName(req.getFirstName().trim());
        }
        if (req.getLastName() != null) {
            user.setLastName(req.getLastName().trim());
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            String newPhone = req.getPhone().trim();
            Optional<User> existingPhone = userRepository.findByPhone(newPhone);
            if (existingPhone.isPresent() && !existingPhone.get().getId().equals(user.getId())) {
                throw new PhoneInUseException("Phone number already in use by another account.");
            }
            if (!newPhone.equals(user.getPhone())) {
                // Only Firebase phone verification (via /auth/firebase) sets this true again.
                user.setPhoneVerified(false);
            }
            user.setPhone(newPhone);
        }
        if (req.getStage() != null && !req.getStage().isBlank()) {
            try {
                user.setStage(PregnancyStage.valueOf(req.getStage().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // keep existing stage if invalid value sent
            }
        }
        if (req.getDueDate() != null) {
            user.setDueDate(req.getDueDate());
        }

        return userRepository.save(user);
    }

    private UserProfileNotFoundException notCreatedYet() {
        return new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
    }
}
