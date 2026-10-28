# API Brief — Phase 0 (Foundations) + Phase 1 (Registration, consent & journey)

**For:** the API agent working in `NB-Ecomm-API` (branch off `firebase-setup`; suggested branch `feature/phase-0-1`).
**Context:** read `../NARTUREBELLE_ROADMAP.md` first. The product source of truth is the NartureBelle implementation blueprint: NartureBelle is a **care-coordination platform**, not a store.
**You go first.** The UI agent builds against the contract you publish in `UI_INTEGRATION_GUIDE.md`.

## Product-owner decisions already made (do not change)
- **Firebase stays** the only sign-in system (Google, email+password, phone). There is no custom OTP and no app-issued tokens.
- **Sign-up collects nothing about the pregnancy.** Journey stage and the expected delivery date (EDD) are **optional** and are set later from the Profile page.
- Payments are out of scope until much later.

## Rules
- Do not hard-code prices, clinical protocols, schedules or policy values. Seed them as data rows with a `version`.
- Seeded clinical content (the stage → professional map) must carry `reviewStatus = PENDING_CLINICAL_REVIEW`.
- Keep the existing error shape `{ "code": "...", "message": "..." }`.
- Every new endpoint needs tests (extend the existing MockMvc + mocked `FirebaseTokenVerifier` pattern).
- **Commit in small steps.** One commit per numbered task below is a good size.

---

## Phase 0 — Foundations

**0.1 Database migrations (Flyway)**
- Add `org.flywaydb:flyway-core` + `flyway-database-postgresql`.
- `V1__baseline.sql` = the current schema as it exists today. Generate it from the real DB (schema-only dump) or from Hibernate's schema export, then review it by hand.
- Set `ddl-auto: validate` in **all** non-test profiles. From now on every schema change is a new `V{n}__*.sql`.
- Test profile: keep H2 with `create-drop` and `spring.flyway.enabled=false`, and note this in `HANDOFF.md`. If H2 drift becomes a problem, switch tests to Testcontainers Postgres later.

**0.2 Roles**
- New `Role` enum: `MOTHER, AUTHORIZED_GUARDIAN, GYNECOLOGIST, NUTRITIONIST, CLINICAL_PSYCHOLOGIST, LACTATION_CONSULTANT, PEDIATRICIAN, POSTPARTUM_PHYSIOTHERAPIST, REGISTERED_NURSE, CLINIC_ADMIN, HOSPITAL_ADMIN, CARE_COORDINATOR, SUPPORT_AGENT, MEDICAL_REVIEWER, OPERATIONS_ADMIN, FINANCE_ADMIN, SECURITY_ADMIN, SUPER_ADMIN`.
- Add a `user_roles` table (many-to-many). Migrate existing `USER` → `MOTHER` and `ADMIN` → `SUPER_ADMIN`. The old `role` column stays readable for one release, and nothing writes to it any more.
- New sign-ups get `MOTHER`.
- Put all roles on the principal as `ROLE_<name>` authorities. `/api/v1/admin/**` requires any `*_ADMIN` role. `AuthorizationService.requireSelfOrAdmin` keeps working.
- API responses expose `roles: string[]`. Keep `role` (the first/primary role) for one release so the current UI doesn't break.

**0.3 Audit log**
- `audit_events` table: id, occurred_at, actor_user_id, actor_roles, action (e.g. `CONSENT_GRANTED`), subject_type, subject_id, reason (nullable), request_id, metadata JSON (**never** health details or document names).
- `AuditService.record(...)`. The table is append-only: no update or delete code paths.
- Call it from every write added in this brief, and from `/auth/firebase` user creation.

**0.4 Validation & errors**
- Add `@Valid` + constraints on all request DTOs (old and new).
- `GlobalExceptionHandler`: `MethodArgumentNotValidException` → `400 {code: "VALIDATION_FAILED", message, fields: {name: error}}`. Unknown exceptions → `500 {code: "INTERNAL_ERROR"}` with no stack trace in the body.

**0.5 Feature flags in the database**
- `feature_flags` table (key, enabled, description, updated_at, updated_by). Seed it from the current YAML values plus these new flags, **all off**: `productRecommendations`, `careBooking`, `recordsVault`, `childTracker`, `aiAssistant`, `joyCircle`, `legacyLeadForms`.
- `GET /api/v1/features` keeps the same response shape. Add `PATCH /api/v1/admin/feature-flags/{key}` (admin only, audited, requires a `reason`).

