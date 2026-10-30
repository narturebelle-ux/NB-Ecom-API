package com.narturebelle.service;

import com.narturebelle.dto.CreateHospitalReferralRequest;
import com.narturebelle.exception.HospitalNotFoundException;
import com.narturebelle.exception.HospitalReferralNotFoundException;
import com.narturebelle.model.HospitalPartner;
import com.narturebelle.model.HospitalReferral;
import com.narturebelle.model.ReferralStatus;
import com.narturebelle.repository.HospitalPartnerRepository;
import com.narturebelle.repository.HospitalReferralRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class HospitalReferralService {

    /** No 0/O/1/I -- the code gets read out over the phone at a hospital front desk. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final HospitalReferralRepository hospitalReferralRepository;
    private final HospitalPartnerRepository hospitalPartnerRepository;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public HospitalReferralService(HospitalReferralRepository hospitalReferralRepository,
                                   HospitalPartnerRepository hospitalPartnerRepository,
                                   AuditService auditService) {
        this.hospitalReferralRepository = hospitalReferralRepository;
        this.hospitalPartnerRepository = hospitalPartnerRepository;
        this.auditService = auditService;
    }

    /** Idempotent: an open (REQUESTED) referral to the same hospital is returned as-is rather than duplicated. */
    @Transactional
    public HospitalReferral createReferral(Long userId, CreateHospitalReferralRequest req) {
        HospitalPartner hospital = hospitalPartnerRepository.findById(req.getHospitalId())
                .orElseThrow(() -> new HospitalNotFoundException("Hospital " + req.getHospitalId() + " not found."));

        Optional<HospitalReferral> open = hospitalReferralRepository
                .findFirstByUserIdAndHospitalIdAndStatus(userId, hospital.getId(), ReferralStatus.REQUESTED);
        if (open.isPresent()) {
            return open.get();
        }

        HospitalReferral referral = new HospitalReferral();
        referral.setUserId(userId);
        referral.setHospitalId(hospital.getId());
        referral.setCode(newUniqueCode());
        referral.setStatus(ReferralStatus.REQUESTED);
        referral.setNote(req.getNote());
        HospitalReferral saved = hospitalReferralRepository.save(referral);
        // The note can carry a phone number -- never copied into the audit row.
        auditService.record("HOSPITAL_REFERRAL_CREATED", "HOSPITAL_REFERRAL", String.valueOf(saved.getId()),
                "User requested a hospital referral", Map.of("hospitalId", hospital.getId()));
        return saved;
    }

    public List<HospitalReferral> listForUser(Long userId) {
        return hospitalReferralRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<HospitalReferral> listAll() {
        return hospitalReferralRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Idempotent: confirming an already-confirmed referral keeps the original confirmedAt. */
    @Transactional
    public HospitalReferral confirm(Long referralId) {
        HospitalReferral referral = hospitalReferralRepository.findById(referralId)
                .orElseThrow(() -> new HospitalReferralNotFoundException("Referral " + referralId + " not found."));
        if (referral.getStatus() == ReferralStatus.CONFIRMED) {
            return referral;
        }
        referral.setStatus(ReferralStatus.CONFIRMED);
        // Truncated to the column's precision so this response matches every later read of the row.
        referral.setConfirmedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        HospitalReferral saved = hospitalReferralRepository.save(referral);
        auditService.record("HOSPITAL_REFERRAL_CONFIRMED", "HOSPITAL_REFERRAL", String.valueOf(saved.getId()),
                "Admin confirmed a hospital referral", Map.of("hospitalId", saved.getHospitalId()));
        return saved;
    }

    public String hospitalName(Long hospitalId) {
        return hospitalPartnerRepository.findById(hospitalId).map(HospitalPartner::getName).orElse(null);
    }

    private String newUniqueCode() {
        while (true) {
            StringBuilder code = new StringBuilder("NB-");
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            if (!hospitalReferralRepository.existsByCode(code.toString())) {
                return code.toString();
            }
        }
    }
}
