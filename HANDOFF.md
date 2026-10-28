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
