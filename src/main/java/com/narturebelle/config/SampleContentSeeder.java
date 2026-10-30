package com.narturebelle.config;

import com.narturebelle.model.*;
import com.narturebelle.repository.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Sample content so no public or listable endpoint comes back empty -- in every profile, prod included
 * (product-owner decision, 2026-10-30). Everything here is labelled as sample in the data itself, because
 * the UI shows rows exactly as returned. Runs on every boot from DataInitializer and is idempotent:
 * it labels the legacy sample rows, adds what's missing, and tops up time-based lists (slots, events)
 * so they don't empty out as dates pass. Real content goes in through the admin endpoints.
 */
@Component
public class SampleContentSeeder {

    static final String PRODUCT_LABEL = "Sample product — not a real listing. ";
    static final String PROVIDER_LABEL = "Sample provider — not a real professional. ";
    static final String SAMPLE_PROVIDER_UID_PREFIX = "sample-provider-";
    private static final String PARTNER_LABEL = "Sample listing — not a real partner or offer";
    private static final String SESSION_LABEL = "Sample session — not a real doctor. ";
    private static final String PACKAGE_LABEL = "Sample package — not a real service. ";
    private static final String EVENT_HOST_BIO = "Sample event — not a real gathering.";

    private static final int MIN_OPEN_SLOTS_PER_PROVIDER = 6;
    private static final int MIN_OPEN_CALL_SLOTS_PER_COORDINATOR = 4;
    private static final int MIN_UPCOMING_EVENTS = 3;

    // Names DataInitializer has always seeded these legacy sample rows under.
    private static final Set<String> LEGACY_PRODUCTS = Set.of(
            "Pure Methylfolate & Gentle Iron Prenatal Complex",
            "Organic Morning Sickness Ginger & B6 Lozenges",
            "Pure Botanical Fragrance-Free Belly Hydration Mist",
            "Acupressure P6 Anti-Nausea Wristbands (Pair)",
            "Consumer Ultrasound Fetal Doppler Device",
            "Ergonomic U-Shaped Full Body Maternity Sleep Pillow",
            "Intensive Stretch Mark Cocoa & Rosehip Belly Butter",
            "Breathable Ergonomic Maternity Bump & Pelvic Support Band",
            "Cooling Magnesium & Peppermint Tired Legs Balm",
            "High-Compression Postpartum Belly Snatcher",
            "The Complete Pre-Packed Maternity Hospital Delivery Bag",
            "Organic Cold-Pressed Perineal Massage Oil with Vitamin E",
            "Organic Red Raspberry Leaf Uterine Toning Tea (30 Bags)",
            "Anti-Burst Ergonomic Maternity Birthing Ball (with Pump)",
            "Newborn Electronic Smart Sock & Pulse Tracker");
    private static final Set<String> LEGACY_HOSPITALS = Set.of(
            "Cloudnine Hospital - Mother & Baby Center",
            "Apollo Cradle & Children's Hospital",
            "Motherhood Hospital - Women & Children",
            "Fernandez Hospital - Unit of Stork Home",
            "Fortis La Femme - Centre for Women");
    private static final Set<String> LEGACY_CHECKLISTS = Set.of(
            "NT Scan (Nuchal Translucency) & Dual Marker Blood Screen",
            "Complete Blood Count (CBC), Thyroid Profile & Blood Grouping",
            "Questions to Ask Your OB-GYN in Month 3",
            "Level-II Target / TIFFA Ultrasound (Anomaly Scan)",
            "Oral Glucose Tolerance Test (OGTT / GDM Screen)",
            "Fetal Movement Count (Kick Counter) Orientation",
            "Group B Streptococcus (GBS) Screening & Placental Maturity Scan",
            "Non-Stress Test (NST / CTG Cardiotocography)",
            "Birth Plan & Cord Blood Stem Cell Banking Decision");
    private static final Set<String> LEGACY_NURSE_PACKAGES = Set.of(
            "Trimester Care Companion",
            "Full 9-Months 'Maa Sakhi' Complete Journey",
            "Month-by-Month Flexible Support");
    private static final Set<String> LEGACY_DOCTORS = Set.of(
            "Dr. Ananya Sharma, MD (OB-GYN)",
            "Dr. Priya Ramanathan, MD, DNB",
            "Sister Meenakshi Sundaram, RN, RM");

