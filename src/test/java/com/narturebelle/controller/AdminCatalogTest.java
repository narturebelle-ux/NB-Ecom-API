package com.narturebelle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.HospitalReferral;
import com.narturebelle.model.ProductRecommendationDismissal;
import com.narturebelle.model.ReferralStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.HospitalReferralRepository;
import com.narturebelle.repository.ProductRecommendationDismissalRepository;
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

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers admin CRUD for hospitals, products and checklist tasks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCatalogTest {

    private static final String TOKEN_ADMIN = "catalog-token-admin";
    private static final String TOKEN_MOTHER = "catalog-token-mother";

    private static final String HOSPITAL = "{\"name\":\"Real Maternity Hospital\",\"city\":\"Pune\",\"pincode\":\"411001\",\"rating\":4.5}";
    private static final String PRODUCT = "{\"name\":\"Real Prenatal\",\"price\":499,\"stage\":\"MONTH_3\",\"tier\":\"OPTIONAL\","
            + "\"category\":\"Nutrition\",\"keyBenefits\":[\"Gentle iron\"]}";
    private static final String CHECKLIST = "{\"stage\":\"MONTH_6\",\"testName\":\"Anomaly scan\",\"weeksDue\":\"Weeks 18-22\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private HospitalReferralRepository hospitalReferralRepository;
    @Autowired private ProductRecommendationDismissalRepository dismissalRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long motherId;

    @BeforeEach
    void setUp() throws Exception {
        hospitalReferralRepository.deleteAll();
        dismissalRepository.deleteAll();
        userRepository.deleteAll();
        createUser("uid-catalog-admin", "catalog-admin@example.com", Role.SUPER_ADMIN);
        motherId = createUser("uid-catalog-mother", "catalog-mother@example.com", Role.MOTHER);

        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-catalog-admin", "catalog-admin@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_MOTHER))
                .thenReturn(new VerifiedFirebaseToken("uid-catalog-mother", "catalog-mother@example.com", true, null, null));
    }

    private Long createUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        return userRepository.save(user).getId();
    }

    private long create(String path, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/" + path).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void nonAdmin_cannotWrite() throws Exception {
        mockMvc.perform(post("/api/v1/admin/hospitals").header("Authorization", "Bearer " + TOKEN_MOTHER)
                        .contentType(MediaType.APPLICATION_JSON).content(HOSPITAL))
                .andExpect(status().isForbidden());
    }

    @Test
    void hospital_createUpdateDelete_visibleOnPublicEndpoint() throws Exception {
        long id = create("hospitals", HOSPITAL);
        mockMvc.perform(get("/api/v1/hospitals/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Real Maternity Hospital"));

        mockMvc.perform(put("/api/v1/admin/hospitals/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(HOSPITAL.replace("Pune", "Mumbai")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Mumbai"));

        mockMvc.perform(delete("/api/v1/admin/hospitals/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/hospitals/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/hospitals/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOSPITAL_NOT_FOUND"));
    }

    @Test
    void hospital_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/hospitals").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"Pune\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void hospital_withReferral_cannotBeDeleted() throws Exception {
        long id = create("hospitals", HOSPITAL);
        HospitalReferral referral = new HospitalReferral();
        referral.setUserId(motherId);
        referral.setHospitalId(id);
        referral.setCode("NB-TESTCODE");
        referral.setStatus(ReferralStatus.REQUESTED);
        hospitalReferralRepository.save(referral);

        mockMvc.perform(delete("/api/v1/admin/hospitals/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_IN_USE"));
    }

    @Test
    void product_createUpdateDelete_andInUseGuard() throws Exception {
        long id = create("products", PRODUCT);
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyBenefits[0]").value("Gentle iron"));

        mockMvc.perform(put("/api/v1/admin/products/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT.replace("499", "549")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(549));

        ProductRecommendationDismissal dismissal = new ProductRecommendationDismissal();
        dismissal.setUserId(motherId);
        dismissal.setProductId(id);
        dismissalRepository.save(dismissal);
        mockMvc.perform(delete("/api/v1/admin/products/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isConflict());

        dismissalRepository.deleteAll();
        mockMvc.perform(delete("/api/v1/admin/products/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void product_missingStageOrTier_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"price\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.stage").exists())
                .andExpect(jsonPath("$.fields.tier").exists());
    }

    @Test
    void checklist_createUpdateDelete() throws Exception {
        long id = create("checklists", CHECKLIST);
        mockMvc.perform(put("/api/v1/admin/checklists/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(CHECKLIST.replace("Weeks 18-22", "Weeks 19-21")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeksDue").value("Weeks 19-21"));
        mockMvc.perform(delete("/api/v1/admin/checklists/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/admin/checklists/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(CHECKLIST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHECKLIST_TASK_NOT_FOUND"));
    }
}
