package com.narturebelle.internal;

import com.narturebelle.mail.OutgoingEmailDispatcher;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.service.ConsentService;
import com.narturebelle.service.VideoRetentionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** What the other NartureBelle services need from this API. Guarded by InternalTokenFilter. */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    public record Identity(Long userId, Set<String> roles) {}

    /** firstName lets an email greet the person by name; it may be null. */
    public record Contact(String email, String firstName) {}

    public record ConsentStatus(List<String> pending) {}

    private final UserRepository userRepository;
    private final OutgoingEmailDispatcher outgoingEmailDispatcher;
    private final ConsentService consentService;
    private final VideoRetentionService videoRetentionService;
    private final boolean schedulingEnabled;

    public InternalController(UserRepository userRepository, OutgoingEmailDispatcher outgoingEmailDispatcher,
                              ConsentService consentService, VideoRetentionService videoRetentionService,
                              @Value("${narturebelle.scheduling.enabled:true}") boolean schedulingEnabled) {
        this.userRepository = userRepository;
        this.outgoingEmailDispatcher = outgoingEmailDispatcher;
        this.consentService = consentService;
        this.videoRetentionService = videoRetentionService;
        this.schedulingEnabled = schedulingEnabled;
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
        return userRepository.findById(id).filter(user -> user.getEmail() != null)
                .map(user -> ResponseEntity.ok(new Contact(user.getEmail(), user.getFirstName())))
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

    /**
     * For the external scheduler (D18): the daily video job (F9-S19), in case the container slept through
     * 09:00. Does nothing where SCHEDULING_ENABLED is off, and nothing if another copy is running it.
     */
    @PostMapping("/jobs/video-retention")
    public Map<String, Object> runVideoRetention() {
        if (!schedulingEnabled) {
            return Map.of("ran", false, "reason", "SCHEDULING_DISABLED");
        }
        return videoRetentionService.runIfFree(LocalDateTime.now())
                .<Map<String, Object>>map(result -> Map.of("ran", true, "checked", result.checked(),
                        "warningsSent", result.warningsSent(), "trashed", result.trashed(), "purged", result.purged()))
                .orElse(Map.of("ran", false, "reason", "ALREADY_RUNNING"));
    }
}