    private final ProductRepository productRepository;
    private final HospitalPartnerRepository hospitalPartnerRepository;
    private final ChecklistTaskRepository checklistTaskRepository;
    private final NursePackageRepository nursePackageRepository;
    private final DoctorSessionRepository doctorSessionRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ProviderSlotRepository providerSlotRepository;
    private final OnboardingCallSlotRepository onboardingCallSlotRepository;
    private final CommunityEventRepository communityEventRepository;
    private final CoordinatorRepository coordinatorRepository;

    public SampleContentSeeder(ProductRepository productRepository,
                               HospitalPartnerRepository hospitalPartnerRepository,
                               ChecklistTaskRepository checklistTaskRepository,
                               NursePackageRepository nursePackageRepository,
                               DoctorSessionRepository doctorSessionRepository,
                               UserRepository userRepository,
                               ProviderRepository providerRepository,
                               ServiceOfferingRepository serviceOfferingRepository,
                               ProviderSlotRepository providerSlotRepository,
                               OnboardingCallSlotRepository onboardingCallSlotRepository,
                               CommunityEventRepository communityEventRepository,
                               CoordinatorRepository coordinatorRepository) {
        this.productRepository = productRepository;
        this.hospitalPartnerRepository = hospitalPartnerRepository;
        this.checklistTaskRepository = checklistTaskRepository;
        this.nursePackageRepository = nursePackageRepository;
        this.doctorSessionRepository = doctorSessionRepository;
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.providerSlotRepository = providerSlotRepository;
        this.onboardingCallSlotRepository = onboardingCallSlotRepository;
        this.communityEventRepository = communityEventRepository;
        this.coordinatorRepository = coordinatorRepository;
    }

    public void run() {
        labelLegacySamples();
        seedBabyCareProducts();
        seedSampleProviders();
        topUpProviderSlots();
        topUpOnboardingCallSlots();
        topUpCommunityEvents();
    }

    /**
     * The original sample catalog names real hospitals, labs and brands with invented offers and codes,
     * and invented doctors. Label every one, drop the made-up discount codes, phone numbers and meeting
     * links, so nothing reads as a real offer or a real professional.
     */
    private void labelLegacySamples() {
        for (Product p : productRepository.findAll()) {
            if (LEGACY_PRODUCTS.contains(p.getName()) && !startsWith(p.getDescription(), PRODUCT_LABEL)) {
                p.setDescription(PRODUCT_LABEL + nullToEmpty(p.getDescription()));
                productRepository.save(p);
            }
        }
        for (HospitalPartner h : hospitalPartnerRepository.findAll()) {
            if (LEGACY_HOSPITALS.contains(h.getName()) && !PARTNER_LABEL.equals(h.getDiscountDetails())) {
                h.setDiscountDetails(PARTNER_LABEL);
                h.setPhone(null);
                hospitalPartnerRepository.save(h);
            }
        }
        for (ChecklistTask t : checklistTaskRepository.findAll()) {
            if (LEGACY_CHECKLISTS.contains(t.getTestName()) && !PARTNER_LABEL.equals(t.getSponsoredPartnerBadge())
                    && t.getSponsoredPartnerName() != null) {
                t.setSponsoredPartnerBadge(PARTNER_LABEL);
                t.setPartnerDiscountCode(null);
                checklistTaskRepository.save(t);
            }
        }
        for (NursePackage n : nursePackageRepository.findAll()) {
            if (LEGACY_NURSE_PACKAGES.contains(n.getName()) && !startsWith(n.getDescription(), PACKAGE_LABEL)) {
                n.setDescription(PACKAGE_LABEL + nullToEmpty(n.getDescription()));
                n.setBadge("SAMPLE");
                nursePackageRepository.save(n);
            }
        }
        // Events an earlier build seeded with a "placeholder host" bio.
        for (CommunityEvent e : communityEventRepository.findAll()) {
            if ("Placeholder host pending real event scheduling.".equals(e.getHostBio())) {
                e.setHostBio(EVENT_HOST_BIO);
                if (!e.getTitle().endsWith("(sample)")) e.setTitle(e.getTitle() + " (sample)");
                communityEventRepository.save(e);
            }
        }
        for (DoctorSession d : doctorSessionRepository.findAll()) {
            if (LEGACY_DOCTORS.contains(d.getDoctorName()) && !startsWith(d.getDoctorTitle(), SESSION_LABEL)) {
                d.setDoctorTitle(SESSION_LABEL + nullToEmpty(d.getDoctorTitle()));
                d.setMeetingUrl(null);
                doctorSessionRepository.save(d);
            }
        }
    }

