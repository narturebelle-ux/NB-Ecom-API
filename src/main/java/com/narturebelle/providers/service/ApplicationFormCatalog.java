package com.narturebelle.providers.service;

import com.narturebelle.providers.dto.FormDtos.FormOption;
import com.narturebelle.providers.dto.FormDtos.FormQuestion;
import com.narturebelle.providers.dto.FormDtos.FormSection;
import com.narturebelle.providers.dto.FormDtos.QuestionType;
import com.narturebelle.model.Role;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The approved application questions (document section 4). The UI renders whatever this returns, so one
 * common form covers every profession, and a question only shows when its parent has a matching answer.
 */
@Component
public class ApplicationFormCatalog {

    public static final String KEY_HOLDS_REGISTRATION = "holdsRegistration";
    public static final String KEY_LEGAL_NAME = "legalName";
    public static final String KEY_REGISTRATION_NUMBER = "registrationNumber";
    public static final String KEY_ALLIED_HEALTH_ID = "alliedHealthId";
    public static final String KEY_REGISTRATION_EXPIRY = "registrationExpiry";
    public static final String KEY_REGISTRATION_CERTIFICATE = "registrationCertificate";
    public static final String KEY_NAME_PROOF = "nameProof";
    public static final String KEY_DISPLAY_NAME = "displayName";
    public static final String KEY_BIO = "bio";
    public static final String KEY_CITY = "city";
    public static final String KEY_LANGUAGES = "languages";
    public static final String KEY_COUNCIL = "council";

    /** Roles someone can apply for, in dropdown order. */
    public static final Map<Role, String> PROFESSIONS = orderedProfessions();

    private static final List<String> STATES = List.of("Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
            "Delhi", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala",
            "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan",
            "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal");

    private static final String YES = "YES";
    private static final String NO = "NO";

    private static Map<Role, String> orderedProfessions() {
        Map<Role, String> map = new java.util.LinkedHashMap<>();
        map.put(Role.GYNECOLOGIST, "Gynecologist");
        map.put(Role.PEDIATRICIAN, "Pediatrician");
        map.put(Role.CLINICAL_PSYCHOLOGIST, "Clinical Psychologist");
        map.put(Role.REGISTERED_NURSE, "Registered Nurse");
        map.put(Role.POSTPARTUM_PHYSIOTHERAPIST, "Postpartum Physiotherapist");
        map.put(Role.NUTRITIONIST, "Nutritionist");
        map.put(Role.LACTATION_CONSULTANT, "Lactation Consultant");
        return map;
    }

    /** Only registered medical practitioners prescribe (customer-approved limit). */
    public boolean canPrescribe(Role profession) {
        return profession == Role.GYNECOLOGIST || profession == Role.PEDIATRICIAN;
    }

    public List<FormSection> sections(Role profession) {
        return List.of(eligibility(), personal(), experience(profession), professional(profession), declarations());
    }

