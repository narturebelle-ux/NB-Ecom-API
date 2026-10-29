# NB-Ecomm-API — Handoff: Full Change History & Current State

**Audience:** a Claude agent (or any engineer) picking up this repo cold, to produce API/DB improvement recommendations.

This document separates **"already in the repo before this session touched anything"** from **"changes made during this session"**, verified against `git diff` rather than memory, so the picture is accurate.

---

## 0. Repo identity
- Spring Boot 3.3.4, Java 21, Gradle. Domain: maternity-care platform backend ("NartureBelle") — products, pregnancy checklists, doctor live sessions, nurse-companion bookings, hospital partner directory, lead capture.
- Branches on `origin`: `main` (GitHub default, now up to date), `master`, `codestepup`, `Added_Split_IO`.
- Git identity used for commits in this session: `narturebelle-ux` / `narturebelle@gmail.com`.

## 1. True baseline — last real commits before this session
Three commits existed before any of this work:
1. `bb26583` "Initial Commit" — Maven project (`pom.xml`), all controllers/models/repos/DTOs for the domains above, H2 only, no Postgres.
2. `a1fc141` "Added Firebase" — added `FirebaseConfig`, `AuthController` Firebase login path, `FirebaseLoginRequest` DTO.
3. `f86ab77` "Added Gradle" — migrated Maven → Gradle (`build.gradle`, wrapper, `settings.gradle`), added `FeatureToggleProperties`/`FeatureToggleController`, split `application.properties` into `application.yml` + `application-{prod,stage,test}.yml`.

**Notable baseline weaknesses at `f86ab77`** (i.e. what was actually committed, not what was later seen in the working tree):
- `application-prod.yml` had a **hardcoded MySQL fallback with no auth**: `url: ${SPRING_DATASOURCE_URL:jdbc:mysql://${DB_HOST:localhost}:3306/narturebelle...}`, `username: ${SPRING_DATASOURCE_USERNAME:root}`, `password: ${SPRING_DATASOURCE_PASSWORD:}` — i.e. if the env vars were ever missing in production, it would silently try to connect to local MySQL as `root` with an empty password.
- `CorsConfig` had `allowedOriginPatterns("*")` hardcoded, no override.
- `User` had only a flat `name` field (no `firstName`/`lastName`).
- `target/` (including a ~49–100MB Maven-built jar) was committed to git.
- No tests (`src/test` never existed in any commit).
- No Spring Security, no real OTP, no order/cart/payment flow. (Still true today — see §5.)

## 2. Changes already in the working tree when this session started — **not made by this session**
At the very first `git status` of this session, these were already modified/added/deleted in the working directory, uncommitted, with no record of who/what made them:

| File | Nature of change |
|---|---|
| `src/main/java/com/narturebelle/config/CorsConfig.java` | Added `@Value("${cors.allowed-origins:*}")` field; origins now configurable instead of hardcoded `"*"`. |
| `src/main/java/com/narturebelle/config/FirebaseConfig.java` | Rewrote init logic: tries credentials file first, then falls back to `GoogleCredentials.getApplicationDefault()` (for Cloud Run IAM), only then logs the dev-fallback warning. |
| `src/main/java/com/narturebelle/model/User.java` | Added `firstName`/`lastName` fields with `@PrePersist`/`@PreUpdate` sync logic to keep legacy `name` field populated from them (or vice versa). |
| `src/main/java/com/narturebelle/model/Product.java` | Added `fetch = FetchType.EAGER` to the two `@ElementCollection` fields (`keyBenefits`, `targetSymptoms`) — likely a fix for `LazyInitializationException` given `open-in-view: false`. |
| `src/main/java/com/narturebelle/controller/UserController.java` | **New file** — `GET /api/v1/users/{id}` and `PUT /api/v1/users/{id}` (profile update, with phone-uniqueness check). No ownership/auth check — any caller can read/edit any user by ID. |
| `src/main/java/com/narturebelle/dto/UpdateProfileRequest.java` | **New file** — DTO backing the above PUT endpoint (`firstName`, `lastName`, `phone`, `stage`). |
| `pom.xml` | Deleted (consistent with the earlier Gradle migration, but this specific deletion happened in the working tree, uncommitted, before this session). |
| `Dockerfile`, `.dockerignore` | **New files**, already present untracked. Dockerfile does a two-stage Gradle build (`eclipse-temurin:21-jdk` → `21-jre`), binds to Cloud Run's `$PORT`. |
| `src/main/resources/application-prod.yml` | Already had the MySQL-with-defaults block (see §1) **replaced** with a Postgres/Neon block (`${SPRING_DATASOURCE_URL}` etc., no defaults) before this session opened the file. |
| `bin/main/**` | ~45 compiled `.class` files + copied YAMLs — IDE/Gradle build output, already staged for commit when checked. Unstaged (see §3) and `bin/` gitignored. |