    /** The legacy catalog has nothing for after birth, so postpartum users got no recommendations. */
    private void seedBabyCareProducts() {
        if (!productRepository.findByStage(PregnancyStage.BABY_CARE).isEmpty()) return;

        babyCareProduct("Sample Nursing Pillow", "Supportive pillow that holds baby at a comfortable height for feeds.",
                "1299", "1599", NecessityTier.MANDATORY, "Feeding", List.of("Removable washable cover", "Fits most seating positions"));
        babyCareProduct("Sample Postpartum Recovery Kit", "Soft maternity pads, disposable briefs and a peri bottle for the first weeks home.",
                "999", "1299", NecessityTier.MANDATORY, "Postpartum Recovery", List.of("Everything for the first two weeks", "Unscented"));
        babyCareProduct("Sample Muslin Swaddle Set (3)", "Breathable cotton muslin swaddles for newborns.",
                "849", "1099", NecessityTier.OPTIONAL, "Baby Care", List.of("Breathable cotton", "Softens with every wash"));
        babyCareProduct("Sample Baby Sleep Sack", "Wearable blanket for safe, cosy newborn sleep.",
                "1199", "1499", NecessityTier.OPTIONAL, "Sleep", List.of("Two-way zip for easy changes", "No loose blankets in the cot"));
    }

    private void babyCareProduct(String name, String description, String price, String originalPrice, NecessityTier tier,
                                 String category, List<String> benefits) {
        productRepository.save(new Product(null, name, PRODUCT_LABEL + description,
                new BigDecimal(price), new BigDecimal(originalPrice), PregnancyStage.BABY_CARE, tier,
                null, null, null, null, false, category, new ArrayList<>(benefits), new ArrayList<>()));
    }

    private record SampleProvider(Role type, String title, String city, List<String> languages) {}

    /** Two sample providers per professional type, across cities and languages, each offering online and in-person sessions. */
    private static final List<SampleProvider> SAMPLE_PROVIDERS = List.of(
            new SampleProvider(Role.GYNECOLOGIST, "Gynecologist", "Bengaluru", List.of("English", "Kannada", "Hindi")),
            new SampleProvider(Role.GYNECOLOGIST, "Gynecologist", "Mumbai", List.of("English", "Hindi", "Marathi")),
            new SampleProvider(Role.NUTRITIONIST, "Nutritionist", "Chennai", List.of("English", "Tamil")),
            new SampleProvider(Role.NUTRITIONIST, "Nutritionist", "Delhi", List.of("English", "Hindi")),
            new SampleProvider(Role.LACTATION_CONSULTANT, "Lactation Consultant", "Hyderabad", List.of("English", "Telugu", "Hindi")),
            new SampleProvider(Role.LACTATION_CONSULTANT, "Lactation Consultant", "Bengaluru", List.of("English", "Kannada")),
            new SampleProvider(Role.PEDIATRICIAN, "Pediatrician", "Mumbai", List.of("English", "Hindi", "Marathi")),
            new SampleProvider(Role.PEDIATRICIAN, "Pediatrician", "Chennai", List.of("English", "Tamil")),
            new SampleProvider(Role.POSTPARTUM_PHYSIOTHERAPIST, "Postpartum Physiotherapist", "Delhi", List.of("English", "Hindi", "Punjabi")),
            new SampleProvider(Role.POSTPARTUM_PHYSIOTHERAPIST, "Postpartum Physiotherapist", "Hyderabad", List.of("English", "Telugu")),
            new SampleProvider(Role.CLINICAL_PSYCHOLOGIST, "Clinical Psychologist", "Bengaluru", List.of("English", "Hindi")),
            new SampleProvider(Role.CLINICAL_PSYCHOLOGIST, "Clinical Psychologist", "Kolkata", List.of("English", "Bengali", "Hindi")),
            new SampleProvider(Role.REGISTERED_NURSE, "Registered Nurse", "Chennai", List.of("English", "Tamil", "Malayalam")),
            new SampleProvider(Role.REGISTERED_NURSE, "Registered Nurse", "Mumbai", List.of("English", "Hindi", "Gujarati")));

