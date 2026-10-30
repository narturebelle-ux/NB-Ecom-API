package com.narturebelle.controller;

import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Modality;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.ServiceOffering;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.User;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.OnboardingIntakeRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.ServiceOfferingRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers intake languages and GET /me/care/matches. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareMatchTest {

    private static final String TOKEN = "match-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private OnboardingIntakeRepository onboardingIntakeRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired private ProviderSlotRepository providerSlotRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private int providerCounter;

    @BeforeEach
    void setUp() throws Exception {
        providerSlotRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        providerRepository.deleteAll();
        onboardingIntakeRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-match");
        user.setEmail("match@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        Long userId = userRepository.save(user).getId();
        for (ConsentDocumentType type : ConsentDocumentType.values()) {
            Consent consent = new Consent();
            consent.setUserId(userId);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-match", "match@example.com", true, null, null));
    }

    private void intake(String body) throws Exception {
        mockMvc.perform(put("/api/v1/me/onboarding/intake").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private Long provider(Role type, String city, List<String> languages, List<Modality> modalities, Integer slotInDays) {
        User user = new User();
        user.setFirebaseUid("uid-match-provider-" + (++providerCounter));
        user.setRoles(Set.of(type));
        Long userId = userRepository.save(user).getId();

        Provider provider = new Provider();
        provider.setUserId(userId);
        provider.setProfessionalType(type);
        provider.setDisplayName("Provider " + providerCounter);
        provider.setCity(city);
        provider.setLanguages(new ArrayList<>(languages));
        provider.setStatus(ProviderStatus.ACTIVE);
        Long providerId = providerRepository.save(provider).getId();

        for (Modality modality : modalities) {
            ServiceOffering offering = new ServiceOffering();
            offering.setProviderId(providerId);
            offering.setName(modality + " consult");
            offering.setModality(modality);
            offering.setDurationMinutes(30);
            offering.setActive(true);
            serviceOfferingRepository.save(offering);
        }
        if (slotInDays != null) {
            ProviderSlot slot = new ProviderSlot();
            slot.setProviderId(providerId);
            slot.setStartAt(LocalDateTime.now().plusDays(slotInDays));
            slot.setEndAt(LocalDateTime.now().plusDays(slotInDays).plusMinutes(30));
            slot.setStatus(SlotStatus.OPEN);
            providerSlotRepository.save(slot);
        }
        return providerId;
    }

    @Test
    void intake_acceptsLanguagesList_andOldSingleLanguage() throws Exception {
        intake("{\"languages\":[\" Tamil \",\"english\",\"TAMIL\"],\"city\":\"Chennai\"}");
        mockMvc.perform(get("/api/v1/me/onboarding").header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.intake.languages[0]").value("Tamil"))
                .andExpect(jsonPath("$.intake.languages[1]").value("english"))
                .andExpect(jsonPath("$.intake.languages", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.intake.language").value("Tamil"));

        intake("{\"language\":\"Hindi\",\"city\":\"Delhi\"}");
        mockMvc.perform(get("/api/v1/me/onboarding").header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.intake.languages[0]").value("Hindi"))
                .andExpect(jsonPath("$.intake.languages", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void matches_requireSharedLanguageAndOpenSlot_inPersonOnlyInSameCity_earliestFirst() throws Exception {
        intake("{\"languages\":[\"Tamil\",\"English\"],\"city\":\"Chennai\"}");
        Long chennaiTamil = provider(Role.GYNECOLOGIST, "Chennai", List.of("Tamil"), List.of(Modality.VIRTUAL, Modality.PHYSICAL), 3);
        provider(Role.GYNECOLOGIST, "Mumbai", List.of("Hindi"), List.of(Modality.VIRTUAL), 1);             // no shared language
        provider(Role.GYNECOLOGIST, "Delhi", List.of("English"), List.of(Modality.PHYSICAL), 1);           // in person, other city
        provider(Role.GYNECOLOGIST, "Delhi", List.of("English"), List.of(Modality.VIRTUAL), null);         // no open slot
        Long onlineEnglish = provider(Role.GYNECOLOGIST, "Bengaluru", List.of("english"), List.of(Modality.VIRTUAL), 1);
        provider(Role.NUTRITIONIST, "Chennai", List.of("Tamil"), List.of(Modality.VIRTUAL), 1);            // other type

        mockMvc.perform(get("/api/v1/me/care/matches?professionalType=GYNECOLOGIST").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria.city").value("Chennai"))
                .andExpect(jsonPath("$.matches", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.matches[0].provider.id").value(onlineEnglish))
                .andExpect(jsonPath("$.matches[0].matchReasons[0]").value("Speaks english"))
                .andExpect(jsonPath("$.matches[1].provider.id").value(chennaiTamil))
                .andExpect(jsonPath("$.matches[1].matchReasons", org.hamcrest.Matchers.contains(
                        "Speaks Tamil", "Offers online sessions", "In-person in Chennai")))
                .andExpect(jsonPath("$.matches[1].nextSlots", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/v1/me/care/matches?professionalType=GYNECOLOGIST&modality=PHYSICAL").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.matches[0].provider.id").value(chennaiTamil))
                .andExpect(jsonPath("$.matches[0].matchReasons", org.hamcrest.Matchers.contains("Speaks Tamil", "In-person in Chennai")));

        mockMvc.perform(get("/api/v1/me/care/matches?professionalType=PEDIATRICIAN").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void noIntakeLanguages_returns409_missingType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/me/care/matches?professionalType=GYNECOLOGIST").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.missing[0]").value("languages"));

        mockMvc.perform(get("/api/v1/me/care/matches").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.professionalType").exists());
    }
}
