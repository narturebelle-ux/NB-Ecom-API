package com.narturebelle.service;

import com.narturebelle.dto.DashboardResponse;
import com.narturebelle.dto.JourneyResponse;
import com.narturebelle.dto.NextActionResponse;
import com.narturebelle.model.CareProtocolStep;
import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.model.User;
import com.narturebelle.repository.CareProtocolStepRepository;
import com.narturebelle.repository.JourneyRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class DashboardService {

    private final JourneyRepository journeyRepository;
    private final CareProtocolStepRepository careProtocolStepRepository;
    private final ConsentService consentService;

    public DashboardService(JourneyRepository journeyRepository, CareProtocolStepRepository careProtocolStepRepository,
                             ConsentService consentService) {
        this.journeyRepository = journeyRepository;
        this.careProtocolStepRepository = careProtocolStepRepository;
        this.consentService = consentService;
    }

    public DashboardResponse build(User user) {
        List<String> pending = consentService.pendingConsentTypes(user.getId());
        Optional<Journey> activeJourney = journeyRepository.findByUserIdAndStatusNot(user.getId(), JourneyStatus.CLOSED);

        if (activeJourney.isEmpty()) {
            return DashboardResponse.noJourney(user, pending);
        }

        Journey journey = activeJourney.get();
        int currentRank = windowRank(journey.getStatus(),
                GestationalMath.trimester(journey.getStatus(), journey.getExpectedDeliveryDate()));

        List<NextActionResponse> nextActions = careProtocolStepRepository.findAll().stream()
                .filter(step -> step.getJourneyStatus() == null || windowRank(step.getJourneyStatus(), step.getTrimester()) >= currentRank)
                .sorted(Comparator
                        .comparingInt((CareProtocolStep s) -> s.getJourneyStatus() == null ? Integer.MAX_VALUE : windowRank(s.getJourneyStatus(), s.getTrimester()))
                        .thenComparingInt(CareProtocolStep::getSortOrder))
                .map(NextActionResponse::from)
                .toList();

        return DashboardResponse.withJourney(user, pending, JourneyResponse.from(journey), nextActions);
    }

    /**
     * Lower rank = earlier in the lifecycle: PREGNANCY trimester 1/2/3 -> 1/2/3, then PRE_DELIVERY (4),
     * DELIVERY_COMPLETED (5), POSTPARTUM (6), CHILDCARE_0_7 (7) — matches JourneyStatus's own declared
     * order. "Current and later windows only" (Workflow D) = steps whose rank >= the journey's rank.
     * A PREGNANCY journey with no EDD (so trimester is unknown) defaults to trimester 1: it's a
     * brand-new journey, not a late-enrollment case, so showing the full roadmap from the start is
     * correct — there's no earlier window being hidden.
     */
    private int windowRank(JourneyStatus status, Integer trimester) {
        if (status == JourneyStatus.PREGNANCY) {
            return trimester == null ? 1 : trimester;
        }
        return 3 + status.ordinal();
    }
}
