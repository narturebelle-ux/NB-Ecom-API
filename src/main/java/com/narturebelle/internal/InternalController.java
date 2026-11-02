package com.narturebelle.internal;

import com.narturebelle.mail.OutgoingEmailDispatcher;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.service.ConsentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** What the other NartureBelle services need from this API. Guarded by InternalTokenFilter. */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    public record Identity(Long userId, Set<String> roles) {}

    public record Contact(String email) {}

    public record ConsentStatus(List<String> pending) {}

    private final UserRepository userRepository;
    private final OutgoingEmailDispatcher outgoingEmailDispatcher;
    private final ConsentService consentService;

    public InternalController(UserRepository userRepository, OutgoingEmailDispatcher outgoingEmailDispatcher,
                              ConsentService consentService) {
        this.userRepository = userRepository;
        this.outgoingEmailDispatcher = outgoingEmailDispatcher;
        this.consentService = consentService;
    }

    /** Sign-in for the other APIs: Firebase UID to user ID and roles. 404 means no profile yet. */
    @GetMapping("/users/by-firebase-uid/{uid}")
    public ResponseEntity<Identity> identity(@PathVariable String uid) {
        return userRepository.findByFirebaseUid(uid)
                .map(user -> ResponseEntity.ok(new Identity(user.getId(),
                        user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Asked at send time, so callers keep no copy of the address. */
    @GetMapping("/users/{id}/contact")
    public ResponseEntity<Contact> contact(@PathVariable Long id) {
        return userRepository.findById(id).map(User::getEmail)
                .map(email -> ResponseEntity.ok(new Contact(email)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** The consents this user still has to give, so other APIs can answer 403 CONSENT_REQUIRED with the same list. */
    @GetMapping("/users/{id}/consent-status")
    public ConsentStatus consentStatus(@PathVariable Long id) {
        return new ConsentStatus(consentService.pendingConsentTypes(id));
    }

    /** For the external scheduler (D18): sends what the background pool couldn't. */
    @PostMapping("/outgoing-emails/retry")
    public Map<String, Integer> retryOutgoingEmails() {
        return Map.of("tried", outgoingEmailDispatcher.retryDue());
    }
}