**0.6 `/me` endpoints**
- `GET /api/v1/me` and `PATCH /api/v1/me` (names and phone; same rules as today's `PUT /users/me`). Keep `/users/me` working as an alias and mark it deprecated in Swagger.
- `GET /me` must also return `pendingConsents: string[]` (see 1.1) and `hasActiveJourney: boolean`.

**0.7 Name backfill**
- In `/auth/firebase`, if an **existing** user has both `firstName` and `lastName` empty, derive the names exactly as you already do for new users. Never touch names that are set.

---

## Phase 1 — Registration, consent & journey

**1.1 Consent**
- `consent_documents` (type `TERMS | PRIVACY`, version, title, body_markdown, effective_at, active). Seed v1 of each with clearly marked **DRAFT placeholder text**; legal review comes later.
- `consents` (user_id, document_type, document_version, granted_at, revoked_at). Never update a row's grant; revoking sets `revoked_at`, and re-granting inserts a new row.
- `GET /api/v1/consent-documents/current` (public) returns the active version of each type.
- `GET /api/v1/me/consents` returns the history plus `pending` (the required types with no active grant for the **current** version).
- `POST /api/v1/me/consents` with `{documentType, documentVersion}`. Reject a stale version with `409 CONSENT_VERSION_OUTDATED`.
- `POST /api/v1/me/consents/{type}/revoke`.
- **Gate:** the journey and dashboard endpoints (1.2, 1.3) return `403 {code: "CONSENT_REQUIRED", pending: [...]}` while consent is pending. `/me`, `/me/consents`, `/auth/firebase` and the public endpoints are **not** gated.
- Audit every grant and revoke.

**1.2 Journey**
- `JourneyStatus` enum: `PREGNANCY, PRE_DELIVERY, DELIVERY_COMPLETED, POSTPARTUM, CHILDCARE_0_7, CLOSED`.
- `journeys` (user_id, status, expected_delivery_date nullable, started_at, closed_at, version for optimistic locking). **At most one non-CLOSED journey per user.**
- `journey_stage_events` (journey_id, from_status, to_status, occurred_at, actor_user_id, source `USER | STAFF | SYSTEM`).
- `POST /api/v1/me/journeys` with `{status, expectedDeliveryDate?}`. Return `409 JOURNEY_ALREADY_ACTIVE` if one already exists.
- `GET /api/v1/me/journeys` and `GET /api/v1/me/journeys/{id}` (owner only).
- `PATCH /api/v1/me/journeys/{id}` with `{status?, expectedDeliveryDate?}`. Record a stage event on a status change and audit the change.
- Computed response fields when `status = PREGNANCY` and an EDD exists: `gestationalWeek` (EDD − 280 days = day 0) and `trimester` (1: weeks 1–13, 2: 14–27, 3: 28+). They are null when there is no EDD. **The EDD is optional everywhere.**
- Validation: the EDD may not be more than 42 weeks in the future or more than 1 year in the past.
- **Migration:** for users with the legacy `stage`/`dueDate`, create a journey (`MONTH_3/6/9` → `PREGNANCY` with `dueDate` as the EDD; `BABY_CARE` → `POSTPARTUM`). Stop writing the legacy fields. **Do not** remove the products endpoints that still use `PregnancyStage`.

**1.3 Dashboard & next actions (late-enrolment aware)**
- `care_protocol_steps` (version, review_status, journey_status, trimester nullable, professional_type, title, description, sort_order). Seed from the blueprint §6 stage map with `review_status = PENDING_CLINICAL_REVIEW`:
  - PREGNANCY, trimester 1: Gynecologist
  - PREGNANCY, trimester 2: Gynecologist + Nutritionist
  - PREGNANCY, trimester 3: Gynecologist + Nutritionist
  - PRE_DELIVERY: Gynecologist + Lactation consultant
  - DELIVERY_COMPLETED / POSTPARTUM: Gynecologist, Pediatrician, Lactation consultant, Postpartum physiotherapist
  - Any stage: Clinical psychologist "available if you'd like support" (offered, never pushed)
- `GET /api/v1/me/dashboard` returns `{ profile: {firstName, lastName}, pendingConsents, journey | null, nextActions: [...], journeySetupRequired: boolean }`.
  - `nextActions` covers only the **current and later** windows. Earlier windows are never shown as done (Workflow D).
  - With no journey: `journeySetupRequired: true` and `nextActions: []`.
  - Each action carries `reviewStatus` so the UI can label drafts.
- No care recommendations are generated before consent (enforced by the 1.1 gate).

**1.4 Leave alone for now**
- Keep the lead, assessment, products, hospitals, checklists, nurse and doctor-session endpoints working unchanged. The UI hides them behind flags; they get removed or reworked in later phases.

---

## Definition of done
- [ ] `./gradlew test` is green, with new tests for: role mapping, the admin flag PATCH, the consent grant/revoke/version-outdated paths, the `CONSENT_REQUIRED` gate, journey create/409/patch/ownership, gestational-week maths (with and without an EDD), dashboard late enrolment and the name backfill.
- [ ] Flyway migrations apply cleanly on an **empty** Postgres and on the current Supabase stage DB.
- [ ] Swagger shows all new endpoints.
- [ ] `UI_INTEGRATION_GUIDE.md` has a new **"Phase 0/1 contract"** section with every endpoint, its request/response JSON and the error codes (`VALIDATION_FAILED`, `CONSENT_REQUIRED`, `CONSENT_VERSION_OUTDATED`, `JOURNEY_ALREADY_ACTIVE`).
- [ ] `HANDOFF.md` is updated, and the matching boxes in `../NARTUREBELLE_ROADMAP.md` are ticked with the date.
- [ ] Report anything you had to decide that isn't covered here, rather than guessing silently.