    private void seedSampleProviders() {
        for (int i = 0; i < SAMPLE_PROVIDERS.size(); i++) {
            SampleProvider sample = SAMPLE_PROVIDERS.get(i);
            String uid = SAMPLE_PROVIDER_UID_PREFIX + (i + 1);
            if (userRepository.findByFirebaseUid(uid).isPresent()) continue;

            // A sample provider needs a user row (providers.user_id is required). No Firebase account backs it,
            // so nobody can sign in as one.
            User user = new User();
            user.setFirebaseUid(uid);
            user.setFirstName("Sample");
            user.setLastName(sample.title());
            user.setAuthProvider("SAMPLE");
            user.setRoles(Set.of(sample.type()));
            Long userId = userRepository.save(user).getId();

            Provider provider = new Provider();
            provider.setUserId(userId);
            provider.setProfessionalType(sample.type());
            provider.setDisplayName("Sample " + sample.title() + " (" + sample.city() + ")");
            provider.setBio(PROVIDER_LABEL + "Shown so the care directory and booking can be tried end to end.");
            provider.setCity(sample.city());
            provider.setLanguages(new ArrayList<>(sample.languages()));
            provider.setStatus(ProviderStatus.ACTIVE);
            Long providerId = providerRepository.save(provider).getId();

            offering(providerId, "Online consultation (sample)", Modality.VIRTUAL, 30);
            offering(providerId, "In-person consultation (sample)", Modality.PHYSICAL, 45);
        }
    }

    private void offering(Long providerId, String name, Modality modality, int minutes) {
        ServiceOffering offering = new ServiceOffering();
        offering.setProviderId(providerId);
        offering.setName(name);
        offering.setDescription("Sample service — not a real appointment.");
        offering.setModality(modality);
        offering.setDurationMinutes(minutes);
        offering.setActive(true);
        serviceOfferingRepository.save(offering);
    }

