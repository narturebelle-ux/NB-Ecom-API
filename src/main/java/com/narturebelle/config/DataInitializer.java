package com.narturebelle.config;

import com.narturebelle.model.*;
import com.narturebelle.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ChecklistTaskRepository checklistTaskRepository;
    private final HospitalPartnerRepository hospitalPartnerRepository;
    private final NursePackageRepository nursePackageRepository;
    private final DoctorSessionRepository doctorSessionRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final ConsentDocumentRepository consentDocumentRepository;

    public DataInitializer(ProductRepository productRepository,
                           ChecklistTaskRepository checklistTaskRepository,
                           HospitalPartnerRepository hospitalPartnerRepository,
                           NursePackageRepository nursePackageRepository,
                           DoctorSessionRepository doctorSessionRepository,
                           FeatureFlagRepository featureFlagRepository,
                           ConsentDocumentRepository consentDocumentRepository) {
        this.productRepository = productRepository;
        this.checklistTaskRepository = checklistTaskRepository;
        this.hospitalPartnerRepository = hospitalPartnerRepository;
        this.nursePackageRepository = nursePackageRepository;
        this.doctorSessionRepository = doctorSessionRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.consentDocumentRepository = consentDocumentRepository;
    }

    @Override
    public void run(String... args) {
        seedProducts();
        seedChecklists();
        seedHospitalPartners();
        seedNursePackages();
        seedDoctorSessions();
        seedFeatureFlags();
        seedConsentDocuments();
    }

    /**
     * Normally seeded by V4__feature_flags.sql, but the test profile skips Flyway entirely (H2
     * create-drop instead), so this covers that path too — harmless no-op elsewhere since the
     * count() > 0 guard skips it once Flyway has already seeded the real databases.
     */
    private void seedFeatureFlags() {
        if (featureFlagRepository.count() > 0) return;

        seedFlag("nurseCompanion", true, "Maa Sakhi nurse care plan & on-call triage");
        seedFlag("hospitalLeads", true, "Local maternity hospital directory & tour bookings");
        seedFlag("doctorLiveSessions", true, "OB-GYN interactive live streams & RSVP");
        seedFlag("assessmentVoucher", true, "Instant discount voucher on diagnostic completion");
        seedFlag("babyCareProducts", false, "Future expansion (0-5 years) - hidden by default");
        seedFlag("productRecommendations", false, "Product store pages (Phase 6)");
        seedFlag("careBooking", false, "Provider booking & sessions (Phase 3)");
        seedFlag("recordsVault", false, "Health records vault & sharing (Phase 4)");
        seedFlag("childTracker", false, "Child profile + vaccination tracker (Phase 6)");
        seedFlag("aiAssistant", false, "AI record plan (Phase 6) - needs clinical governance first");
        seedFlag("joyCircle", false, "Non-clinical group events (Phase 6)");
        seedFlag("legacyLeadForms", false, "Old public lead/assessment forms");
    }

    private void seedFlag(String key, boolean enabled, String description) {
        FeatureFlag flag = new FeatureFlag();
        flag.setKey(key);
        flag.setEnabled(enabled);
        flag.setDescription(description);
        featureFlagRepository.save(flag);
    }

    /**
     * Normally seeded by V5__consent.sql, but the test profile skips Flyway entirely (see
     * seedFeatureFlags() above for the same pattern) — this keeps H2 in sync with that seed data.
     */
    private void seedConsentDocuments() {
        if (consentDocumentRepository.count() > 0) return;

        seedConsentDocument(ConsentDocumentType.TERMS, 1, "Terms of Service",
                "# Terms of Service (DRAFT)\n\nThis is placeholder text pending legal review. Do not treat this as a final or binding document.");
        seedConsentDocument(ConsentDocumentType.PRIVACY, 1, "Privacy Notice",
                "# Privacy Notice (DRAFT)\n\nThis is placeholder text pending legal review. Do not treat this as a final or binding document.");
    }

    private void seedConsentDocument(ConsentDocumentType type, int version, String title, String bodyMarkdown) {
        ConsentDocument doc = new ConsentDocument();
        doc.setType(type);
        doc.setVersion(version);
        doc.setTitle(title);
        doc.setBodyMarkdown(bodyMarkdown);
        doc.setEffectiveAt(LocalDateTime.now());
        doc.setActive(true);
        consentDocumentRepository.save(doc);
    }

    private void seedProducts() {
        if (productRepository.count() > 0) return;

        // ================= MONTH 3 (FIRST TRIMESTER) =================
        // Mandatory 1: Pure Methylfolate Prenatal
        productRepository.save(new Product(null,
                "Pure Methylfolate & Gentle Iron Prenatal Complex",
                "Non-constipating, clinically bioavailable prenatal multivitamin engineered for rapid cellular division and neural tube closure during weeks 4–12.",
                new BigDecimal("849"), new BigDecimal("1099"),
                PregnancyStage.MONTH_3, NecessityTier.MANDATORY,
                "Essential: Folic acid/Methylfolate is non-negotiable in early pregnancy to prevent neural tube defects and support early placental vascularization.",
                "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=600&q=80",
                4.9, 328, true, "Nutrition",
                Arrays.asList("Non-constipating chelated iron", "Pure active L-Methylfolate 800mcg", "Certified Organic & Vegan capsules"),
                Arrays.asList("NAUSEA", "FATIGUE", "EARLY_DEVELOPMENT")
        ));

        // Mandatory 2: Organic Ginger & B6 Drops
        productRepository.save(new Product(null,
                "Organic Morning Sickness Ginger & B6 Lozenges",
                "Certified organic crystallized ginger drops infused with micro-dose Vitamin B6 to calm gastric spasms and hyperemesis during early pregnancy.",
                new BigDecimal("499"), new BigDecimal("650"),
                PregnancyStage.MONTH_3, NecessityTier.MANDATORY,
                "Essential: Vitamin B6 combined with natural ginger is the ACOG first-line clinical recommendation for reducing pregnancy-induced nausea.",
                "https://images.unsplash.com/photo-1607613009820-a29f7bb81c04?auto=format&fit=crop&w=600&q=80",
                4.8, 412, true, "Nausea Relief",
                Arrays.asList("Clinically backed 25mg Vitamin B6", "Real organic ginger root extract", "Zero refined sugars or artificial colors"),
                Arrays.asList("NAUSEA", "VOMITING", "MORNING_SICKNESS")
        ));

        // Mandatory 3: Fragrance-Free Belly Hydration Mist
        productRepository.save(new Product(null,
                "Pure Botanical Fragrance-Free Belly Hydration Mist",
                "Ultralight organic aloe and hyaluronic body spray formulated without scent compounds to soothe ultra-sensitive first-trimester skin without triggering nausea.",
                new BigDecimal("699"), new BigDecimal("899"),
                PregnancyStage.MONTH_3, NecessityTier.MANDATORY,
                "Essential: Pregnant skin stretches early while olfactory sensitivity spikes. Fragrance-free clean hydration prevents early itching safely.",
                "https://images.unsplash.com/photo-1608248597359-00995fa1b6cf?auto=format&fit=crop&w=600&q=80",
                4.7, 189, true, "Skin Care",
                Arrays.asList("100% Unscented & Hypoallergenic", "Immediate itch relief with pure Aloe Vera", "Safe for hormonal flare-ups"),
                Arrays.asList("ITCHY_SKIN", "SENSITIVE_SKIN")
        ));

        // Optional: Acupressure Morning Sickness Wristband
        productRepository.save(new Product(null,
                "Acupressure P6 Anti-Nausea Wristbands (Pair)",
                "Soft elastic woven bands that apply gentle mechanical pressure to the Nei-Kuan (P6) acupressure point on each wrist.",
                new BigDecimal("349"), new BigDecimal("499"),
                PregnancyStage.MONTH_3, NecessityTier.OPTIONAL,
                "Optional Comfort: Non-invasive drug-free comfort tool. Great to have during car commutes or morning routines, but not clinically mandatory.",
                "https://images.unsplash.com/photo-1512290900672-1f4f4699042b?auto=format&fit=crop&w=600&q=80",
                4.5, 96, false, "Comfort Tool",
                Arrays.asList("Zero side effects", "Washable soft fabric", "Works on targeted P6 pressure points"),
                Arrays.asList("NAUSEA", "TRAVEL_SICKNESS")
        ));

        // Skip Item: Consumer Home Doppler
        productRepository.save(new Product(null,
                "Consumer Ultrasound Fetal Doppler Device",
                "Pocket sound doppler marketed to parents to hear fetal heartbeat at home.",
                new BigDecimal("2999"), new BigDecimal("3999"),
                PregnancyStage.MONTH_3, NecessityTier.SKIP,
                "What to Skip (Save Your Money): Medical obstetricians strongly advise against home fetal dopplers in Month 3. They frequently pick up maternal artery sounds instead of the baby, causing needless panic or false reassurance.",
                "https://images.unsplash.com/photo-1516549655169-df83a0774514?auto=format&fit=crop&w=600&q=80",
                3.1, 45, false, "Consumer Gadget",
                Arrays.asList("Not recommended by OB-GYNs", "Prone to user error", "Waste of early pregnancy budget"),
                Arrays.asList("ANXIETY")
        ));

        // ================= MONTH 6 (SECOND TRIMESTER) =================
        // Mandatory 1: U-Shaped Full Body Pregnancy Pillow
        productRepository.save(new Product(null,
                "Ergonomic U-Shaped Full Body Maternity Sleep Pillow",
                "Full 360-degree orthopedic body pillow with 100% organic cotton cover that supports the lumbar spine, pelvis, and growing bump simultaneously.",
                new BigDecimal("1899"), new BigDecimal("2799"),
                PregnancyStage.MONTH_6, NecessityTier.MANDATORY,
                "Essential: Side-sleeping (SOS position) after week 20 is medically crucial to prevent inferior vena cava compression and ensure optimal blood flow to the placenta.",
                "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?auto=format&fit=crop&w=600&q=80",
                4.9, 580, true, "Sleep Comfort",
                Arrays.asList("Orthopedic spinal alignment", "Certified Oeko-Tex cotton cover", "Relieves round ligament & hip pain"),
                Arrays.asList("BACK_PAIN", "INSOMNIA", "PELVIC_PRESSURE")
        ));

        // Mandatory 2: Shea & Rosehip Intensive Stretch Mark Butter
        productRepository.save(new Product(null,
                "Intensive Stretch Mark Cocoa & Rosehip Belly Butter",
                "Ultra-rich dermal elasticity cream rich in pure cold-pressed Rosehip oil, Shea butter, and plant ceramides that deeply replenish rapidly stretching skin layers.",
                new BigDecimal("899"), new BigDecimal("1199"),
                PregnancyStage.MONTH_6, NecessityTier.MANDATORY,
                "Essential: The second trimester is when the belly undergoes its fastest expansion. Maintaining dermal hydration and lipid barrier elasticity minimizes deep dermal tearing.",
                "https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=600&q=80",
                4.8, 620, true, "Skin Elasticity",
                Arrays.asList("Zero mineral oils, parabens, or phthalates", "Infused with pure Centella Asiatica", "Fast-absorbing non-sticky texture"),
                Arrays.asList("STRETCH_MARKS", "ITCHY_BELLY")
        ));

        // Mandatory 3: Pelvic & Bump Support Belt
        productRepository.save(new Product(null,
                "Breathable Ergonomic Maternity Bump & Pelvic Support Band",
                "Dual-tension elastic maternity band that lifts abdominal weight gently off the pelvic floor and distributes it across the back and shoulders.",
                new BigDecimal("999"), new BigDecimal("1499"),
                PregnancyStage.MONTH_6, NecessityTier.MANDATORY,
                "Essential: Relieves sacroiliac (SI) joint pain and Symphysis Pubis Dysfunction (SPD) caused by pregnancy relaxin hormones loosening pelvic ligaments.",
                "https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=600&q=80",
                4.7, 340, true, "Posture & Pelvis",
                Arrays.asList("Doctor recommended for SI joint pain", "Seamless under everyday clothing", "Fully adjustable velcro as bump grows"),
                Arrays.asList("BACK_PAIN", "PELVIC_PAIN", "HEAVY_BUMP")
        ));

        // Optional: Cooling Magnesium Leg Balm
        productRepository.save(new Product(null,
                "Cooling Magnesium & Peppermint Tired Legs Balm",
                "Transdermal magnesium balm blended with organic peppermint and arnica to soothe restless legs and calf cramps during the night.",
                new BigDecimal("649"), new BigDecimal("799"),
                PregnancyStage.MONTH_6, NecessityTier.OPTIONAL,
                "Optional Comfort: Wonderful luxury relief for heavy, swollen evening feet, though elevation and compression socks can also suffice.",
                "https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=600&q=80",
                4.6, 175, true, "Body Relief",
                Arrays.asList("Natural peppermint cool sensation", "Topical magnesium for muscle ease", "Reduces evening heaviness"),
                Arrays.asList("SWOLLEN_FEET", "LEG_CRAMPS")
        ));

        // Skip Item: Postpartum Waist Trainer
        productRepository.save(new Product(null,
                "High-Compression Postpartum Belly Snatcher",
                "Rigid steel-boned abdominal compression corset.",
                new BigDecimal("1899"), new BigDecimal("2499"),
                PregnancyStage.MONTH_6, NecessityTier.SKIP,
                "What to Skip (Save Your Money): Zero utility during pregnancy, and rigid corsets can actually harm abdominal healing postpartum. Do not spend budget on this right now.",
                "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=600&q=80",
                2.9, 30, false, "Avoid Gimmick",
                Arrays.asList("Not usable during pregnancy", "Risk of pelvic floor pressure", "Clinically unproven"),
                Arrays.asList("BODY_IMAGE")
        ));

        // ================= MONTH 9 (THIRD TRIMESTER & DELIVERY PREP) =================
        // Mandatory 1: Pre-Packed Eco Hospital Bag
        productRepository.save(new Product(null,
                "The Complete Pre-Packed Maternity Hospital Delivery Bag",
                "Thoughtfully packed luxury water-resistant duffel containing all sterile post-delivery essentials: maternity pads, organic disposable underwear, feeding gown, nursing pads, and warm non-skid socks.",
                new BigDecimal("2499"), new BigDecimal("3499"),
                PregnancyStage.MONTH_9, NecessityTier.MANDATORY,
                "Essential: Labor can begin anytime after 36 weeks. Having hospital-approved essentials pre-packed prevents chaotic scrambling when water breaks.",
                "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=600&q=80",
                4.9, 780, true, "Hospital Prep",
                Arrays.asList("Doctor & midwife vetted checklist", "Eco-friendly organic cotton essentials", "Keeps hospital documentation organized"),
                Arrays.asList("LABOR_PREP", "ANXIETY", "HOSPITAL_STAY")
        ));

        // Mandatory 2: Organic Perineal Massage Oil
        productRepository.save(new Product(null,
                "Organic Cold-Pressed Perineal Massage Oil with Vitamin E",
                "Certified organic blend of sweet almond, jojoba, and natural Vitamin E specifically formulated to condition perineal tissue from Week 34 onward.",
                new BigDecimal("699"), new BigDecimal("899"),
                PregnancyStage.MONTH_9, NecessityTier.MANDATORY,
                "Essential: Clinical Cochrane reviews demonstrate that daily perineal massage from week 34 significantly lowers the incidence of severe perineal tears and episiotomies during vaginal birth.",
                "https://images.unsplash.com/photo-1608248597359-00995fa1b6cf?auto=format&fit=crop&w=600&q=80",
                4.9, 490, true, "Birth Prep",
                Arrays.asList("Clinically proven to enhance tissue elasticity", "100% plant-based organic formula", "Fragrance-free and preservative-free"),
                Arrays.asList("LABOR_PREP", "BIRTH_RECOVERY")
        ));

        // Mandatory 3: Red Raspberry Leaf Labor Prep Tea
        productRepository.save(new Product(null,
                "Organic Red Raspberry Leaf Uterine Toning Tea (30 Bags)",
                "Pure certified organic Rubus idaeus leaf tea rich in fragarine, traditionally used by midwives in the final month to tone uterine musculature for efficient contractions.",
                new BigDecimal("499"), new BigDecimal("650"),
                PregnancyStage.MONTH_9, NecessityTier.MANDATORY,
                "Essential: Tones uterine muscles in the final weeks, associated with shorter second-stage labor and reduced need for artificial intervention.",
                "https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&w=600&q=80",
                4.8, 310, true, "Herbal Labor Prep",
                Arrays.asList("Caffeine-free organic herbal infusion", "Supports uterine tone for delivery", "Safe for daily consumption from week 36"),
                Arrays.asList("LABOR_PREP", "CONTRACTIONS")
        ));

        // Optional: Birthing & Labor Exercise Ball
        productRepository.save(new Product(null,
                "Anti-Burst Ergonomic Maternity Birthing Ball (with Pump)",
                "Heavy-duty non-slip exercise ball that encourages optimal fetal positioning (anterior position) and pelvic rotation during early labor contractions.",
                new BigDecimal("899"), new BigDecimal("1299"),
                PregnancyStage.MONTH_9, NecessityTier.OPTIONAL,
                "Optional Comfort: Helpful for hip swaying and early labor comfort at home, though a sturdy chair and gentle walking work well too.",
                "https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=600&q=80",
                4.7, 230, true, "Labor Comfort",
                Arrays.asList("Anti-burst tested up to 300kg", "Non-slip matte grip", "Includes foot pump and labor exercise guide"),
                Arrays.asList("PELVIC_OPENING", "EARLY_LABOR")
        ));

        // Skip Item: Newborn Electronic Stride Monitor
        productRepository.save(new Product(null,
                "Newborn Electronic Smart Sock & Pulse Tracker",
                "Expensive wearable sensor claiming to measure newborn foot oxygen continuously.",
                new BigDecimal("12999"), new BigDecimal("16999"),
                PregnancyStage.MONTH_9, NecessityTier.SKIP,
                "What to Skip (Save Your Money): Pediatrician associations caution that consumer wearables frequently trigger false alarms, interrupting crucial sleep and inducing acute parental anxiety.",
                "https://images.unsplash.com/photo-1516549655169-df83a0774514?auto=format&fit=crop&w=600&q=80",
                3.2, 40, false, "Overkill Gadget",
                Arrays.asList("High false-alarm rate", "Very expensive", "Standard safe sleep practices are far more effective"),
                Arrays.asList("SLEEP_ANXIETY")
        ));
    }

    private void seedChecklists() {
        if (checklistTaskRepository.count() > 0) return;

        // Month 3 Tasks & Clinic Ads
        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_3,
                "NT Scan (Nuchal Translucency) & Dual Marker Blood Screen",
                "Weeks 11–13",
                "Measures the clear tissue at the back of baby's neck and maternal blood hormones (PAPP-A and Free Beta-hCG) to assess chromosomal health and placental risk.",
                true, "Ultrasound Scan", "Certified Fetal Medicine Center",
                "Apollo Cradle & Fetal Health Labs",
                "NABH Certified • 15% NartureBelle Partner Discount",
                "NARTURESCAN15",
                "https://apollocradle.com"
        ));

        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_3,
                "Complete Blood Count (CBC), Thyroid Profile & Blood Grouping",
                "Weeks 8–10",
                "Screens for maternal anemia, thyroid abnormalities (TSH), and Rh factor compatibility.",
                true, "Blood Test", "Accredited Diagnostic Pathology Lab",
                "Thyrocare / Lal PathLabs Partner",
                "Home Blood Collection Available • ₹250 Off",
                "NARTUREBLOOD",
                "https://lalpathlabs.com"
        ));

        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_3,
                "Questions to Ask Your OB-GYN in Month 3",
                "Week 12",
                "Ask your doctor about safe nausea medication, dietary supplements, safe exercise limits, and expected schedule of visits.",
                false, "Doctor Question", "OB-GYN Clinic",
                null, null, null, null
        ));

        // Month 6 Tasks & Clinic Ads
        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_6,
                "Level-II Target / TIFFA Ultrasound (Anomaly Scan)",
                "Weeks 18–22",
                "Crucial high-resolution ultrasound examining all major fetal organs, spine, brain, heart chambers, kidneys, and umbilical blood flow.",
                true, "Ultrasound Scan", "Advanced 4D Fetal Ultrasound Unit",
                "Cloudnine Hospital Fetal Medicine",
                "Senior Sonologist Guarantee • Priority Booking",
                "NARTURE4D",
                "https://cloudninecare.com"
        ));

        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_6,
                "Oral Glucose Tolerance Test (OGTT / GDM Screen)",
                "Weeks 24–28",
                "75g oral glucose screening to diagnose gestational diabetes and protect maternal-fetal metabolic balance.",
                true, "Blood Test", "Clinical Diagnostic Lab",
                "Metropolis Healthcare Partner",
                "Early Morning Priority Slot Available",
                "NARTUREGDM",
                "https://metropolisindia.com"
        ));

        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_6,
                "Fetal Movement Count (Kick Counter) Orientation",
                "Week 24+",
                "Learn how to notice your baby's active movement cycles and sleep patterns.",
                false, "Doctor Question", "OB-GYN Clinic",
                null, null, null, null
        ));

        // Month 9 Tasks & Clinic Ads
        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_9,
                "Group B Streptococcus (GBS) Screening & Placental Maturity Scan",
                "Weeks 35–37",
                "Routine swab test to screen for GBS bacteria to ensure safe antibiotic protocol if needed during vaginal birth.",
                true, "Clinical Screening", "Maternity Hospital",
                "Motherhood Hospitals Network",
                "Free Labor Ward Tour & Delivery Package Consultation",
                "NARTUREBIRTH",
                "https://motherhoodindia.com"
        ));

        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_9,
                "Non-Stress Test (NST / CTG Cardiotocography)",
                "Weeks 38+",
                "Monitors fetal heart rate in response to baby movements and detects silent uterine contractions.",
                true, "Hospital Evaluation", "Maternity Labor Ward",
                "Fernandez Hospital Natural Birth Unit",
                "Renowned Natural Birth Leader • 10% Package Benefit",
                "NARTURENATURAL",
                "https://fernandez.foundation"
        ));

        checklistTaskRepository.save(new ChecklistTask(null,
                PregnancyStage.MONTH_9,
                "Birth Plan & Cord Blood Stem Cell Banking Decision",
                "Week 36",
                "Finalize pain relief choices (epidural/natural), labor partner role, and decision on cord blood preservation.",
                false, "Hospital Prep", "Maternity Hospital",
                "LifeCell Stem Cell Banking",
                "Exclusive ₹5,000 Off Stem Cell Preservation for NartureBelle Mothers",
                "NARTURECORD",
                "https://lifecell.in"
        ));
    }

    private void seedHospitalPartners() {
        if (hospitalPartnerRepository.count() > 0) return;

        hospitalPartnerRepository.save(new HospitalPartner(null,
                "Cloudnine Hospital - Mother & Baby Center",
                "Bengaluru", "560017",
                "Old Airport Road, Kodihalli, Bengaluru, Karnataka",
                "Comprehensive Maternity, 4D Fetal Scans & Level-3 NICU",
                4.9, 1240, "+91 99728 00001",
                "https://cloudninecare.com",
                "Free Doctor Consultation + 10% Discount on Delivery Packages",
                "PLATINUM_ACCREDITED",
                "https://images.unsplash.com/photo-1586773860418-d37222d8fce3?auto=format&fit=crop&w=600&q=80"
        ));

        hospitalPartnerRepository.save(new HospitalPartner(null,
                "Apollo Cradle & Children's Hospital",
                "Mumbai", "400034",
                "Tardeo, Near AC Market, Mumbai, Maharashtra",
                "High-Risk Pregnancy, Luxury Labor Suites & Pediatric ICU",
                4.8, 980, "+91 22 4333 5555",
                "https://apollocradle.com",
                "₹5,000 Voucher on Maternity Packages for NartureBelle Mothers",
                "PLATINUM_ACCREDITED",
                "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?auto=format&fit=crop&w=600&q=80"
        ));

        hospitalPartnerRepository.save(new HospitalPartner(null,
                "Motherhood Hospital - Women & Children",
                "Bengaluru", "560038",
                "100 Feet Road, Indiranagar, Bengaluru, Karnataka",
                "Natural Birth Friendly, Prenatal Classes & Advanced Scan Lab",
                4.8, 850, "+91 80 6723 8888",
                "https://motherhoodindia.com",
                "Complimentary Prenatal Yoga & Nutrition Session Included",
                "VERIFIED_PARTNER",
                "https://images.unsplash.com/photo-1516549655169-df83a0774514?auto=format&fit=crop&w=600&q=80"
        ));

        hospitalPartnerRepository.save(new HospitalPartner(null,
                "Fernandez Hospital - Unit of Stork Home",
                "Hyderabad", "500034",
                "Banjara Hills Road No. 12, Hyderabad, Telangana",
                "Pioneering Midwifery & Natural Gentle Childbirth Center",
                4.9, 1420, "+91 40 4063 2000",
                "https://fernandez.foundation",
                "10% Discount on Natural Birth Suite Registration",
                "PLATINUM_ACCREDITED",
                "https://images.unsplash.com/photo-1586773860418-d37222d8fce3?auto=format&fit=crop&w=600&q=80"
        ));

        hospitalPartnerRepository.save(new HospitalPartner(null,
                "Fortis La Femme - Centre for Women",
                "Delhi", "110048",
                "Greater Kailash Part 2, New Delhi",
                "Bespoke Birthing Suites, Fetal Medicine & Neonatal Care",
                4.8, 760, "+91 11 4057 9400",
                "https://fortishealthcare.com",
                "Free Hospital Tour & Consultation with Lactation Expert",
                "PLATINUM_ACCREDITED",
                "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?auto=format&fit=crop&w=600&q=80"
        ));
    }

    private void seedNursePackages() {
        if (nursePackageRepository.count() > 0) return;

        nursePackageRepository.save(new NursePackage(null,
                "Trimester Care Companion",
                "Your personal senior female nurse on-call for daily questions, report deciphering, and symptoms support.",
                new BigDecimal("2999"), new BigDecimal("4499"),
                "3 Months Duration", "MOST POPULAR",
                "Dedicated certified nurse assigned exclusively to you throughout your current trimester. Never feel anxious about test results or late-night symptoms.",
                Arrays.asList(
                        "Direct WhatsApp access to your assigned Senior Maternal Nurse (Mon-Sat, 9am-9pm)",
                        "Weekly 30-min scheduled video wellness check-in",
                        "Instant explanation of ultrasound scan & blood report numbers",
                        "Trimester-specific nutrition, hydration, and safe movement advice",
                        "Preparation of personalized questions to take to your OB-GYN visit"
                )
        ));

        nursePackageRepository.save(new NursePackage(null,
                "Full 9-Months 'Maa Sakhi' Complete Journey",
                "Complete handholding from positive pregnancy test until 40 days postpartum.",
                new BigDecimal("7999"), new BigDecimal("11999"),
                "Full 9 Months + Postpartum", "COMPLETE PEACE OF MIND",
                "Our flagship white-glove maternal care service. A dedicated senior nurse walks with you through every week, scan, doctor appointment, and early newborn breastfeeding days.",
                Arrays.asList(
                        "Dedicated Primary Nurse + Back-up Night Emergency Helpline",
                        "Unlimited WhatsApp doubt clearing & emotional reassurance",
                        "Bi-weekly wellness reviews with Trimester Milestones tracking",
                        "Assistance choosing the best maternity hospital in your locality",
                        "Pre-labor hospital bag review & birth plan consultation",
                        "40-day postpartum recovery, breastfeeding, and newborn care guidance"
                )
        ));

        nursePackageRepository.save(new NursePackage(null,
                "Month-by-Month Flexible Support",
                "Flexible month-to-month care without long-term commitment.",
                new BigDecimal("1199"), new BigDecimal("1699"),
                "1 Month Duration", "FLEXIBLE",
                "Ideal for mothers entering a specific milestone (e.g., month 3 nausea peak, or month 6 anomaly scan month) who want immediate access to a trained maternal care nurse.",
                Arrays.asList(
                        "WhatsApp query support with verified nurse",
                        "2 structured phone consults per month",
                        "Symptom tracking & diet guidance",
                        "Doctor visit prep checklist review"
                )
        ));
    }

    private void seedDoctorSessions() {
        if (doctorSessionRepository.count() > 0) return;

        doctorSessionRepository.save(new DoctorSession(null,
                "Dr. Ananya Sharma, MD (OB-GYN)",
                "Senior Consultant Obstetrician • 18+ Years Experience",
                "Cloudnine Hospital, Bengaluru",
                "Demystifying Your 1st Trimester: Nutrition, Morning Sickness & Early Scans",
                "Live interactive Q&A discussing safe nausea relief, folic acid requirements, managing early fatigue, and what to expect during your 12-week NT scan.",
                PregnancyStage.MONTH_3,
                "Saturday, 5:00 PM IST",
                60, 240,
                "https://images.unsplash.com/photo-1559839734-2b71ea197ec2?auto=format&fit=crop&w=600&q=80",
                "https://zoom.us/j/demo123456",
                "UPCOMING"
        ));

        doctorSessionRepository.save(new DoctorSession(null,
                "Dr. Priya Ramanathan, MD, DNB",
                "Fetal Medicine Specialist • Specialist in 4D Ultrasound",
                "Apollo Cradle, Mumbai",
                "Navigating Month 6: The Anomaly Scan, Pelvic Pain & Safe Sleeping",
                "Learn why the Level-II Anomaly Scan is vital, how to distinguish normal round ligament twinges from warning signs, and side-sleeping posture ergonomics.",
                PregnancyStage.MONTH_6,
                "Sunday, 11:30 AM IST",
                60, 312,
                "https://images.unsplash.com/photo-1594824813500-2921516e537c?auto=format&fit=crop&w=600&q=80",
                "https://zoom.us/j/demo234567",
                "UPCOMING"
        ));

        doctorSessionRepository.save(new DoctorSession(null,
                "Sister Meenakshi Sundaram, RN, RM",
                "Certified Nurse Midwife & International Board Lactation Consultant (IBCLC)",
                "Fernandez Foundation, Hyderabad",
                "Month 9 Labor Readiness: Birthing Positions, Breathing & Hospital Bag Packing",
                "Practical masterclass on recognizing early labor contractions, breathing techniques to manage pain, perineal massage demonstration, and newborn first latch.",
                PregnancyStage.MONTH_9,
                "Wednesday, 6:30 PM IST",
                75, 420,
                "https://images.unsplash.com/photo-1579684385127-1ef15d508118?auto=format&fit=crop&w=600&q=80",
                "https://zoom.us/j/demo345678",
                "UPCOMING"
        ));
    }
}
