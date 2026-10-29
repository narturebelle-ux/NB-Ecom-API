# API Endpoints & Access Levels

**Interactive docs:** run the app and open `http://localhost:<PORT>/swagger-ui/index.html` (publicly reachable, no token needed just to view it). Click **Authorize** and paste a Firebase ID token to test endpoints that require login directly from the UI. Raw OpenAPI JSON is at `/v3/api-docs`.

Access tiers, per the AUTH CONTRACT between UI and API:

- **Public** — no `Authorization` header needed.
- **Logged in** — valid `Authorization: Bearer <Firebase ID token>` required; user doesn't need to be verified.
- **Logged in + Verified** — valid token AND (`email_verified == true` OR the token carries a verified `phone_number`).
- **Admin** — valid token AND the caller holds any of the 6 `*_ADMIN` roles (`CLINIC_ADMIN`, `HOSPITAL_ADMIN`, `OPERATIONS_ADMIN`, `FINANCE_ADMIN`, `SECURITY_ADMIN`, `SUPER_ADMIN`) — updated from the original single `ADMIN` role in Phase 0's multi-role model.
- **Dev-only** — bean doesn't exist at all when `SPRING_PROFILES_ACTIVE` is `prod` or `stage`; available in `default`/local/`test`.

Errors are JSON `{"code": "...", "message": "..."}` — `401 UNAUTHENTICATED`, `403 EMAIL_NOT_VERIFIED`, `403 FORBIDDEN`, plus the Phase 0/1/2/3/4 and Onboarding codes in `UI_INTEGRATION_GUIDE.md` §7-11 (`VALIDATION_FAILED`, `CONSENT_REQUIRED`, `CONSENT_VERSION_OUTDATED`, `JOURNEY_ALREADY_ACTIVE`, `PLAN_NOT_FOUND`, `IDEMPOTENCY_KEY_CONFLICT`, `PAYMENT_NOT_ALLOWED`, `INVALID_WEBHOOK_SIGNATURE`, `REFUND_NOT_ALLOWED`, `PROVIDER_NOT_FOUND`, `PROVIDER_NOT_BOOKABLE`, `INVALID_PROVIDER_STATUS_TRANSITION`, `SLOT_NOT_AVAILABLE`, `NO_ENTITLEMENT_AVAILABLE`, `BOOKING_NOT_ALLOWED`, `FEEDBACK_NOT_ALLOWED`, `RECORD_UPLOAD_NOT_CONFIRMED`, `RECORD_NOT_ALLOWED`, `ONBOARDING_CALL_SLOT_NOT_AVAILABLE`, `ONBOARDING_CALL_ALREADY_BOOKED`).

