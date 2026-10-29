# API Endpoints & Access Levels

**Interactive docs:** run the app and open `http://localhost:<PORT>/swagger-ui/index.html` (publicly reachable, no token needed just to view it). Click **Authorize** and paste a Firebase ID token to test endpoints that require login directly from the UI. Raw OpenAPI JSON is at `/v3/api-docs`.

Access tiers, per the AUTH CONTRACT between UI and API:

- **Public** — no `Authorization` header needed.
- **Logged in** — valid `Authorization: Bearer <Firebase ID token>` required; user doesn't need to be verified.
- **Logged in + Verified** — valid token AND (`email_verified == true` OR the token carries a verified `phone_number`).
- **Admin** — valid token AND the caller holds any of the 6 `*_ADMIN` roles (`CLINIC_ADMIN`, `HOSPITAL_ADMIN`, `OPERATIONS_ADMIN`, `FINANCE_ADMIN`, `SECURITY_ADMIN`, `SUPER_ADMIN`) — updated from the original single `ADMIN` role in Phase 0's multi-role model.
- **Dev-only** — bean doesn't exist at all when `SPRING_PROFILES_ACTIVE` is `prod` or `stage`; available in `default`/local/`test`.

Errors are JSON `{"code": "...", "message": "..."}` — `401 UNAUTHENTICATED`, `403 EMAIL_NOT_VERIFIED`, `403 FORBIDDEN`, plus the Phase 0/1/2 codes in `UI_INTEGRATION_GUIDE.md` §7-8 (`VALIDATION_FAILED`, `CONSENT_REQUIRED`, `CONSENT_VERSION_OUTDATED`, `JOURNEY_ALREADY_ACTIVE`, `PLAN_NOT_FOUND`, `IDEMPOTENCY_KEY_CONFLICT`, `PAYMENT_NOT_ALLOWED`, `INVALID_WEBHOOK_SIGNATURE`, `REFUND_NOT_ALLOWED`).

**This table predates Phase 0/1/2.** For `/api/v1/me`, consent, journey, dashboard, plans, orders, payments and entitlements — see `UI_INTEGRATION_GUIDE.md` §7 (Phase 0/1) and §8 (Phase 2) for the full request/response contract. Only the original pre-pivot endpoints and the new Phase 2 rows are kept below.

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
| `/api/v1/admin/**` | Admin | feature flags, order refunds, finance view |

Cart (pre-checkout) still isn't implemented — Phase 2 went straight from plan selection to order creation, no intermediate cart resource.

When extending any of the above, reuse `AuthorizationService.requireSelfOrAdmin(...)` for per-resource ownership and `@RequireVerified`/`@RequireConsent` for the respective gates — all already exist (`com.narturebelle.security`) for this purpose.
