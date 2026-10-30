package com.narturebelle.service;

import com.narturebelle.exception.InputValidationException;
import com.narturebelle.dto.CreateCredentialRequest;
import com.narturebelle.dto.CreateProviderRequest;
import com.narturebelle.dto.CreateServiceOfferingRequest;
import com.narturebelle.dto.CreateSlotsRequest;
import com.narturebelle.exception.InvalidProviderStatusTransitionException;
import com.narturebelle.exception.ProviderNotFoundException;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.Modality;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderCredential;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.ServiceOffering;
import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.User;
import com.narturebelle.repository.ProviderCredentialRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.ServiceOfferingRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ProviderService {

    /**
     * Legal updateStatus() transitions. VERIFIED is never a valid target here — only /verify grants
     * it, since that's the one place the Role-grant side effect happens. A provider in the
     * already-verified tier (ACTIVE/PAUSED/SUSPENDED) can move directly between those three without
     * going back through VERIFIED, since the Role was already granted once and is never re-granted.
     */
    private static final Map<ProviderStatus, Set<ProviderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ProviderStatus.class);
    static {
        ALLOWED_TRANSITIONS.put(ProviderStatus.INVITED, EnumSet.of(ProviderStatus.DOCUMENTS_PENDING, ProviderStatus.DEACTIVATED));
        ALLOWED_TRANSITIONS.put(ProviderStatus.DOCUMENTS_PENDING, EnumSet.of(ProviderStatus.UNDER_REVIEW, ProviderStatus.INVITED, ProviderStatus.DEACTIVATED));
        ALLOWED_TRANSITIONS.put(ProviderStatus.UNDER_REVIEW, EnumSet.of(ProviderStatus.DOCUMENTS_PENDING, ProviderStatus.DEACTIVATED));
        ALLOWED_TRANSITIONS.put(ProviderStatus.VERIFIED, EnumSet.of(ProviderStatus.ACTIVE, ProviderStatus.PAUSED, ProviderStatus.SUSPENDED, ProviderStatus.DEACTIVATED));
        ALLOWED_TRANSITIONS.put(ProviderStatus.ACTIVE, EnumSet.of(ProviderStatus.PAUSED, ProviderStatus.SUSPENDED, ProviderStatus.DEACTIVATED));
        ALLOWED_TRANSITIONS.put(ProviderStatus.PAUSED, EnumSet.of(ProviderStatus.ACTIVE, ProviderStatus.SUSPENDED, ProviderStatus.DEACTIVATED));
        ALLOWED_TRANSITIONS.put(ProviderStatus.SUSPENDED, EnumSet.of(ProviderStatus.ACTIVE, ProviderStatus.PAUSED, ProviderStatus.DEACTIVATED));
        ALLOWED_TRANSITIONS.put(ProviderStatus.DEACTIVATED, EnumSet.noneOf(ProviderStatus.class));
    }

    private static final List<ProviderStatus> PUBLICLY_LISTED = List.of(ProviderStatus.VERIFIED, ProviderStatus.ACTIVE);

    private final ProviderRepository providerRepository;
    private final ProviderCredentialRepository providerCredentialRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ProviderSlotRepository providerSlotRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProviderService(ProviderRepository providerRepository, ProviderCredentialRepository providerCredentialRepository,
                            ServiceOfferingRepository serviceOfferingRepository, ProviderSlotRepository providerSlotRepository,
                            UserRepository userRepository, AuditService auditService) {
        this.providerRepository = providerRepository;
        this.providerCredentialRepository = providerCredentialRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.providerSlotRepository = providerSlotRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public Provider createProvider(CreateProviderRequest req) {
        userRepository.findById(req.getUserId())
                .orElseThrow(() -> new UserProfileNotFoundException("No user with id " + req.getUserId()));

        Provider provider = new Provider();
        provider.setUserId(req.getUserId());
        provider.setProfessionalType(req.getProfessionalType());
        provider.setDisplayName(req.getDisplayName());
        provider.setBio(req.getBio());
        provider.setCity(req.getCity());
        if (req.getLanguages() != null) {
            provider.setLanguages(req.getLanguages());
        }
        provider.setStatus(ProviderStatus.INVITED);
        Provider saved = providerRepository.save(provider);

        auditService.record("PROVIDER_CREATED", "Provider", String.valueOf(saved.getId()), null,
                Map.of("userId", saved.getUserId(), "professionalType", saved.getProfessionalType().name()));

        return saved;
    }

    /** Requires current status UNDER_REVIEW. Grants the professional Role onto the linked User if not already present. */
    public Provider verify(Long providerId, String reason) {
        Provider provider = findOrThrow(providerId);
        if (provider.getStatus() != ProviderStatus.UNDER_REVIEW) {
            throw new InvalidProviderStatusTransitionException(
                    "Provider must be UNDER_REVIEW to verify; current status is " + provider.getStatus());
        }
        provider.setStatus(ProviderStatus.VERIFIED);
        Provider saved = providerRepository.save(provider);

        auditService.record("PROVIDER_VERIFIED", "Provider", String.valueOf(saved.getId()), reason, null);
        grantProfessionalRoleIfAbsent(saved);

        return saved;
    }

    public Provider updateStatus(Long providerId, ProviderStatus newStatus, String reason) {
        Provider provider = findOrThrow(providerId);
        ProviderStatus current = provider.getStatus();
        Set<ProviderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (newStatus == ProviderStatus.VERIFIED || !allowed.contains(newStatus)) {
            throw new InvalidProviderStatusTransitionException(
                    "Cannot transition from " + current + " to " + newStatus
                            + (newStatus == ProviderStatus.VERIFIED ? " via this endpoint — use /verify instead." : "."));
        }
        provider.setStatus(newStatus);
        Provider saved = providerRepository.save(provider);

        auditService.record("PROVIDER_STATUS_CHANGED", "Provider", String.valueOf(saved.getId()), reason,
                Map.of("from", current.name(), "to", newStatus.name()));

        return saved;
    }

    private void grantProfessionalRoleIfAbsent(Provider provider) {
        User user = userRepository.findById(provider.getUserId())
                .orElseThrow(() -> new IllegalStateException("Provider references a missing User: " + provider.getUserId()));
        if (!user.getRoles().contains(provider.getProfessionalType())) {
            Set<Role> updatedRoles = new java.util.HashSet<>(user.getRoles());
            updatedRoles.add(provider.getProfessionalType());
            user.setRoles(updatedRoles);
            userRepository.save(user);
            auditService.record("ROLE_GRANTED", "User", String.valueOf(user.getId()), "Provider verified",
                    Map.of("role", provider.getProfessionalType().name()));
        }
    }

    /** VERIFIED + ACTIVE only, with simple equality filters applied in Java — fine at this scale. */
    public List<Provider> listPublic(Role professionalType, String language, String city, Modality modality) {
        List<ServiceOffering> modalityMatches = modality != null
                ? serviceOfferingRepository.findAll().stream().filter(s -> s.getModality() == modality && s.isActive()).toList()
                : null;
        Set<Long> providerIdsWithModality = modalityMatches != null
                ? modalityMatches.stream().map(ServiceOffering::getProviderId).collect(java.util.stream.Collectors.toSet())
                : null;

        return providerRepository.findByStatusIn(PUBLICLY_LISTED).stream()
                .filter(p -> professionalType == null || p.getProfessionalType() == professionalType)
                .filter(p -> language == null || p.getLanguages().stream().anyMatch(l -> l.equalsIgnoreCase(language)))
                .filter(p -> city == null || city.equalsIgnoreCase(p.getCity()))
                .filter(p -> providerIdsWithModality == null || providerIdsWithModality.contains(p.getId()))
                .toList();
    }

    public Optional<Provider> find(Long id) {
        return providerRepository.findById(id);
    }

    /** Admin queue view: every provider regardless of status. No pagination precedent exists elsewhere in this codebase. */
    public List<Provider> listAll() {
        return providerRepository.findAll();
    }

    public Optional<Provider> findByUserId(Long userId) {
        return providerRepository.findByUserId(userId);
    }

    public List<ProviderCredential> credentialsFor(Long providerId) {
        return providerCredentialRepository.findByProviderId(providerId);
    }

    /** Public-facing — active offerings only, so the UI has a real serviceOfferingId to book against. */
    public List<ServiceOffering> activeOfferingsFor(Long providerId) {
        return serviceOfferingRepository.findByProviderId(providerId).stream()
                .filter(ServiceOffering::isActive)
                .toList();
    }

    public ProviderCredential addCredential(Long providerId, CreateCredentialRequest req) {
        findOrThrow(providerId);
        LocalDate today = LocalDate.now();
        if (req.getExpiresAt() != null) {
            if (!req.getExpiresAt().isAfter(today)) {
                throw new InputValidationException("expiresAt", "This credential has already expired.");
            }
            if (req.getIssuedAt() != null && !req.getExpiresAt().isAfter(req.getIssuedAt())) {
                throw new InputValidationException("expiresAt", "Expiry date must be after the issue date.");
            }
        }
        ProviderCredential credential = new ProviderCredential();
        credential.setProviderId(providerId);
        credential.setCredentialType(req.getCredentialType());
        credential.setDocumentReference(req.getDocumentReference());
        credential.setIssuedAt(req.getIssuedAt());
        credential.setExpiresAt(req.getExpiresAt());
        return providerCredentialRepository.save(credential);
    }

    public ServiceOffering addServiceOffering(Long providerId, CreateServiceOfferingRequest req) {
        findOrThrow(providerId);
        ServiceOffering offering = new ServiceOffering();
        offering.setProviderId(providerId);
        offering.setName(req.getName());
        offering.setDescription(req.getDescription());
        offering.setModality(req.getModality());
        offering.setDurationMinutes(req.getDurationMinutes());
        offering.setActive(true);
        return serviceOfferingRepository.save(offering);
    }

    public List<ProviderSlot> addSlots(Long providerId, CreateSlotsRequest req) {
        findOrThrow(providerId);
        List<LocalDateTime[]> checked = new java.util.ArrayList<>();
        for (CreateSlotsRequest.SlotWindow window : req.getSlots()) {
            SlotRules.check(window.getStartAt(), window.getEndAt(), checked);
            if (providerSlotRepository.existsByProviderIdAndStartAtBeforeAndEndAtAfter(providerId, window.getEndAt(), window.getStartAt())) {
                throw new InputValidationException("slots", "That time overlaps a slot this provider already has.");
            }
        }
        return req.getSlots().stream().map(window -> {
            ProviderSlot slot = new ProviderSlot();
            slot.setProviderId(providerId);
            slot.setStartAt(window.getStartAt());
            slot.setEndAt(window.getEndAt());
            slot.setStatus(SlotStatus.OPEN);
            return providerSlotRepository.save(slot);
        }).toList();
    }

    public List<ProviderSlot> openSlotsFor(Long providerId) {
        return providerSlotRepository.findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(
                providerId, SlotStatus.OPEN, LocalDateTime.now());
    }

    private Provider findOrThrow(Long providerId) {
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException("No provider with id " + providerId));
    }
}