    /** Keeps every sample provider bookable: at least a few open future slots on weekdays, 10:00 / 14:00 / 17:00. */
    private void topUpProviderSlots() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < SAMPLE_PROVIDERS.size(); i++) {
            User user = userRepository.findByFirebaseUid(SAMPLE_PROVIDER_UID_PREFIX + (i + 1)).orElse(null);
            Provider provider = user == null ? null : providerRepository.findByUserId(user.getId()).orElse(null);
            if (provider == null) continue;

            List<ProviderSlot> open = providerSlotRepository
                    .findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(provider.getId(), SlotStatus.OPEN, now);
            int missing = MIN_OPEN_SLOTS_PER_PROVIDER - open.size();
            LocalDateTime cursor = open.isEmpty() ? now : open.get(open.size() - 1).getStartAt();
            for (LocalDateTime start : weekdaySlotsAfter(cursor, missing, 10, 14, 17)) {
                ProviderSlot slot = new ProviderSlot();
                slot.setProviderId(provider.getId());
                slot.setStartAt(start);
                slot.setEndAt(start.plusMinutes(45));
                slot.setStatus(SlotStatus.OPEN);
                providerSlotRepository.save(slot);
            }
        }
    }

    private record SampleCoordinator(String name, List<String> languages) {}

    /** Between them, every language the sample providers speak -- so any user's language has a coordinator. */
    private static final List<SampleCoordinator> SAMPLE_COORDINATORS = List.of(
            new SampleCoordinator("Sample Belle Coordinator — North", List.of("English", "Hindi", "Punjabi", "Bengali")),
            new SampleCoordinator("Sample Belle Coordinator — South", List.of("English", "Tamil", "Telugu", "Malayalam", "Kannada")),
            new SampleCoordinator("Sample Belle Coordinator — West", List.of("English", "Hindi", "Marathi", "Gujarati")));

    /**
     * Sample coordinators, each kept topped up with open call slots so onboarding's call step always has
     * something bookable in every language. Open slots from before coordinators existed (all sample) are
     * handed to the first sample coordinator so they stay visible.
     */
    private void topUpOnboardingCallSlots() {
        LocalDateTime now = LocalDateTime.now();
        List<Coordinator> coordinators = new ArrayList<>();
        for (SampleCoordinator sample : SAMPLE_COORDINATORS) {
            coordinators.add(coordinatorRepository.findFirstByDisplayName(sample.name()).orElseGet(() -> {
                Coordinator coordinator = new Coordinator();
                coordinator.setDisplayName(sample.name());
                coordinator.setLanguages(new ArrayList<>(sample.languages()));
                coordinator.setBio("Sample coordinator — not a real person.");
                coordinator.setActive(true);
                return coordinatorRepository.save(coordinator);
            }));
        }

        List<OnboardingCallSlot> open = onboardingCallSlotRepository.findByStatusAndStartAtAfterOrderByStartAtAsc(SlotStatus.OPEN, now);
        for (OnboardingCallSlot legacy : open) {
            if (legacy.getCoordinatorId() == null) {
                legacy.setCoordinatorId(coordinators.get(0).getId());
                onboardingCallSlotRepository.save(legacy);
            }
        }

        for (Coordinator coordinator : coordinators) {
            List<OnboardingCallSlot> theirs = open.stream()
                    .filter(slot -> coordinator.getId().equals(slot.getCoordinatorId()))
                    .toList();
            int missing = MIN_OPEN_CALL_SLOTS_PER_COORDINATOR - theirs.size();
            LocalDateTime cursor = theirs.isEmpty() ? now : theirs.get(theirs.size() - 1).getStartAt();
            for (LocalDateTime start : weekdaySlotsAfter(cursor, missing, 10, 15)) {
                OnboardingCallSlot slot = new OnboardingCallSlot();
                slot.setStartAt(start);
                slot.setEndAt(start.plusMinutes(30));
                slot.setStatus(SlotStatus.OPEN);
                slot.setCoordinatorId(coordinator.getId());
                onboardingCallSlotRepository.save(slot);
            }
        }
    }

    /** Hosts are generic, not real people, and there's no joinUrl -- a fake meeting link would look real. */
    private void topUpCommunityEvents() {
        LocalDateTime now = LocalDateTime.now();
        long upcoming = communityEventRepository.findByEndsAtAfterOrderByStartsAtAsc(now).stream()
                .filter(e -> e.getStatus() == CommunityEventStatus.SCHEDULED)
                .count();
        if (upcoming >= MIN_UPCOMING_EVENTS) return;

        LocalDateTime base = now.plusDays(3).withHour(18).withMinute(0).withSecond(0).withNano(0);
        communityEvent("First-time parents' circle (sample)",
                "An informal evening to meet other first-time parents and swap what's been working for you.",
                "Peer support", base, Modality.VIRTUAL, null, 20);
        communityEvent("Pregnancy journaling hour (sample)",
                "Bring a notebook. Guided prompts for reflecting on the weeks so far, at your own pace.",
                "Wellbeing", base.plusDays(4), Modality.VIRTUAL, null, null);
        communityEvent("New mums' morning meet-up (sample)",
                "Coffee and conversation for mums with little ones. Babies welcome.",
                "Meet-up", base.plusDays(9).withHour(10), Modality.PHYSICAL, "Venue shared with attendees", 12);
    }

    private void communityEvent(String title, String description, String category, LocalDateTime startsAt,
                                Modality modality, String location, Integer capacity) {
        CommunityEvent event = new CommunityEvent();
        event.setTitle(title);
        event.setDescription(description);
        event.setHostName("NartureBelle community team (sample)");
        event.setHostBio(EVENT_HOST_BIO);
        event.setCategory(category);
        event.setStartsAt(startsAt);
        event.setEndsAt(startsAt.plusMinutes(60));
        event.setModality(modality);
        event.setLocation(location);
        event.setCapacity(capacity);
        event.setStatus(CommunityEventStatus.SCHEDULED);
        communityEventRepository.save(event);
    }

    /** The next {@code count} weekday slot starts strictly after {@code after}, at the given hours, from tomorrow on. */
    static List<LocalDateTime> weekdaySlotsAfter(LocalDateTime after, int count, int... hours) {
        List<LocalDateTime> starts = new ArrayList<>();
        if (count <= 0) return starts;
        LocalDateTime day = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        while (starts.size() < count) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                for (int hour : Arrays.stream(hours).sorted().toArray()) {
                    LocalDateTime start = day.withHour(hour);
                    if (start.isAfter(after) && starts.size() < count) {
                        starts.add(start);
                    }
                }
            }
            day = day.plusDays(1);
        }
        return starts;
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
