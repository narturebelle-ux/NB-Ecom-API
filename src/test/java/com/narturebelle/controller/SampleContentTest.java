package com.narturebelle.controller;

import com.narturebelle.config.SampleContentSeeder;
import com.narturebelle.model.CommunityEventStatus;
import com.narturebelle.model.Modality;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Provider;
import com.narturebelle.model.Role;
import com.narturebelle.model.ServiceOffering;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.repository.CommunityEventRepository;
import com.narturebelle.repository.OnboardingCallSlotRepository;
import com.narturebelle.repository.ProductRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.ServiceOfferingRepository;
import com.narturebelle.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers SampleContentSeeder: every listable endpoint has (labelled) sample rows, and re-running adds nothing new. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SampleContentTest {

    private static final Set<Role> PROVIDER_TYPES = EnumSet.of(Role.GYNECOLOGIST, Role.NUTRITIONIST, Role.LACTATION_CONSULTANT,
            Role.PEDIATRICIAN, Role.POSTPARTUM_PHYSIOTHERAPIST, Role.CLINICAL_PSYCHOLOGIST, Role.REGISTERED_NURSE);

    @Autowired private MockMvc mockMvc;
    @Autowired private SampleContentSeeder seeder;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired private ProviderSlotRepository providerSlotRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CommunityEventRepository communityEventRepository;
    @Autowired private OnboardingCallSlotRepository onboardingCallSlotRepository;

    /** Other test classes share this context and clear these tables, so start clean and seed explicitly. */
    @BeforeEach
    void setUp() {
        providerSlotRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        providerRepository.deleteAll();
        userRepository.deleteAll();
        communityEventRepository.deleteAll();
        onboardingCallSlotRepository.deleteAll();
        seeder.run();
    }

    @Test
    void everyProfessionalType_isListed_withBothModalitiesAndOpenSlots() throws Exception {
        List<Provider> providers = providerRepository.findAll();
        assertEquals(PROVIDER_TYPES, providers.stream().map(Provider::getProfessionalType).collect(Collectors.toSet()));

        for (Provider provider : providers) {
            assertTrue(provider.getBio().startsWith("Sample provider — not a real professional."));
            assertTrue(provider.getDisplayName().startsWith("Sample "));
            Set<Modality> modalities = serviceOfferingRepository.findByProviderId(provider.getId()).stream()
                    .filter(ServiceOffering::isActive).map(ServiceOffering::getModality).collect(Collectors.toSet());
            assertEquals(EnumSet.allOf(Modality.class), modalities);
            assertTrue(providerSlotRepository.findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(
                    provider.getId(), SlotStatus.OPEN, LocalDateTime.now()).size() >= 6);
        }

        mockMvc.perform(get("/api/v1/providers?professionalType=GYNECOLOGIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
        mockMvc.perform(get("/api/v1/providers?modality=PHYSICAL&city=Chennai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void rerunning_addsNoDuplicatesAndKeepsSlotCounts() {
        long providers = providerRepository.count();
        long slots = providerSlotRepository.count();
        long callSlots = onboardingCallSlotRepository.count();
        long events = communityEventRepository.count();
        long products = productRepository.count();

        seeder.run();

        assertEquals(providers, providerRepository.count());
        assertEquals(slots, providerSlotRepository.count());
        assertEquals(callSlots, onboardingCallSlotRepository.count());
        assertEquals(events, communityEventRepository.count());
        assertEquals(products, productRepository.count());
    }

    @Test
    void timeBasedLists_andBabyCareProducts_areNeverEmpty() {
        assertTrue(onboardingCallSlotRepository.findByStatusAndStartAtAfterOrderByStartAtAsc(SlotStatus.OPEN, LocalDateTime.now()).size() >= 8);
        assertTrue(communityEventRepository.findByEndsAtAfterOrderByStartsAtAsc(LocalDateTime.now()).stream()
                .filter(e -> e.getStatus() == CommunityEventStatus.SCHEDULED).count() >= 3);
        assertFalse(productRepository.findByStage(PregnancyStage.BABY_CARE).isEmpty());
    }

    @Test
    void legacySampleRows_areLabelledAndStripped() throws Exception {
        assertTrue(productRepository.findAll().stream()
                .filter(p -> p.getName().equals("Pure Methylfolate & Gentle Iron Prenatal Complex"))
                .allMatch(p -> p.getDescription().startsWith("Sample product — not a real listing.")));

        mockMvc.perform(get("/api/v1/hospitals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].discountDetails").value("Sample listing — not a real partner or offer"))
                .andExpect(jsonPath("$[0].phone").doesNotExist());
        mockMvc.perform(get("/api/v1/doctor-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doctorTitle", org.hamcrest.Matchers.startsWith("Sample session — not a real doctor.")))
                .andExpect(jsonPath("$[0].meetingUrl").doesNotExist());
    }
}
