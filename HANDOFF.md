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

## 5. Known gaps / pending work
- **No Split.io integration** despite the branch name `Added_Split_IO` — `FeatureToggleProperties` is static YAML booleans, not a real flagging SDK.
- **No tests at all**, despite `spring-boot-starter-test` being a dependency.
- **No security layer**: no Spring Security, no JWT validation on the mock `/login`/`/firebase` tokens, `UserController` has no ownership checks (any ID is readable/writable by anyone).
- **OTP flow is fake**: `/send-otp` just echoes `123456`, no `/verify-otp` endpoint exists.
- **No input validation** anywhere despite the validation starter being present.
- **No global exception handler** — unhandled errors leak default Spring stack traces.
- **No order/cart/payment/checkout** — despite being named "Ecomm-API", there is no way to actually buy a `Product`; all "purchase-like" flows (nurse booking, doctor RSVP, lead capture) are lead-gen forms only.
- **Race condition** in `DoctorSessionController.rsvpSession` (`attendeeCount` read-then-increment-then-save, no transaction/locking).
- **No pagination** anywhere; all list endpoints use `findAll()`.
- `ddl-auto: update` in dev/stage means schema drift is auto-applied; prod uses `validate`, so **tables must be manually created in Supabase first** (or let `update` run once against Supabase from a dev/stage profile boot) before a prod deploy. Tables Hibernate manages: `users`, `products` (+ `product_benefits`, `product_symptoms`), `checklist_tasks`, `hospital_partners`, `nurse_packages` (+ `nurse_package_features`), `nurse_bookings`, `doctor_sessions`, `doctor_rsvps`, `lead_profiles` (+ `lead_symptoms`).
- The ~100MB jar is gone from tracking but still lives in `master`'s/`main`'s older commit objects (`bb26583`, `f86ab77`) — repo clone size is still bloated unless history is rewritten later (explicitly declined so far).
- `codestepup` and `Added_Split_IO` remote branches are now stale relative to `main`/`master` and may need cleanup or reconciliation.

---

### Suggested focus areas for the next review
1. Secure the API — real auth/session validation, ownership checks on `UserController`, input validation on all DTOs.
2. Design the missing order/cart/payment domain — this is the actual "Ecomm" gap.
3. Review the Supabase schema given `ddl-auto` behavior — confirm tables exist correctly before prod relies on `validate`.
4. Decide whether `Added_Split_IO`'s original intent (real feature flagging via Split.io) should still be implemented, or whether the YAML-based toggles are sufficient going forward.