**This table predates Phase 0/1/2/3/4.** For `/api/v1/me`, consent, journey, dashboard, plans, orders, payments, entitlements, providers, bookings, records and onboarding — see `UI_INTEGRATION_GUIDE.md` §7 (Phase 0/1), §8 (Phase 2), §9 (Phase 3), §10 (Phase 4) and §11 (Onboarding) for the full request/response contract. Only the original pre-pivot endpoints and the new Phase 2/3/4/Onboarding rows are kept below.

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/health/db` | Public | DB connectivity check |
| GET | `/api/v1/products` | Public | query by `stage`/`tier` |
| GET | `/api/v1/products/{id}` | Public | |
| GET | `/api/v1/products/stage/{stage}` | Public | |
| GET | `/api/v1/checklists` | Public | |
| GET | `/api/v1/checklists/stage/{stage}` | Public | |
| GET | `/api/v1/hospitals` | Public | query by `city`/`pincode` |
| GET | `/api/v1/hospitals/{id}` | Public | |
| GET | `/api/v1/features` | Public | feature-toggle flags |
| GET | `/api/v1/doctor-sessions` | Public | |
| POST | `/api/v1/doctor-sessions/rsvp` | Public | anonymous lead-gen form, not tied to a logged-in user |
| GET | `/api/v1/nurse-companion/packages` | Public | |
| POST | `/api/v1/nurse-companion/book` | Public | anonymous lead-gen form |
| POST | `/api/v1/leads/assess` | Public | anonymous lead-gen form |
| POST | `/api/v1/leads/unlock-checklist` | Public | anonymous lead-gen form |
| POST | `/api/v1/auth/send-otp` | Dev-only | mock OTP, disabled in prod/stage |
| POST | `/api/v1/auth/login` | Dev-only | mock login, no app-issued token, disabled in prod/stage |
| POST | `/api/v1/auth/firebase` | Logged in | creates/updates the user from the verified token; call this right after any Firebase sign-in |
| GET | `/api/v1/users/me` | Logged in | own profile |
| PUT | `/api/v1/users/me` | Logged in + Verified | updates own profile; changing `phone` resets `phoneVerified` to `false` until re-verified via `/auth/firebase` |
| GET | `/api/v1/users/{id}` | Logged in (self) or Admin | 403 FORBIDDEN if `{id}` isn't the caller and the caller isn't ADMIN |
| GET | `/api/v1/plans` | Public | Phase 2 — plan comparison (Standard/Care/Premium) |
| GET | `/api/v1/plans/{id}` | Public | Phase 2 |
| POST | `/api/v1/orders` | Logged in + Verified | Phase 2 — requires `Idempotency-Key` header; server recalculates price |
| GET | `/api/v1/orders` | Logged in + Verified | Phase 2 — own orders |
| GET | `/api/v1/orders/{id}` | Logged in (self) or Admin | Phase 2 |
| POST | `/api/v1/payments/create` | Logged in + Verified | Phase 2 |
| POST | `/api/v1/payments/webhook` | Public (signature-verified) | Phase 2 — a real gateway carries no Firebase token; HMAC signature is the only guard |
| POST | `/api/v1/payments/{id}/mock/succeed` \| `/mock/fail` | Logged in + Verified | Phase 2 — prototype-only, 404s once a real `PaymentProvider` replaces the mock |
| GET | `/api/v1/entitlements` | Logged in | Phase 2 — the session wallet |
| POST | `/api/v1/admin/orders/{id}/refunds` | Admin | Phase 2 — always an admin-entered amount, no auto policy |
| GET | `/api/v1/admin/finance/summary` | Admin | Phase 2 |
| GET | `/api/v1/providers` | Public | Phase 3 — directory, filterable; VERIFIED+ACTIVE only |
| GET | `/api/v1/providers/{id}` | Public | Phase 3 |
| GET | `/api/v1/providers/{id}/slots` | Public | Phase 3 — OPEN, future-only |
| POST | `/api/v1/admin/providers` | Admin | Phase 3 — the professional must already be a signed-up User |
| GET | `/api/v1/admin/providers[/{id}]` | Admin | Phase 3 |
| POST | `/api/v1/admin/providers/{id}/verify` | Admin | Phase 3 — the ONLY action that grants the professional Role |
| PATCH | `/api/v1/admin/providers/{id}/status` | Admin | Phase 3 — never accepts VERIFIED as a target |
| POST | `/api/v1/admin/providers/{id}/credentials` \| `/services` \| `/slots` | Admin | Phase 3 — no provider self-service yet |
| POST | `/api/v1/bookings` | Logged in + Verified | Phase 3 — no Idempotency-Key needed, the slot claim is itself safe to retry |
| GET | `/api/v1/bookings` | Logged in + Verified | Phase 3 — own, as user or as assigned provider |
| GET | `/api/v1/bookings/{id}` | Logged in (owner/provider/admin) | Phase 3 |
| POST | `/api/v1/bookings/{id}/cancel` \| `/reschedule` | Logged in + Verified | Phase 3 — owner, assigned provider, or admin |
| POST | `/api/v1/bookings/{id}/start` \| `/complete` | Logged in + Verified | Phase 3 — assigned provider or admin only |
| POST | `/api/v1/bookings/{id}/no-show` | Logged in + Verified | Phase 3 — party-restricted (can't self-report) |
| PATCH | `/api/v1/bookings/{id}/note` | Logged in + Verified | Phase 3 — provider/admin, only after COMPLETED |
| POST | `/api/v1/bookings/{id}/feedback` | Logged in + Verified | Phase 3 — booking's own user, only after COMPLETED, once |
| POST | `/api/v1/records/upload-url` | Logged in + Verified | Phase 4 — issues a signed Supabase Storage upload URL, no DB row yet |
| POST | `/api/v1/records` | Logged in + Verified | Phase 4 — confirms the upload (verifies the object exists) and creates the record |
| GET | `/api/v1/records` | Logged in | Phase 4 — own records, excludes deleted |
| GET | `/api/v1/records/shared-with-me` | Logged in | Phase 4 — as a provider, via direct or booking-scoped shares |
| GET | `/api/v1/records/{id}` | Logged in (owner/admin/shared provider) | Phase 4 — every view is audited, including the owner's own |
| PATCH | `/api/v1/records/{id}` | Logged in + Verified (owner/admin) | Phase 4 — category only |
| DELETE | `/api/v1/records/{id}` | Logged in + Verified (owner/admin) | Phase 4 — soft delete, cascades share revocation |
| POST | `/api/v1/records/{id}/download-url` | Logged in + Verified (owner/admin/shared provider) | Phase 4 |
| POST | `/api/v1/records/{id}/shares` | Logged in + Verified (owner/admin) | Phase 4 — targets a provider or a booking, with an expiry |
| GET | `/api/v1/records/{id}/shares` | Logged in (owner/admin) | Phase 4 |
| POST | `/api/v1/records/shares/{shareId}/revoke` | Logged in + Verified (owner/admin) | Phase 4 |
| GET | `/api/v1/records/{id}/access-log` | Logged in (owner/admin ONLY) | Phase 4 — never exposed to a shared provider |
| GET | `/api/v1/me/onboarding` | Logged in + Consent | Onboarding Part A — status aggregate; `documentsSigned`/`completedAt` always false/null until Part B |
| PUT | `/api/v1/me/onboarding/intake` | Logged in + Consent | Onboarding Part A — upsert, not a merge |
| GET | `/api/v1/onboarding/call-slots` | Logged in + Consent | Onboarding Part A — OPEN, future-only |
| POST | `/api/v1/me/onboarding/call` | Logged in + Consent | Onboarding Part A — at most one active call per user |
| POST | `/api/v1/admin/onboarding/call-slots` | Admin | Onboarding Part A — seeds call slots; no real coordinator-calendar integration |
| `/api/v1/admin/**` | Admin | feature flags, order refunds, finance view, provider onboarding |

Cart (pre-checkout) still isn't implemented — Phase 2 went straight from plan selection to order creation, no intermediate cart resource.

When extending any of the above, reuse `AuthorizationService.requireSelfOrAdmin(...)` (or `.requireBookingAccess(...)` for the three-way owner/provider/admin case) for per-resource ownership and `@RequireVerified`/`@RequireConsent` for the respective gates — all already exist (`com.narturebelle.security`) for this purpose.