## 3. Changes made in this session (chronological)

**Task 1 — repo exploration / gap analysis** (no file changes): read every controller, model, repo, DTO, config, and YAML to produce a feature/gap report (reproduced in §5).

**Task 2 — Supabase connection, CORS, health check:**
- `build.gradle`: moved `com.h2database:h2` from `runtimeOnly` → `testRuntimeOnly` (H2 now backs only the test profile; Postgres backs everything real).
- `src/main/resources/application.yml`: datasource now `${DB_URL}` / `${DB_USERNAME}` / `${DB_PASSWORD}` with **no defaults** (fails fast if unset — never hardcode credentials), `driver-class-name: org.postgresql.Driver`, Hikari `maximum-pool-size: 5`, `database-platform: PostgreSQLDialect`, `ddl-auto: update`. Removed the old H2-in-memory block and the H2 console block entirely. Added `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:*}` and `server.port: ${PORT:8085}`.
- `src/main/resources/application-prod.yml`: removed the datasource block entirely (now inherited from `application.yml`), kept only `ddl-auto: validate` and `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS}` (still no default — required in prod).
- `src/main/resources/application-stage.yml`: same pattern — removed the old `STAGE_DB_URL`/H2 fallback datasource block, kept `ddl-auto: update` and added `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:*}`.
- `src/main/resources/application-test.yml`: fully re-overrides the datasource back to H2 in-memory (`org.h2.Driver`, `sa`/`password`, `ddl-auto: create-drop`) so tests never need real Supabase credentials, since the common file no longer has a default.
- **New file** `src/main/java/com/narturebelle/controller/HealthController.java`: `GET /health/db`, runs `SELECT 1` via `JdbcTemplate`, returns `{"database":"ok"}` or a 503 with the exception message.
- **New file** `.env.example`: `DB_URL` (Supabase session pooler, `aws-0-us-east-1.pooler.supabase.com:5432`), `DB_USERNAME` (`postgres.lvtyxrjfnroopmlvnnqg`), blank `DB_PASSWORD`, `PORT=8080`, `CORS_ALLOWED_ORIGINS=https://www.narturebelle.com,https://narturebelle.com`.
- `.gitignore`: added `bin/` (confirmed `.env` was already covered by the existing `*.env` rule).
- Verified: `./gradlew compileJava` succeeds; booting with no env vars fails fast with a clear Hikari error naming `DB_URL`; booting with the real host/username and a deliberately wrong password reached Supabase and got back `FATAL: password authentication failed for user "postgres"` — confirms DNS/TLS/driver/env-var wiring all work end-to-end, just needs the real password to fully boot.
- Committed as `ebfa05d` "Connect to Supabase Postgres, restrict CORS, add DB health check" — **note:** because everything in §2 was already staged, this commit's diff also includes those pre-existing changes (they were not separated into their own commit).

