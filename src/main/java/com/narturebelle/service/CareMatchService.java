package com.narturebelle.service;

import com.narturebelle.dto.CareMatchesResponse;
import com.narturebelle.dto.CareMatchesResponse.CareMatch;
import com.narturebelle.dto.ProviderResponse;
import com.narturebelle.dto.SlotResponse;
import com.narturebelle.exception.MatchProfileIncompleteException;
import com.narturebelle.model.Modality;
import com.narturebelle.model.OnboardingIntake;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.ServiceOffering;
import com.narturebelle.repository.OnboardingIntakeRepository;
import com.narturebelle.repository.ProviderRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Matches the signed-in user to providers of one professional type (product-owner rule, 2026-10-30):
 * the provider must speak one of her languages and have an open future slot. In-person also needs the
 * same city; online works from any city. Her preferred modality from the intake applies unless the
 * caller overrides it; none means either. No match is an empty list, never a fallback to someone else.
 */
@Service
public class CareMatchService {

    private static final int NEXT_SLOTS = 3;
    private static final List<ProviderStatus> LISTED = List.of(ProviderStatus.VERIFIED, ProviderStatus.ACTIVE);

    private final OnboardingIntakeRepository onboardingIntakeRepository;
    private final ProviderRepository providerRepository;
    private final ProviderService providerService;

    public CareMatchService(OnboardingIntakeRepository onboardingIntakeRepository,
                            ProviderRepository providerRepository,
                            ProviderService providerService) {
        this.onboardingIntakeRepository = onboardingIntakeRepository;
        this.providerRepository = providerRepository;
        this.providerService = providerService;
    }

    public CareMatchesResponse match(Long userId, Role professionalType, Modality modalityOverride) {
        OnboardingIntake intake = onboardingIntakeRepository.findByUserId(userId).orElse(null);
        if (intake == null || intake.getLanguages().isEmpty()) {
            throw new MatchProfileIncompleteException(List.of("languages"));
        }
        List<String> languages = List.copyOf(intake.getLanguages());
        String city = intake.getCity();
        Modality modality = modalityOverride != null ? modalityOverride : intake.getPreferredModality();

        List<Match> matches = new ArrayList<>();
        for (Provider provider : providerRepository.findByStatusIn(LISTED)) {
            if (provider.getProfessionalType() != professionalType) continue;

            List<String> shared = provider.getLanguages().stream()
                    .filter(spoken -> languages.stream().anyMatch(spoken::equalsIgnoreCase))
                    .toList();
            if (shared.isEmpty()) continue;

            List<ServiceOffering> offerings = providerService.activeOfferingsFor(provider.getId());
            boolean online = offerings.stream().anyMatch(o -> o.getModality() == Modality.VIRTUAL);
            boolean inPersonHere = city != null && city.equalsIgnoreCase(provider.getCity())
                    && offerings.stream().anyMatch(o -> o.getModality() == Modality.PHYSICAL);
            boolean eligible = modality == Modality.VIRTUAL ? online
                    : modality == Modality.PHYSICAL ? inPersonHere
                    : online || inPersonHere;
            if (!eligible) continue;

            List<ProviderSlot> slots = providerService.openSlotsFor(provider.getId());
            if (slots.isEmpty()) continue;

            List<String> reasons = new ArrayList<>();
            reasons.add("Speaks " + String.join(", ", shared));
            if (modality != Modality.PHYSICAL && online) reasons.add("Offers online sessions");
            if (modality != Modality.VIRTUAL && inPersonHere) reasons.add("In-person in " + provider.getCity());

            List<SlotResponse> nextSlots = slots.stream().limit(NEXT_SLOTS).map(SlotResponse::from).toList();
            matches.add(new Match(slots.get(0), new CareMatch(ProviderResponse.from(provider, offerings), reasons, nextSlots)));
        }

        List<CareMatch> sorted = matches.stream()
                .sorted(Comparator.comparing(m -> m.firstSlot().getStartAt()))
                .map(Match::match)
                .toList();
        return CareMatchesResponse.of(languages, city, modality, sorted);
    }

    private record Match(ProviderSlot firstSlot, CareMatch match) {}
}