    public Map<String, FormQuestion> questionsByKey(Role profession) {
        return sections(profession).stream()
                .flatMap(section -> section.questions().stream())
                .collect(Collectors.toMap(FormQuestion::key, q -> q, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    /** True if the question's parent chain is visible and each parent holds a triggering answer. */
    public boolean isVisible(FormQuestion question, Map<String, String> answers, Map<String, FormQuestion> byKey) {
        if (question.showWhenKey() == null) {
            return true;
        }
        FormQuestion parent = byKey.get(question.showWhenKey());
        if (parent == null || !isVisible(parent, answers, byKey)) {
            return false;
        }
        String answer = answers.get(question.showWhenKey());
        if (answer == null) {
            return false;
        }
        return Arrays.stream(answer.split("\n")).anyMatch(question.showWhenValues()::contains);
    }

    // ---- sections ----

    private FormSection eligibility() {
        return new FormSection("eligibility", "Eligibility", "A few questions about your practice.", List.of(
                yesNo(KEY_HOLDS_REGISTRATION, "Do you hold a current registration or certification for this profession?")
                        .hint("You can still apply without one, but an application without it cannot be approved.").build(),
                yesNo("everSuspended", "Has a licence ever been suspended or cancelled, or have you faced disciplinary action?").build(),
                text("suspensionCouncil", "Which council?").when("everSuspended", YES).build(),
                year("suspensionYear", "Year").when("everSuspended", YES).build(),
                text("suspensionStatus", "Current status").when("everSuspended", YES).build(),
                longText("suspensionDetails", "Details").when("everSuspended", YES).build(),
                file("suspensionOrder", "Upload the order").when("everSuspended", YES).build(),
                yesNo("criminalCase", "Any criminal case related to your practice?").build(),
                longText("criminalDetails", "Details").when("criminalCase", YES).build(),
                text("criminalStatus", "Current status").when("criminalCase", YES).build(),
                file("criminalDocument", "Upload a supporting document").when("criminalCase", YES).build(),
                yesNo("currentlyPractising", "Are you currently practising?").build(),
                date("lastPracticeDate", "Last practice date").when("currentlyPractising", NO).build(),
                text("notPractisingReason", "Reason").when("currentlyPractising", NO).build()));
    }

    private FormSection personal() {
        return new FormSection("personal", "Personal details", "These must match your registration and ID documents.", List.of(
                text(KEY_LEGAL_NAME, "Full legal name, as on your registration").build(),
                yesNo("nameChanged", "Has your name changed since registering?").build(),
                text("earlierName", "Earlier name").when("nameChanged", YES).build(),
                file(KEY_NAME_PROOF, "Upload proof of the name change").when("nameChanged", YES).build(),
                date("dateOfBirth", "Date of birth").build(),
                select("gender", "Gender", opts("FEMALE:Female", "MALE:Male", "OTHER:Other", "UNDISCLOSED:Prefer not to say")).build(),
                longText("address", "Address").build(),
                text(KEY_CITY, "City").build(),
                select("state", "State", values(STATES)).build(),
                text("pin", "PIN code").pattern("^[1-9][0-9]{5}$", "Enter a 6-digit PIN code.").build(),
                text("pan", "PAN number").pattern("^[A-Z]{5}[0-9]{4}[A-Z]$", "Enter a PAN like ABCDE1234F.").sensitive()
                        .hint("Stored encrypted. Only reviewers can see it.").build(),
                file("panCard", "Upload your PAN card").build(),
                select("photoIdType", "Photo ID type", opts("PASSPORT:Passport", "VOTER_ID:Voter ID", "DRIVING_LICENCE:Driving Licence", "MASKED_AADHAAR:Masked Aadhaar")).build(),
                file("photoId", "Upload your photo ID").hint("For Aadhaar, upload the masked version only.").build(),
                file("photo", "Recent photo").build()));
    }

    private FormSection experience(Role profession) {
        return new FormSection("experience", "Experience and care profile",
                "Mothers see your display name and bio once you are live. Your fee is fixed; experience is used for recommendations.", List.of(
                year("startYear", "Year you started practising").build(),
                number("experienceYears", "Years of experience in this profession").hint("A reviewer confirms this.").build(),
                select("workplaceType", "Where do you currently work?", opts("HOSPITAL:Hospital", "OWN_CLINIC:Own clinic", "INDEPENDENT:Independent", "NOT_WORKING:Not working")).build(),
                text("workplaceName", "Name of hospital or clinic").when("workplaceType", "HOSPITAL", "OWN_CLINIC").build(),
                text("workplaceCity", "City of hospital or clinic").when("workplaceType", "HOSPITAL", "OWN_CLINIC").build(),
                multi("careStages", "Which stages do you support?", stagesFor(profession)).build(),
                multi(KEY_LANGUAGES, "Languages", values(List.of("English", "Hindi", "Bengali", "Kannada", "Malayalam", "Marathi", "Tamil", "Telugu", "Gujarati", "Punjabi", "Urdu", "Odia"))).build(),
                file("cv", "CV").build(),
                text("referenceName", "Reference name").build(),
                text("referenceRole", "Reference role").build(),
                text("referencePhone", "Reference phone").pattern("^[+0-9 ()\\-]{7,20}$", "Enter a valid phone number.").build(),
                select("consultMode", "How will you consult?", opts("VIRTUAL:Virtual", "IN_PERSON:In-person", "BOTH:Both")).build(),
                select("inPersonLocation", "In-person location", opts("CLINIC:Clinic", "HOME_VISITS:Home visits", "BOTH:Both")).when("consultMode", "IN_PERSON", "BOTH").build(),
                text("clinicName", "Clinic name").when("inPersonLocation", "CLINIC", "BOTH").build(),
                longText("clinicAddress", "Clinic address").when("inPersonLocation", "CLINIC", "BOTH").build(),
                text("clinicPin", "Clinic PIN code").pattern("^[1-9][0-9]{5}$", "Enter a 6-digit PIN code.").when("inPersonLocation", "CLINIC", "BOTH").build(),
                text("homeVisitCities", "Cities covered for home visits").when("inPersonLocation", "HOME_VISITS", "BOTH").build(),
                file("policeVerification", "Upload police verification").when("inPersonLocation", "HOME_VISITS", "BOTH").build(),
                number("hoursPerWeek", "Hours available per week").build(),
                multi("preferredDays", "Preferred days", values(List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"))).build(),
                text(KEY_DISPLAY_NAME, "Display name").hint("For example, Dr. Asha Rao.").build(),
                longText(KEY_BIO, "Short bio").hint("Operations approves this before it is published.").build()));
    }

    private FormSection professional(Role profession) {
        List<FormQuestion> questions = switch (profession) {
            case GYNECOLOGIST -> concat(doctorQuestions(opts("MD_MS:MD/MS", "DNB:DNB", "DGO:DGO")), List.of(
                    yesNo("highRisk", "Do you manage high-risk pregnancies?").build(),
                    number("highRiskYears", "Years of high-risk experience").when("highRisk", YES).build(),
                    yesNo("performsUltrasound", "Do you perform ultrasounds?").build(),
                    text("pcpndtNumber", "PCPNDT registration number").when("performsUltrasound", YES).build(),
                    file("pcpndtCertificate", "Upload PCPNDT certificate").when("performsUltrasound", YES).build(),
                    yesNo("hospitalAttached", "Are you attached to a hospital for deliveries?").build(),
                    text("hospitalName", "Hospital name").when("hospitalAttached", YES).build()));
            case PEDIATRICIAN -> concat(doctorQuestions(opts("MD:MD", "DNB:DNB", "DCH:DCH")), List.of(
                    yesNo("newbornCare", "Do you have newborn care experience?").build(),
                    number("newbornYears", "Years of newborn care experience").when("newbornCare", YES).build(),
                    multi("ageGroups", "Which age groups do you see?", opts("NEWBORN:Newborn", "INFANT:Infant (up to 1 year)", "TODDLER:Toddler (1 to 3 years)", "CHILD:Child (3 to 7 years)")).build(),
                    yesNo("vaccinationGuidance", "Do you give vaccination guidance?").build()));
            case CLINICAL_PSYCHOLOGIST -> List.of(
                    text("qualification", "Qualification").build(),
                    file("qualificationCertificate", "Upload your qualification").build(),
                    registrationNumber("RCI CRR number"),
                    registrationExpiry("CRR expiry date"),
                    registrationCertificate("Upload your CRR certificate"),
                    yesNo("perinatalExperience", "Do you have perinatal mental health experience?").build(),
                    yesNo("crisisExperience", "Do you have crisis handling experience?").build());
            case REGISTERED_NURSE -> List.of(
                    select("qualification", "Nursing qualification", opts("GNM:GNM", "BSC:B.Sc", "POST_BASIC:Post-Basic", "MSC:M.Sc")).build(),
                    file("qualificationCertificate", "Upload your qualification").build(),
                    select(KEY_COUNCIL, "State nursing council", values(STATES)).when(KEY_HOLDS_REGISTRATION, YES).build(),
                    registrationNumber("Registration number"),
                    registrationExpiry("Registration expiry date"),
                    registrationCertificate("Upload your registration certificate"),
                    yesNo("isMidwife", "Are you a registered midwife?").build(),
                    file("midwifeCertificate", "Upload your midwife certificate").when("isMidwife", YES).build(),
                    yesNo("newbornCareExperience", "Do you have newborn care experience?").build());
            case POSTPARTUM_PHYSIOTHERAPIST -> List.of(
                    select("qualification", "Qualification", opts("BPT:BPT", "MPT:MPT")).build(),
                    file("qualificationCertificate", "Upload your qualification").build(),
                    yesNo("stateHasCouncil", "Does your state have a physiotherapy council?").when(KEY_HOLDS_REGISTRATION, YES).build(),
                    text(KEY_REGISTRATION_NUMBER, "State council registration number").when("stateHasCouncil", YES).build(),
                    file(KEY_REGISTRATION_CERTIFICATE, "Upload your registration certificate").when("stateHasCouncil", YES).build(),
                    text(KEY_ALLIED_HEALTH_ID, "National allied health ID").when("stateHasCouncil", NO).build(),
                    yesNo("womensHealthTraining", "Do you have women's health training?").build(),
                    file("trainingCertificate", "Upload your training certificate").when("womensHealthTraining", YES).build());
            case NUTRITIONIST -> List.of(
                    text("qualification", "Degree in nutrition or dietetics").build(),
                    file("qualificationCertificate", "Upload your degree").build(),
                    yesNo("registeredDietitian", "Are you a Registered Dietitian?").when(KEY_HOLDS_REGISTRATION, YES).build(),
                    text(KEY_REGISTRATION_NUMBER, "Registered Dietitian number").when("registeredDietitian", YES).build(),
                    file(KEY_REGISTRATION_CERTIFICATE, "Upload your RD certificate").when("registeredDietitian", YES).build(),
                    text(KEY_ALLIED_HEALTH_ID, "National allied health ID, if you have one").optional().build(),
                    multi("nutritionExperience", "Where do you have nutrition experience?", opts("PREGNANCY:Pregnancy", "LACTATION:Lactation", "CHILD:Child nutrition")).build());
            case LACTATION_CONSULTANT -> List.of(
                    select("certificationType", "Certification type", opts("IBCLC:IBCLC", "CLC:CLC", "OTHER:Other")).when(KEY_HOLDS_REGISTRATION, YES).build(),
                    registrationNumber("Certification number"),
                    registrationExpiry("Certification expiry date"),
                    registrationCertificate("Upload your certificate"),
                    yesNo("alsoNurseOrDoctor", "Are you also a nurse or doctor?").build(),
                    text("otherRegistrationNumber", "Nursing or medical registration number").when("alsoNurseOrDoctor", YES).build(),
                    number("practicalHours", "Hours of hands-on experience").build());
            default -> throw new IllegalArgumentException(profession + " is not a profession.");
        };
        return new FormSection("professional", PROFESSIONS.get(profession) + " details", "Your qualifications and registration.", questions);
    }

    private List<FormQuestion> doctorQuestions(List<FormOption> specialistDegrees) {
        List<String> councils = new ArrayList<>(List.of("National Medical Commission"));
        councils.addAll(STATES.stream().map(state -> state + " Medical Council").toList());
        return List.of(
                select(KEY_COUNCIL, "Which council are you registered with?", values(councils)).when(KEY_HOLDS_REGISTRATION, YES).build(),
                registrationNumber("Registration number"),
                year("registrationYear", "Registration year").when(KEY_HOLDS_REGISTRATION, YES).build(),
                registrationCertificate("Upload your registration certificate"),
                text("nmrId", "National Medical Register ID, if you have one").optional().when(KEY_HOLDS_REGISTRATION, YES).build(),
                yesNo("hasRenewalDate", "Does your certificate have a renewal date?").when(KEY_HOLDS_REGISTRATION, YES).build(),
                date(KEY_REGISTRATION_EXPIRY, "Renewal date").when("hasRenewalDate", YES).build(),
                text("mbbsInstitute", "MBBS institute").build(),
                year("mbbsYear", "MBBS year").build(),
                file("mbbsDegree", "Upload your MBBS degree").build(),
                select("specialistDegree", "Specialist degree", specialistDegrees).build(),
                file("specialistCertificate", "Upload your specialist degree").build(),
                yesNo("specialistRecorded", "Is the specialist degree recorded with your council?").build(),
                yesNo("qualifiedAbroad", "Did you qualify outside India?").build(),
                text("qualifiedCountry", "Country").when("qualifiedAbroad", YES).build(),
                file("abroadProof", "Upload Indian registration or screening proof").when("qualifiedAbroad", YES).build(),
                file("prescriptionSignature", "Signature for prescriptions").build());
    }

    private FormSection declarations() {
        return new FormSection("declarations", "Declarations", "Please read and confirm.", List.of(
                yesNo("financialTies", "Do you have financial ties to any product company, hospital or lab?").build(),
                text("tieWho", "Who with?").when("financialTies", YES).build(),
                text("tieKind", "What kind of tie?").when("financialTies", YES).build(),
                confirm("declTrue", "I confirm that everything I have entered is true.").build(),
                confirm("declVerify", "I agree to NartureBelle checking these details with registers and my references.").build(),
                confirm("declNoSex", "I will not reveal or help determine a baby's sex.").build()));
    }

    private List<FormOption> stagesFor(Role profession) {
        return switch (profession) {
            case GYNECOLOGIST -> opts("PREGNANCY:Pregnancy", "POSTPARTUM:Postpartum");
            case PEDIATRICIAN -> opts("CHILD:Child 0-7");
            default -> opts("PREGNANCY:Pregnancy", "POSTPARTUM:Postpartum", "CHILD:Child 0-7");
        };
    }

    private FormQuestion registrationNumber(String label) {
        return text(KEY_REGISTRATION_NUMBER, label).when(KEY_HOLDS_REGISTRATION, YES).build();
    }

    private FormQuestion registrationExpiry(String label) {
        return date(KEY_REGISTRATION_EXPIRY, label).when(KEY_HOLDS_REGISTRATION, YES).build();
    }

    private FormQuestion registrationCertificate(String label) {
        return file(KEY_REGISTRATION_CERTIFICATE, label).when(KEY_HOLDS_REGISTRATION, YES).build();
    }

    // ---- builders ----

    private static List<FormQuestion> concat(List<FormQuestion> a, List<FormQuestion> b) {
        List<FormQuestion> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static List<FormOption> opts(String... valueColonLabel) {
        return Arrays.stream(valueColonLabel).map(s -> {
            int colon = s.indexOf(':');
            return new FormOption(s.substring(0, colon), s.substring(colon + 1));
        }).toList();
    }

    private static List<FormOption> values(List<String> values) {
        return values.stream().map(v -> new FormOption(v, v)).toList();
    }

    private static Builder text(String key, String label) { return new Builder(key, label, QuestionType.TEXT); }
    private static Builder longText(String key, String label) { return new Builder(key, label, QuestionType.LONG_TEXT); }
    private static Builder number(String key, String label) { return new Builder(key, label, QuestionType.NUMBER); }
    private static Builder year(String key, String label) { return new Builder(key, label, QuestionType.YEAR); }
    private static Builder date(String key, String label) { return new Builder(key, label, QuestionType.DATE); }
    private static Builder file(String key, String label) { return new Builder(key, label, QuestionType.FILE); }
    private static Builder confirm(String key, String label) { return new Builder(key, label, QuestionType.CONFIRM); }
    private static Builder yesNo(String key, String label) {
        return new Builder(key, label, QuestionType.YES_NO).options(opts("YES:Yes", "NO:No"));
    }
    private static Builder select(String key, String label, List<FormOption> options) {
        return new Builder(key, label, QuestionType.SELECT).options(options);
    }
    private static Builder multi(String key, String label, List<FormOption> options) {
        return new Builder(key, label, QuestionType.MULTI_SELECT).options(options);
    }

    private static final class Builder {
        private final String key;
        private final String label;
        private final QuestionType type;
        private List<FormOption> options = List.of();
        private boolean required = true;
        private boolean sensitive;
        private String hint;
        private String showWhenKey;
        private Set<String> showWhenValues = Set.of();
        private String pattern;
        private String patternMessage;

        private Builder(String key, String label, QuestionType type) {
            this.key = key;
            this.label = label;
            this.type = type;
        }

        private Builder options(List<FormOption> options) { this.options = options; return this; }
        private Builder optional() { this.required = false; return this; }
        private Builder sensitive() { this.sensitive = true; return this; }
        private Builder hint(String hint) { this.hint = hint; return this; }
        private Builder pattern(String pattern, String message) { this.pattern = pattern; this.patternMessage = message; return this; }
        private Builder when(String parentKey, String... parentValues) {
            this.showWhenKey = parentKey;
            this.showWhenValues = Set.of(parentValues);
            return this;
        }

        private FormQuestion build() {
            return new FormQuestion(key, label, type, options, required, sensitive, hint, showWhenKey, showWhenValues, pattern, patternMessage);
        }
    }
}