**Task 3 — branch cleanup / merge to `main`:**
- Unstaged `bin/` before committing (`git restore --staged bin/`).
- Added `target/` explicitly commented in `.gitignore` (was already listed, just never enforced since it was committed earlier).
- `git rm -r --cached target/` — untracked the committed `target/narturebelle-backend-1.0.0.jar` (~103MB) and all other Maven build output (45 files). Committed as `0ec09fc` "Stop tracking obsolete Maven target/ build output". **This removes the jar from tracking going forward only — it still exists in old commit history** (`bb26583`/`f86ab77`), since history was not rewritten.
- Discovered `origin/main` exists on GitHub (`origin/HEAD -> origin/main`) with **unrelated git history** to `master`/`codestepup` — it was a single auto-generated "Initial commit" containing only `README.md` (`# NB-Ecom-API`), from GitHub's repo-creation flow. All real development had been happening on `master` (and `codestepup`, `Added_Split_IO`), which share no common ancestor with `main`.
- Created local `main` tracking `origin/main`, merged `master` into it with `git merge --allow-unrelated-histories` (clean merge, zero conflicts — `README.md` preserved, all of `master`'s files added). Commit `661caee`.
- Verified `git ls-tree -r HEAD` on `main`: no `pom.xml`, no `target/`, no large jar (only the legitimate ~43KB `gradle/wrapper/gradle-wrapper.jar`, which is standard for Gradle wrapper and expected to be committed).
- Verified `./gradlew compileJava` on merged `main` — clean build.
- **Pushed** `main` to `origin/main` (`36bbc52..661caee`).
- **Pushed** `master` to `origin/master` (`ebfa05d..0ec09fc`), to keep it in sync with the `target/` cleanup since it's still a real remote branch.
- Did **not** touch `codestepup` or `Added_Split_IO` on the remote — they still exist, untouched, potentially stale now.

## 4. Current file inventory (as of `origin/main`, commit `661caee`)

```
.dockerignore, .env.example, .gitignore, Dockerfile       — infra/config (see above)
build.gradle, settings.gradle, gradlew(.bat), gradle/      — Gradle build (no pom.xml anymore)
src/main/java/com/narturebelle/
  NartureBelleApplication.java                             — unchanged, standard @SpringBootApplication
  config/
    CorsConfig.java            — configurable origins via cors.allowed-origins (see §2)
    DataInitializer.java       — CommandLineRunner, seeds Products/Checklists/HospitalPartners/
                                  NursePackages/DoctorSessions on empty DB (idempotent via count()>0 guard)
    FeatureToggleProperties.java — @ConfigurationProperties("features"), 5 booleans, YAML-driven only —
                                    NOT a real feature-flag service despite branch name "Added_Split_IO"
    FirebaseConfig.java        — credentials file -> ADC -> dev-fallback warning chain (see §2)
  controller/
    AuthController.java        — /send-otp (returns hardcoded "123456", doesn't send anything, no /verify-otp),
                                  /login (mock token, no validation), /firebase (real Firebase token verify
                                  when configured, else FIREBASE_DEV pseudo-identity)
    ChecklistController.java   — GET by stage, read-only
    DoctorSessionController.java — GET sessions, POST /rsvp (increments attendeeCount with a read-modify-write
                                  race condition, no locking)
    FeatureToggleController.java — GET /api/v1/features
    HealthController.java      — NEW: GET /health/db (SELECT 1 check)
    HospitalPartnerController.java — GET by city/pincode, read-only
    LeadController.java        — POST /assess, POST /unlock-checklist (lead capture + voucher, no validation)
    NurseCompanionController.java — GET packages, POST /book
    ProductController.java     — GET by stage/tier, read-only
    UserController.java        — GET/PUT /api/v1/users/{id} — NO AUTH CHECK, anyone can read/edit any user
  dto/  — all plain POJOs, zero @Valid/@NotBlank/@Email annotations anywhere despite
          spring-boot-starter-validation being a dependency
  model/ — 9 @Entity classes (User, Product, ChecklistTask, DoctorRsvp, DoctorSession,
          HospitalPartner, LeadProfile, NurseBooking, NursePackage) + 2 enums (PregnancyStage,
          NecessityTier); User and Product carry the pre-existing changes from §2
  repository/ — 9 plain JpaRepository interfaces, simple derived queries only, no pagination
src/main/resources/
  application.yml              — common config, Supabase datasource via env vars (see §3)
  application-{prod,stage,test}.yml — profile overrides (see §3)
```
No `src/test` directory exists in any commit, ever.

## 5. Known gaps / pending work *(as of §1-4, i.e. the `main` merge at `661caee` — mostly resolved since; see §6)*
- ~~No security layer~~, ~~no input validation~~, ~~no global exception handler~~, ~~OTP flow is fake~~, ~~No Split.io integration~~ — **all resolved in §6 below.**
- **No order/cart/payment/checkout** — despite being named "Ecomm-API", there is no way to actually buy a `Product`; all "purchase-like" flows (nurse booking, doctor RSVP, lead capture) are lead-gen forms only. **Still true** — and now explicitly out of scope: the product pivoted to a care-coordination platform (§6), where cart/checkout isn't the roadmap's near-term focus either.
- **Race condition** in `DoctorSessionController.rsvpSession` (`attendeeCount` read-then-increment-then-save, no transaction/locking). **Still true, untouched** — Phase 1.4 of the brief explicitly says leave the doctor-session/nurse/lead/products/hospitals/checklists endpoints alone.
- **No pagination** anywhere; all list endpoints use `findAll()`. **Still true.**
- The ~100MB jar is gone from tracking but still lives in `master`'s/`main`'s older commit objects (`bb26583`, `f86ab77`) — repo clone size is still bloated unless history is rewritten later (explicitly declined so far).
- `codestepup` and `Added_Split_IO` remote branches are stale relative to `main`/`master`/`firebase-setup` and may need cleanup or reconciliation.

## 6. Firebase auth + the care-coordination pivot (Phase 0 + Phase 1) — everything since §1-5

Everything in this section happened on the `firebase-setup` branch, after the work in §1-5. It's a large, two-part change:

**Part A — real Firebase authentication** (the mock `/send-otp`/`/login` from §1 are now dev-only, gated `@Profile("!prod & !stage")`, kept only for local convenience — real auth is Firebase ID tokens on every request, verified server-side, no app-issued session token). Full contract, request/response shapes, and error codes are in `UI_INTEGRATION_GUIDE.md` §1-6 — that document is kept in sync and is the source of truth for the HTTP contract, not duplicated here. Highlights: `FirebaseAuthenticationFilter`/`FirebaseUserPrincipal`/`AuthorizationService` (stateless Spring Security), `GET/PATCH /api/v1/me` (+ deprecated `/api/v1/users/me` alias), role-gated `/api/v1/admin/**`, Swagger/OpenAPI (springdoc) with a working "Authorize" button, CORS locked to `narturebelle.com`/`www.narturebelle.com`/`stage-narturebelle.com` + any `http://localhost:*` for dev — not a wildcard.

**Part B — the roadmap pivot.** The user pasted `../NARTUREBELLE_ROADMAP.md` (sibling directory, not a git repo — a plain shared file, not committed here) and `PHASE_0_1_BRIEF.md` (committed in this repo), reframing NartureBelle from a product store into a **care-coordination platform**. `PHASE_0_1_BRIEF.md` is the authoritative, highly prescriptive task spec (exact enums, table shapes, endpoint behaviors, error codes) — read it directly for anything this summary doesn't cover. Implemented exactly as specified, one commit per numbered task, each verified via the full test suite **and** a live boot against the real stage Supabase DB with real Firebase-signed-up users before committing:

- **0.1 Flyway** — `V1__baseline.sql` onward; `ddl-auto: validate` everywhere non-test; `spring.flyway.baseline-on-migrate: true` so the already-populated stage/prod DBs adopt V1 as "already applied" instead of re-running `CREATE TABLE`. Test profile still skips Flyway entirely (`spring.flyway.enabled: false`, H2 `create-drop`) — **anything seeded via a migration must also be seeded in `DataInitializer`** (guarded by `count() > 0`) or tests never see it. Verified on a genuinely empty Postgres (Docker, not just the already-migrated stage DB) — all 7 migrations (V1-V7) apply cleanly from scratch.
- **0.2 Roles** — new `Role` enum (18 values), `user_roles` many-to-many table, `USER`→`MOTHER`/`ADMIN`→`SUPER_ADMIN` migration backfill. Legacy single `role` column kept readable, nothing writes to it. `/api/v1/admin/**` now `hasAnyAuthority` across the 6 `*_ADMIN` roles.
- **0.3 Audit log** — `audit_events`, append-only by omission (no update/delete repo methods). `AuditService.record(...)`, called from every write Phase 1 added.
- **0.4 Validation & errors** — `@Valid` + Bean Validation on request DTOs; `GlobalExceptionHandler` now has a `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` handler and an `Exception` catch-all → 500 `INTERNAL_ERROR`. **Watch out if adding new exception handlers**: the catch-all will swallow anything without a more-specific handler, including `AccessDeniedException` (must re-throw, not handle, so Spring Security's own 403 flow still fires) and Spring's "no route matched" exception (`NoResourceFoundException` in practice on Spring 6.1+, not the classic `NoHandlerFoundException` — both are handled).
- **0.5 Feature flags in the DB** — `feature_flags` table replaces the YAML-only toggles from §1; `GET /api/v1/features` unchanged shape; new `PATCH /api/v1/admin/feature-flags/{key}` (admin-only, audited, requires `reason`). **This finally answers §1's "Suggested focus area #4"**: the YAML-based toggles are superseded by a real DB-backed flag service, not Split.io.
- **0.6-0.7 `/me` + name backfill** — see Part A above and `UI_INTEGRATION_GUIDE.md`.
- **1.1 Consent** — `consent_documents`/`consents`, `GET /api/v1/consent-documents/current` (public), `GET/POST /api/v1/me/consents`, `POST /api/v1/me/consents/{type}/revoke`. New `@RequireConsent` annotation + `ConsentCheckInterceptor` (same `HandlerInterceptor` pattern as the pre-existing `@RequireVerified`), gating Journey and Dashboard endpoints with 403 `CONSENT_REQUIRED`.
- **1.2 Journey** — `journeys`/`journey_stage_events`, `JourneyStatus` enum, `POST/GET/PATCH /api/v1/me/journeys[/{id}]`. At most one non-CLOSED journey per user (partial unique index + app check). `gestationalWeek`/`trimester` computed per-response from the EDD, never persisted (`GestationalMath` utility, shared with Dashboard). **`User.stage`/`dueDate` are now frozen** — `UserProfileService` no longer writes them; Journey is the source of truth. A migration backfills a `Journey` per existing user who had a legacy `stage` on file (verified as a no-op on stage today — no such users exist there yet, but applies cleanly either way).
- **1.3 Dashboard** — `care_protocol_steps` (seeded clinical content map, all `PENDING_CLINICAL_REVIEW` — **not yet clinically reviewed, don't present as approved**), `GET /api/v1/me/dashboard`. Implements "Workflow D" (late enrollment never shows earlier stages as missed) via a window-rank comparison over the journey's current stage/trimester.
- **1.4** — lead/assessment/products/hospitals/checklists/nurse/doctor-session endpoints explicitly left alone, per the brief.

Full request/response JSON for every endpoint above (including the 4 new error codes: `VALIDATION_FAILED`, `CONSENT_REQUIRED`, `CONSENT_VERSION_OUTDATED`, `JOURNEY_ALREADY_ACTIVE`) is in `UI_INTEGRATION_GUIDE.md` §7 — that's the contract to build a UI against, this section is just the "what and why."

**Updated gaps, on top of §5:**
- `CHILDCARE_0_7` journey status exists but has **no seeded `care_protocol_steps`** yet — a user who reaches that stage gets an empty `nextActions` (plus the universal clinical-psychologist offer). Not a bug, just unimplemented content — Phase 6 territory per the roadmap.
- Clinical content (the stage → professional map) is all `PENDING_CLINICAL_REVIEW` — a real review workflow (who approves it, how `ReviewStatus` transitions to `APPROVED`) doesn't exist yet, just the enum value.
- `LegacyAuthController`'s dev-only mock `/login` still writes `user.setStage()`/`setDueDate()` — left alone since it doesn't exist as a bean in prod/stage and isn't a real-world write path, but it's inconsistent with Journey now being the real source of truth if anyone resurrects it for local testing of stage-dependent features.
- No Testcontainers — the brief flagged this as a future option if H2/Postgres drift becomes a real problem; hasn't been needed yet (H2 create-drop + the `DataInitializer` dual-seed pattern has kept pace through 7 migrations).

## 7. Phase 2 — Plans, checkout & entitlements

Also on `firebase-setup`, immediately after §6. Per `NARTUREBELLE_ROADMAP.md`'s Phase 2, with two things the roadmap itself flags as blocking decisions ("refund/unused-session policy" and "blueprint §5" per-professional allocation numbers) resolved by the product owner as: ship the full structure now, with refunds as an admin-entered manual amount (no auto policy) and the allocation/validity numbers as clearly-flagged placeholders, fixable via a later migration — same precedent as Phase 1's DRAFT consent text, not a new pattern.

- **Plan/PlanVersion/PlanEntitlement** ("config not code", same shape as `ConsentDocument`/`CareProtocolStep`) — 3 tiers seeded (Standard ₹9,999/9 sessions, Care ₹14,999/12, Premium ₹24,999/16 — these 3 numbers *are* real, straight from the roadmap; the per-professional split, 365-day validity, and inclusion/exclusion copy are the placeholders). `GET /api/v1/plans`, `GET /api/v1/plans/{id}`, public.
- **Order** — server-recalculated price (the client never sends one), `Idempotency-Key`-gated creation with a SHA-256 request fingerprint (same key + same fingerprint replays the existing order; same key + different fingerprint is a 409, not a silent wrong answer). `InstallmentSchedule` is a read-only breakdown only — the mock payment always settles the whole order in one shot regardless of payment mode, since real installment *collection* is explicitly Phase 7.
- **New `com.narturebelle.payment` package** — `PaymentProvider` interface is the seam Phase 7's real gateway plugs into. `MockPaymentProvider` signs/verifies webhook payloads with HMAC-SHA256 (`payments.mock.webhook-secret`). The "simulated checkout" buttons (`POST /payments/{id}/mock/succeed|fail`) don't take a shortcut — they build a real signed, gateway-shaped payload and call the *identical* `PaymentService.processWebhook(...)` method the public `POST /payments/webhook` endpoint uses, so signature verification and parsing are genuinely exercised every time, not bypassed for convenience.
- **Entitlement grants are idempotent three different ways** (since "no duplicates on retries" was a hard requirement, not a nice-to-have): a `payment_webhook_events` ledger (unique `provider_event_id`) dedupes webhook deliveries, `Order.status` only transitions `PENDING → PAID` once, and `entitlements` has `unique(order_id, professional_type)` as a last-resort DB guard if the first two somehow both got raced.
- `GET /api/v1/entitlements` — the session wallet. Nothing consumes a session yet; that's Phase 3's booking/session-completion flow.
- **Refunds and finance** — `POST /api/v1/admin/orders/{id}/refunds` is always an admin-entered amount (never computed), validated against what's actually still refundable, and never touches `Entitlement` rows. `GET /api/v1/admin/finance/summary` reports `deferredRevenueLiability` as *literally equal to* net revenue with an explicit methodology note — because there's no session-consumption mechanism yet, 100% of paid revenue genuinely is "unused" today, and the response says so rather than fabricating a partial-usage number.
- Fixed a real latent gap while here: `POST /orders`' required `Idempotency-Key` header was the first required header anywhere in this codebase, and `MissingRequestHeaderException` had no handler in `GlobalExceptionHandler` — it would have fallen through to the 500 catch-all. Added a handler → 400 `VALIDATION_FAILED`.

Full request/response JSON (including the new error codes `PLAN_NOT_FOUND`, `IDEMPOTENCY_KEY_CONFLICT`, `PAYMENT_NOT_ALLOWED`, `INVALID_WEBHOOK_SIGNATURE`, `REFUND_NOT_ALLOWED`) is in `UI_INTEGRATION_GUIDE.md` §8.

**Verification note specific to this phase:** the user-facing flow (plans → order → idempotency replay/conflict → payment → mock succeed/fail → webhook signature check → entitlements) was verified live against the real stage Supabase DB with a real Firebase-signed-up, *verified* user — verifying email required a one-off Firebase Admin REST call (`accounts:update` via a service-account-signed OAuth token) since there's no way to mark a test account verified through normal sign-in alone. The admin-only endpoints (refund, finance summary) were **not** separately re-verified live — doing so would have meant extracting the stage DB password to hand-insert a `FINANCE_ADMIN` role row, which a safety guardrail correctly blocked; they're covered instead by the full `AdminFinanceTest` suite against H2.

**Updated gaps, on top of §6:**
- No cart resource — plan selection goes straight to `POST /orders`. The roadmap's "Logged in: cart" access tier (`API_ENDPOINTS.md`) still has nothing built against it.
- Installment *collection* doesn't exist — `InstallmentSchedule` is display-only, by design, until Phase 7.
- `AdminOrderController`'s refund endpoint has no companion "list all refunds" route; refunds are only reachable embedded in `GET /orders/{id}`'s response. Fine for now, flagged in case a standalone admin refund report is wanted later.
- The mock payment provider's `payments.mock.webhook-secret` has a checked-in dev default (`dev-only-mock-webhook-secret-change-me`) — harmless since `MockPaymentProvider` never ships past the prototype, but worth real rotation hygiene if that assumption ever changes.

## 8. Phase 3 — Providers, booking & sessions

Also on `firebase-setup`, right after §7. Per `NARTUREBELLE_ROADMAP.md`'s Phase 3. The roadmap flags 3 "Decisions needed" (video-call provider, payout rate, no-show/cancellation policy) plus "how providers sign in" — the last one isn't actually open, since the roadmap itself already recommends the answer (same Firebase project, admin-assigned role), and that recommendation is exactly what got built. The other 3 followed the same "ship structure, defer the policy" precedent as Phase 2's refund policy and Phase 2's plan-allocation placeholders.

- **"How providers sign in", concretely**: a provider is just a normal Firebase-signed-up `User` (defaults to `MOTHER` like everyone else) who gains a professional `Role` (e.g. `GYNECOLOGIST`) the moment an admin's `/verify` call moves their linked `Provider` profile to `VERIFIED`. This works with **zero changes to the auth layer** because `FirebaseAuthenticationFilter` already re-fetches the `User` row from the DB on every single request and builds the security principal's roles from whatever is in that row *right now* — confirmed via a dedicated research pass before writing any code, not assumed. So the grant takes effect on the user's very next API call; no new Firebase ID token, no client-side re-login. `ProviderTest` has a test that proves this concretely (grants the role, then re-hits `GET /me` with the *same* token used before the grant).
- **`Provider`/`ProviderCredential`/`ServiceOffering`/`ProviderSlot`/`Booking`** — `Booking` deliberately merges what the roadmap describes as separate "booking" and "session" concepts into one entity with an evolving status (`BOOKED → IN_PROGRESS → COMPLETED`, or `CANCELLED`/`RESCHEDULED`/`NO_SHOW_*`): a session never exists apart from exactly one booking, so a 1:1 split would just add a join with no independent lifecycle. `ServiceOffering` (not "Service", the roadmap's literal word) to avoid colliding in spirit with the dozen `*Service` classes already in `com.narturebelle.service`.
- **Slot claiming** is two-layered, same style as Phase 2's entitlement-grant guards: a conditional `UPDATE ... WHERE status='OPEN'` (0 rows = lost the race) plus a partial unique index on `bookings(slot_id)` as a DB-level backstop. No `Idempotency-Key` on `POST /bookings` (unlike `POST /orders`) — the slot's own claim uniqueness already makes a blind retry safely 409.
- **Entitlements are consumed only at session completion, never at booking time** — `EntitlementService.consumeOneSession` (new) picks the soonest-expiring eligible entitlement (FIFO) **under a pessimistic write lock**, not `@Version`, since this is a "find-then-increment" read-then-write under contention, not a "client has the record open and submits an edit" pattern. This is a retrofit onto Phase 2's `Entitlement` entity — Phase 2 itself never wrote `usedSessions` after creation, so it never needed concurrency protection until now.
- **Payouts** (`PayoutRate` config + `Payout` ledger row) are created only on session completion, at a flat seeded placeholder rate per professional type — the roadmap's "payout rate (from config)" decision is explicitly deferred, flagged the same way as every other placeholder number this project has shipped with. No disbursement mechanism exists; every payout sits `PENDING` forever until a future phase.
- **No-show and cancellation both leave entitlements and payouts untouched** — the safe default per the deferred policy. No-show reporting is party-restricted (a user can only flag the provider, a provider only the user) since the roadmap doesn't specify who may report it; this is an inferred rule, not roadmap text.
- **Three-way booking access** (`AuthorizationService.requireBookingAccess`, new) extends the exact pattern its own prior doc comment promised ("reuse for future cart/order endpoints too"): admin, the booking's own user, or the `Provider` linked to the caller. Start/complete are tighter (provider-or-admin only); feedback is tighter still (booking's own user only).
- **New admin endpoints beyond the roadmap's literal bullet list**: `POST /admin/providers` (something has to create the initial `Provider` row — there's no pre-signup invite flow), `GET /admin/providers[/{id}]` (the onboarding queue view), and `POST /admin/providers/{id}/credentials|services|slots` (admin-only, since no provider portal or self-service exists yet — same reasoning the roadmap itself used to defer the portal on the UI side).

Full request/response JSON (including the new error codes `PROVIDER_NOT_FOUND`, `PROVIDER_NOT_BOOKABLE`, `INVALID_PROVIDER_STATUS_TRANSITION`, `SLOT_NOT_AVAILABLE`, `NO_ENTITLEMENT_AVAILABLE`, `BOOKING_NOT_ALLOWED`, `FEEDBACK_NOT_ALLOWED`) is in `UI_INTEGRATION_GUIDE.md` §9.

**Verification note specific to this phase:** the public provider-directory endpoints and the `@RequireVerified`/`EMAIL_NOT_VERIFIED` gate on booking creation were verified live against the real stage Supabase DB (V9 applies cleanly on top of V1-V8). The admin-only onboarding chain (create provider → verify → book → complete → payout) was **not** separately re-verified live — same blocker as Phase 2's finance endpoints: granting a test account admin access on stage would require extracting the DB password, which this session's safety guardrail correctly blocks. It's covered instead by `ProviderTest`/`BookingTest` (83 tests total across the whole suite), including the concrete same-token role-grant proof described above.

**Updated gaps, on top of §7:**
- No provider portal and no admin console exist (confirmed already skipped on the UI side for the identical reason) — every provider-side action (adding credentials/services/slots, starting/completing a session) has to go through the raw API today.
- Credential `expiresAt` is stored but nothing reads it — no automatic status downgrade on expiry, no expiry-alert mechanism. A real "credential expiry alerts" feature (mentioned in the roadmap's admin-console bullet, itself deferred) would need this.
- The professional `Role` granted at `VERIFIED` is **never auto-revoked** on `PAUSED`/`SUSPENDED`/`DEACTIVATED` — any future feature that gates on a role claim alone (rather than also checking live `Provider.status`) would incorrectly keep treating a suspended provider as active. Flagged in the code, not silently assumed.
- `GET /providers`'s multi-filter logic runs in Java over a small fetched set (`findByStatusIn` then stream filters) — fine at current scale, but there's no Specification/Criteria precedent anywhere in this codebase to reach for if the provider count grows large enough to need it pushed into SQL.
- Reschedule can only change the slot/time, not the provider or service — a genuine "different provider" request needs a separate new booking plus cancelling the old one.
