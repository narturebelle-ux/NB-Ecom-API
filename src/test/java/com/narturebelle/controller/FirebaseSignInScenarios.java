package com.narturebelle.controller;

import com.narturebelle.model.AuditEvent;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.AuditEventRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG-37: what POST /auth/firebase does when the token's UID isn't found. Runs on H2 and on real Postgres,
 * because the two differ on what happens after a unique rule refuses a row. Every test uses its own
 * emails and phones, since the Postgres database carries the migrations' data and is shared between tests.
 */
abstract class FirebaseSignInScenarios {

    @Autowired
    protected MockMvc mockMvc;

    @SpyBean
    protected UserRepository userRepository;

    @Autowired
    protected AuditEventRepository auditEventRepository;

    @MockBean
    protected FirebaseTokenVerifier firebaseTokenVerifier;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String email() {
        return unique() + "@example.com";
    }

    private static String phone() {
        return "+91" + unique().replaceAll("[^0-9]", "") + System.nanoTime();
    }

    private User storedUser(String uid, String email, Boolean emailVerified, String phone) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(emailVerified);
        user.setPhone(phone);
        user.setFirstName("Stored");
        user.setLastName("Name");
        user.setStage(PregnancyStage.MONTH_6);
        user.setRoles(Set.of(Role.MOTHER));
        return userRepository.save(user);
    }

    /** Mockito stubbing isn't thread-safe, so the simultaneous tests stub their tokens before starting. */
    private String tokenFor(String uid, String email, boolean emailVerified, String phone) throws Exception {
        String token = "token-" + unique();
        when(firebaseTokenVerifier.verify(token))
                .thenReturn(new VerifiedFirebaseToken(uid, email, emailVerified, phone, "Token Name"));
        return token;
    }

    private ResultActions signIn(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer " + token));
    }

    private ResultActions signIn(String uid, String email, boolean emailVerified, String phone) throws Exception {
        return signIn(tokenFor(uid, email, emailVerified, phone));
    }

    private int statusOf(String token) throws Exception {
        return signIn(token).andReturn().getResponse().getStatus();
    }

    private List<AuditEvent> auditEventsFor(User user, String action) {
        return auditEventRepository.findAll().stream()
                .filter(event -> action.equals(event.getAction()) && String.valueOf(user.getId()).equals(event.getSubjectId()))
                .toList();
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    @Test
    void uidFound_updatesFromTheToken_andKeepsTheName() throws Exception {
        String uid = "uid-" + unique();
        String newEmail = email();
        String newPhone = phone();
        User stored = storedUser(uid, email(), false, null);

        signIn(uid, newEmail, true, newPhone)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stored.getId()))
                .andExpect(jsonPath("$.firstName").value("Stored"))
                .andExpect(jsonPath("$.email").value(newEmail));

        User after = reload(stored);
        assertThat(after.getEmailVerified()).isTrue();
        assertThat(after.getPhone()).isEqualTo(newPhone);
        assertThat(auditEventsFor(stored, "USER_FIREBASE_RELINKED")).isEmpty();
    }

    @Test
    void newUidSameEmail_bothVerified_relinksKeepingTheData_andAuditsWithoutEmailOrToken() throws Exception {
        String email = email();
        User stored = storedUser("uid-old-" + unique(), email, true, null);
        String newUid = "uid-new-" + unique();
        long usersBefore = userRepository.count();

        signIn(newUid, email, true, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stored.getId()))
                .andExpect(jsonPath("$.firebaseUid").value(newUid))
                .andExpect(jsonPath("$.firstName").value("Stored"));

        User after = reload(stored);
        assertThat(after.getFirebaseUid()).isEqualTo(newUid);
        assertThat(after.getStage()).isEqualTo(PregnancyStage.MONTH_6);
        assertThat(after.getRoles()).containsExactly(Role.MOTHER);
        assertThat(userRepository.count()).isEqualTo(usersBefore);

        List<AuditEvent> events = auditEventsFor(stored, "USER_FIREBASE_RELINKED");
        assertThat(events).hasSize(1);
        String written = String.valueOf(events.get(0).getReason()) + events.get(0).getMetadataJson();
        assertThat(written).doesNotContain(email).doesNotContain("token-").doesNotContain("@");
    }

    @Test
    void emailDiffersOnlyByCase_bothVerified_relinksTheSameUser() throws Exception {
        String email = email();
        User stored = storedUser("uid-old-" + unique(), email.toUpperCase(), true, null);
        String newUid = "uid-new-" + unique();

        signIn(newUid, email, true, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stored.getId()));

        assertThat(reload(stored).getFirebaseUid()).isEqualTo(newUid);
    }

    @Test
    void tokenEmailUnverified_neverRelinks_returns409() throws Exception {
        String email = email();
        String oldUid = "uid-old-" + unique();
        User stored = storedUser(oldUid, email, true, null);

        signIn("uid-attacker-" + unique(), email, false, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        User after = reload(stored);
        assertThat(after.getFirebaseUid()).isEqualTo(oldUid);
        assertThat(after.getEmailVerified()).isTrue();
        assertThat(auditEventsFor(stored, "USER_FIREBASE_RELINKED")).isEmpty();
    }

    @Test
    void storedEmailUnverified_neverRelinks_returns409() throws Exception {
        String email = email();
        String oldUid = "uid-old-" + unique();
        User stored = storedUser(oldUid, email, false, null);

        signIn("uid-new-" + unique(), email, true, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_CONFLICT"));

        User after = reload(stored);
        assertThat(after.getFirebaseUid()).isEqualTo(oldUid);
        assertThat(after.getEmailVerified()).isFalse();
    }

    @Test
    void storedVerificationMissing_countsAsUnverified() throws Exception {
        String email = email();
        String oldUid = "uid-old-" + unique();
        User stored = storedUser(oldUid, email, null, null);

        signIn("uid-new-" + unique(), email, true, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_CONFLICT"));

        assertThat(reload(stored).getFirebaseUid()).isEqualTo(oldUid);
    }

    @Test
    void emailMatchesTwoRowsByCase_isFlagged_neverRelinked_andNeitherRowChanges() throws Exception {
        String email = email();
        String lowerUid = "uid-lower-" + unique();
        String upperUid = "uid-upper-" + unique();
        User lower = storedUser(lowerUid, email, true, null);
        User upper = storedUser(upperUid, email.toUpperCase(), true, null);

        signIn("uid-new-" + unique(), email, true, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_CONFLICT"));

        assertThat(reload(lower).getFirebaseUid()).isEqualTo(lowerUid);
        assertThat(reload(upper).getFirebaseUid()).isEqualTo(upperUid);
    }

    @Test
    void neitherFound_createsTheUser_andAuditsOnce() throws Exception {
        String uid = "uid-new-" + unique();

        signIn(uid, email(), false, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firebaseUid").value(uid))
                .andExpect(jsonPath("$.firstName").value("Token"));

        User created = userRepository.findByFirebaseUid(uid).orElseThrow();
        assertThat(created.getRoles()).containsExactly(Role.MOTHER);
        assertThat(auditEventsFor(created, "USER_CREATED")).hasSize(1);
    }

    @Test
    void newUser_phoneOnAnotherAccount_returns409_andCreatesNothing() throws Exception {
        String phone = phone();
        String oldUid = "uid-old-" + unique();
        User stored = storedUser(oldUid, email(), true, phone);
        String newUid = "uid-new-" + unique();

        signIn(newUid, null, false, phone)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PHONE_CONFLICT"));

        assertThat(userRepository.findByFirebaseUid(newUid)).isEmpty();
        assertThat(reload(stored).getFirebaseUid()).isEqualTo(oldUid);
    }

    @Test
    void relink_phoneOnAnotherAccount_stillRelinks_butLeavesThePhonesAlone() throws Exception {
        String email = email();
        String ownPhone = phone();
        String otherPhone = phone();
        User stored = storedUser("uid-old-" + unique(), email, true, ownPhone);
        User other = storedUser("uid-other-" + unique(), email(), true, otherPhone);
        String newUid = "uid-new-" + unique();

        signIn(newUid, email, true, otherPhone)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stored.getId()));

        User after = reload(stored);
        assertThat(after.getFirebaseUid()).isEqualTo(newUid);
        assertThat(after.getPhone()).isEqualTo(ownPhone);
        assertThat(reload(other).getPhone()).isEqualTo(otherPhone);
    }

    @Test
    void uidFound_phoneOnAnotherAccount_signsIn_andLeavesThePhonesAlone() throws Exception {
        String uid = "uid-" + unique();
        String otherPhone = phone();
        User stored = storedUser(uid, email(), true, null);
        User other = storedUser("uid-other-" + unique(), email(), true, otherPhone);

        signIn(uid, stored.getEmail(), true, otherPhone)
                .andExpect(status().isOk());

        assertThat(reload(stored).getPhone()).isNull();
        assertThat(reload(stored).getPhoneVerified()).isNotEqualTo(Boolean.TRUE);
        assertThat(reload(other).getPhone()).isEqualTo(otherPhone);
    }

    @Test
    void uidFound_emailOnAnotherAccount_signsIn_andDoesNotTakeOverTheVerifiedStatus() throws Exception {
        String uid = "uid-" + unique();
        String ownEmail = email();
        String otherEmail = email();
        User stored = storedUser(uid, ownEmail, false, null);
        User other = storedUser("uid-other-" + unique(), otherEmail, true, null);

        signIn(uid, otherEmail, true, null)
                .andExpect(status().isOk());

        User after = reload(stored);
        assertThat(after.getEmail()).isEqualTo(ownEmail);
        assertThat(after.getEmailVerified()).isFalse();
        assertThat(reload(other).getEmail()).isEqualTo(otherEmail);
    }

    @Test
    void simultaneousFirstSignIns_sameUid_allSucceed_andCreateOneUser() throws Exception {
        String uid = "uid-race-" + unique();
        String email = email();
        String phone = phone();

        String token = tokenFor(uid, email, false, phone);

        List<Integer> statuses = runTogether(List.of(token, token, token, token, token, token, token, token));

        assertThat(statuses).containsOnly(200);
        User created = userRepository.findByFirebaseUid(uid).orElseThrow();
        assertThat(created.getEmail()).isEqualTo(email);
        assertThat(auditEventsFor(created, "USER_CREATED")).hasSize(1);
    }

    @Test
    void simultaneousRelinks_sameUid_allSucceed_andMoveTheOneUser() throws Exception {
        String email = email();
        User stored = storedUser("uid-old-" + unique(), email, true, null);
        String newUid = "uid-new-" + unique();
        long usersBefore = userRepository.count();

        String token = tokenFor(newUid, email, true, null);

        List<Integer> statuses = runTogether(List.of(token, token, token, token, token, token, token, token));

        assertThat(statuses).containsOnly(200);
        assertThat(reload(stored).getFirebaseUid()).isEqualTo(newUid);
        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    void simultaneousFirstSignIns_differentUidsSameUnverifiedEmail_oneWins_theRestGet409() throws Exception {
        String email = email();

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tokens.add(tokenFor("uid-race-" + unique(), email, false, null));
        }

        List<Integer> statuses = runTogether(tokens);

        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(userRepository.findAllByEmailIgnoreCase(email)).hasSize(1);
    }

    /** Makes the controller's UID lookup miss, as if a copy of this sign-in saved the row just after it. */
    private void missTheControllersUidLookupOnce(String uid) {
        Optional<User> found = userRepository.findByFirebaseUid(uid);
        doReturn(found) // The authentication filter's lookup.
                .doReturn(Optional.empty())
                .doReturn(found)
                .when(userRepository).findByFirebaseUid(uid);
    }

    @Test
    void rowSavedJustAfterTheUidLookup_byEmail_isThatSameUser_not409() throws Exception {
        String uid = "uid-" + unique();
        String email = email();
        User stored = storedUser(uid, email, false, null);
        missTheControllersUidLookupOnce(uid);

        signIn(uid, email, false, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stored.getId()));

        assertThat(auditEventsFor(stored, "USER_FIREBASE_RELINKED")).isEmpty();
    }

    @Test
    void rowSavedJustAfterTheUidLookup_byPhone_isThatSameUser_not409() throws Exception {
        String uid = "uid-" + unique();
        String phone = phone();
        User stored = storedUser(uid, null, false, phone);
        missTheControllersUidLookupOnce(uid);

        signIn(uid, null, false, phone)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stored.getId()));
    }

    @Test
    void emailTakenJustAfterTheLookup_isRefused_neverANewUserWithoutAnEmail() throws Exception {
        String email = email();
        User stored = storedUser("uid-other-" + unique(), email, false, null);
        doReturn(List.of()).doReturn(List.of(stored)).when(userRepository).findAllByEmailIgnoreCase(email);
        String newUid = "uid-new-" + unique();

        signIn(newUid, email, false, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_CONFLICT"));

        assertThat(userRepository.findByFirebaseUid(newUid)).isEmpty();
    }

    @Test
    void phoneTakenJustAfterTheLookup_isRefused_neverANewUserWithoutAPhone() throws Exception {
        String phone = phone();
        User stored = storedUser("uid-other-" + unique(), email(), true, phone);
        doReturn(Optional.empty()).doReturn(Optional.of(stored)).when(userRepository).findByPhone(phone);
        String newUid = "uid-new-" + unique();

        signIn(newUid, email(), true, phone)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PHONE_CONFLICT"));

        assertThat(userRepository.findByFirebaseUid(newUid)).isEmpty();
    }

    private List<Integer> runTogether(List<String> tokens) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return statusOf(token);
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
