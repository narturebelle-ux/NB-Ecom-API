# UI Integration Guide — Firebase Auth + NartureBelle API

For whoever is building the frontend sign-in flow and wiring it to this backend. Everything below is implemented and verified working (real Firebase signup tested end-to-end, not just mocks) unless marked otherwise.

## 1. Firebase client config

Initialize the Firebase **client** SDK (`npm install firebase`) with this exact config — these values are public by design, safe to ship in client code:

```js
const firebaseConfig = {
  apiKey: "AIzaSyB-w7qNi0ENmrXkrI4x6OheElmwEgeL1YI",
  authDomain: "narturebelle-5ae7a.firebaseapp.com",
  projectId: "narturebelle-5ae7a",
  storageBucket: "narturebelle-5ae7a.firebasestorage.app",
  messagingSenderId: "1074948826879",
  appId: "1:1074948826879:web:975562cb7cbcf9196d8eca",
  measurementId: "G-LL9YH3W4TN"
};
```

This is currently the **NonProd** Firebase project (`narturebelle-5ae7a`). There is no separate prod Firebase project yet — only this one exists.

## 2. Sign-in methods enabled on this Firebase project

- **Google**
- **Email/Password**
- **Phone** — ⚠️ new projects get a hard cap of **10 SMS/day** until a billing account (Blaze plan) is added to the Firebase project. You'll hit this fast in testing.
- **Anonymous** — also currently enabled, but **not part of the auth contract** (see §7). If guest/anonymous accounts aren't an intended feature, flag it to get disabled — right now any anonymous sign-in produces a valid token our API will accept as a logged-in user.

## 3. The Auth Contract (implemented exactly as follows)

**There is no app-issued session token.** Every authenticated request sends:
```
Authorization: Bearer <Firebase ID token>
```
Get this token from the client SDK after any sign-in (`await firebaseUser.getIdToken()`).

**Right after any sign-in** (Google, Email/Password, or Phone — doesn't matter which), call:
```
POST /api/v1/auth/firebase
Authorization: Bearer <token>
```
No request body. This creates the user in our DB on first login, or updates email/phone/verification status on every later login. Response, **first-ever login for a new user, signed up via Google**:
```json
{
  "id": 6,
  "firebaseUid": "gKWI7G8oymWbLfIXvxQytHd2vEf1",
  "email": "anjali.verma@example.com",
  "emailVerified": false,
  "phone": null,
  "phoneVerified": false,
  "firstName": "Anjali",
  "lastName": "Verma",
  "stage": null,
  "dueDate": null,
  "role": "MOTHER",
  "roles": ["MOTHER"]
}
```
**Updated by Phase 0 (see §7):** `role` is now the legacy single-role field (`"MOTHER"` for every new sign-up, was `"USER"` before) — kept readable for one release, nothing writes to it any more. Use `roles` (an array) going forward; it's what `/api/v1/admin/**` access and every other authorization check is actually based on now. `stage`/`dueDate` are also legacy — see §7's Journey section for the replacement.
**`firstName`/`lastName` are now auto-populated on account creation — this is new, don't assume they're always null.** Where the guess comes from, and how confident you should treat it:

| Sign-in method | Source | Confidence |
|---|---|---|
| Google | Google's real display name, split on whitespace | High — it's their actual name |
| Email/Password | Guessed from the email's local-part (`anjali.verma@...` → "Anjali"/"Verma") | **Low — just a guess.** Consider showing it pre-filled but editable during onboarding, not as if it were confirmed. |
| Phone only | Nothing to derive from | `firstName`/`lastName` stay `null`, same as before — you still need to ask |

This only happens **once**, at the moment the row is created. Calling `/auth/firebase` again on a later login (or even if the user's Google display name changes) will **never** overwrite `firstName`/`lastName` — once a user has (or has been given) a name, `PUT /users/me` is the only thing that changes it. `stage`/`dueDate` are still always `null` until set via `PUT /users/me` — those aren't derived from anything.

**Profile endpoints:**
```
GET  /api/v1/users/me     — read own profile (just needs login)
PUT  /api/v1/users/me     — update own profile (needs login AND verified)
```
`PUT` body: `{ "firstName": "...", "lastName": "...", "phone": "...", "stage": "MONTH_3|MONTH_6|MONTH_9|BABY_CARE", "dueDate": "2026-05-01" }` — all fields optional, only non-null ones get applied. **Changing `phone` resets `phoneVerified` to `false`** — only a real Firebase phone sign-in sets it back to `true` (via `/auth/firebase`).

**"Verified"** = `email_verified` is `true` on the token, OR the token carries a `phone_number` claim (i.e. phone sign-in). Email/password signups start unverified — you need to call the client SDK's `sendEmailVerification()` yourself; the API doesn't send that email.

**Error shape**, every 401/403:
```json
{ "code": "UNAUTHENTICATED", "message": "..." }
```
Codes: `UNAUTHENTICATED` (401, missing/invalid/expired token), `EMAIL_NOT_VERIFIED` (403, logged in but not verified — only on `PUT /users/me` right now), `FORBIDDEN` (403, wrong role or accessing another user's data).

## 4. Running the API locally for UI dev

There's **no deployed environment yet** — everything so far has only been run and verified locally. To point your local UI dev server at a real running backend:

```powershell
cd NB-Ecomm-API
# load one of: .env (points at the "stage" Supabase project), .env.stage, or .env.prod
Get-Content .env | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { Set-Item "env:$($matches[1])" $matches[2] } }
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew bootRun
```
Default port **8085** (`.env`'s `PORT` value). **Since 2026-10-30 the UI is built against `http://localhost:8080/api/v1`**, so start the API with
`$env:PORT = "8080"` before `bootRun`. Health check: `GET http://localhost:8080/health/db` → `{"database":"ok"}`.

**Sample data and profiles (2026-10-30):** outside the `prod` profile, `DataInitializer` seeds sample products, checklists, hospitals,
nurse packages, doctor sessions, onboarding call slots and Joy Circle events. **Under `prod` it seeds none of these**, so on a fresh
prod DB those lists are empty until content is added through the admin endpoints (§13.7). Versioned config is seeded everywhere:
feature flags, consent documents, care protocol steps, plans, onboarding documents and the vaccine schedule.

**CORS:** `cors.allowed-origins` includes `http://localhost:*` (any local dev port) alongside the real domains — verified live with a preflight request from `http://localhost:4200` (Angular's default dev port): got back `Access-Control-Allow-Origin: http://localhost:4200`, `Access-Control-Allow-Credentials: true`. No action needed on the UI side; any localhost port works. **`Content-Disposition` is exposed** (`Access-Control-Expose-Headers`, 2fa71d8),
so the browser can read the `GET /me/export` filename.

## 5. Full endpoint reference

For live, interactive docs with a "try it out" button: run the app, open `http://localhost:<PORT>/swagger-ui/index.html`, click **Authorize**, paste a Firebase ID token.

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
| `/api/v1/admin/**` | Admin (`*_ADMIN` role) | see §13.7 for the endpoints that now exist (catalog, referrals, Joy Circle events, deletion requests) |

## 6. Known gaps — don't build UI assuming these exist

- **No product cart/ordering endpoints.** Plan orders and payments exist (§8, mock gateway until Phase 7). Product purchases do not,
  so the UI's product basket is labelled as a demo.
- ~~No admin endpoints exist~~. *Superseded 2026-10-30:* see §13.7. Providers, their credentials, service offerings and slots are
  created through the Phase 3 admin endpoints (`/api/v1/admin/providers`, §9). A fresh DB has an empty care directory until an admin
  adds real providers there.
- **No deployed URL** — if the UI needs to point at something other than localhost, that infrastructure doesn't exist yet either.

## 7. Phase 0/1 contract — roles, consent, journey & dashboard

NartureBelle is being rebuilt from a product store into a **care-coordination platform** (see `../NARTUREBELLE_ROADMAP.md`). Everything in this section is new, implemented, and verified live against the real Supabase stage DB with real Firebase-signed-up users — same rigor as the rest of this guide. Sections 1-6 above (Firebase config, sign-in methods, `/auth/firebase`, running locally, Swagger, CORS) are all still accurate and unchanged.

**New error codes**, same `{code, message}` shape as §3, some with extra fields:
| Code | Status | When | Extra fields |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | `@Valid` request body fails, or a malformed/unparseable body | `fields: {name: message}` |
| `CONSENT_REQUIRED` | 403 | Calling a journey or dashboard endpoint while required consents are outstanding | `pending: ["TERMS", "PRIVACY"]` |
| `CONSENT_VERSION_OUTDATED` | 409 | Granting a consent version that isn't the current active one | — |
| `JOURNEY_ALREADY_ACTIVE` | 409 | Creating a journey while a non-CLOSED one already exists | — |
| `USER_NOT_FOUND` | 404 | Any `/me/*` call before `POST /api/v1/auth/firebase` has ever run for this token | — |
| `PHONE_IN_USE` | 400 | `PATCH /api/v1/me` with a phone already claimed by another account | — |

**`/api/v1/me` (new — prefer this over `/api/v1/users/me`, which still works but is deprecated):**
```
GET   /api/v1/me
PATCH /api/v1/me      { "firstName"?, "lastName"?, "phone"? }   — needs Verified (see §3)
```
`GET` response adds two fields on top of the old `/users/me` shape:
```json
{
  "id": 12, "firebaseUid": "...", "email": "...", "emailVerified": true,
  "phone": null, "phoneVerified": false, "firstName": "Anjali", "lastName": "Verma",
  "stage": null, "dueDate": null, "role": "MOTHER", "roles": ["MOTHER"],
  "pendingConsents": ["TERMS", "PRIVACY"],
  "hasActiveJourney": false
}
```
`pendingConsents` is which required documents (if any) still need granting — empty once both are granted. `hasActiveJourney` is true once the user has a non-CLOSED journey. **`stage`/`dueDate` are no longer written by `PATCH /api/v1/me`** (or the old `PUT /users/me`) even if you send them — they stay on the response for one release but are frozen; use the Journey endpoints below instead.

**Consent — gate everything below this point:**
```
GET  /api/v1/consent-documents/current                        — Public, no auth needed
GET  /api/v1/me/consents                                      — Logged in
POST /api/v1/me/consents       { "documentType", "documentVersion" }
POST /api/v1/me/consents/{type}/revoke      ({type} = "TERMS" | "PRIVACY")
```
`GET /consent-documents/current` → `[{ "id", "type": "TERMS"|"PRIVACY", "version", "title", "bodyMarkdown", "effectiveAt", "active" }, ...]`. **Both documents are currently DRAFT placeholder text pending legal review — label them as drafts in the UI, don't present as final.**

`GET /me/consents` → `{ "history": [...grants, incl. revoked...], "pending": ["TERMS", "PRIVACY"] }`. Show the consent screen whenever `pending` (from here or from `/me`) is non-empty — once `pending` is empty, journey and dashboard endpoints stop 403ing.

`POST /me/consents` with the type+version from `/consent-documents/current` → 200 with the saved grant, or 409 `CONSENT_VERSION_OUTDATED` if the version you sent isn't current (re-fetch `/consent-documents/current` and show the user the new text). Revoking with nothing currently granted for that type → 404.

**Journey — the pregnancy/postpartum stage tracker:**
```
POST  /api/v1/me/journeys       { "status", "expectedDeliveryDate"? }
GET   /api/v1/me/journeys
GET   /api/v1/me/journeys/{id}
PATCH /api/v1/me/journeys/{id}  { "status"?, "expectedDeliveryDate"? }
```
All four require consent (403 `CONSENT_REQUIRED` otherwise). `status` is one of `PREGNANCY, PRE_DELIVERY, DELIVERY_COMPLETED, POSTPARTUM, CHILDCARE_0_7, CLOSED`. `expectedDeliveryDate` (the EDD) is **always optional** — sign-up and this flow collect nothing about the pregnancy by default; it's set later if/when the user chooses to. If sent, it can't be more than 42 weeks in the future or 1 year in the past (400 `VALIDATION_FAILED` otherwise).

Creating a second journey while one is still active (anything other than `CLOSED`) → 409 `JOURNEY_ALREADY_ACTIVE`. Close the current one first (`PATCH` with `{"status":"CLOSED"}`) to start a new one. `GET`/`PATCH /{id}` are owner-only (403 `FORBIDDEN` for another user's journey).

Response shape:
```json
{
  "id": 4, "status": "PREGNANCY", "expectedDeliveryDate": "2027-08-04",
  "startedAt": "2026-10-28T02:29:21.35", "closedAt": null,
  "gestationalWeek": 1, "trimester": 1
}
```
`gestationalWeek`/`trimester` are computed on every response, **never stored** — both `null` unless `status` is `PREGNANCY` **and** an EDD is on file. Trimester 1 = weeks 1-13, 2 = 14-27, 3 = 28+.

**Dashboard — read-only, pulls everything above together:**
```
GET /api/v1/me/dashboard      — requires consent, same 403 CONSENT_REQUIRED gate
```
```json
{
  "profile": { "firstName": "Anjali", "lastName": "Verma" },
  "pendingConsents": [],
  "journey": { "...same shape as above, or null if none active..." },
  "nextActions": [
    { "professionalType": "GYNECOLOGIST", "title": "See your Gynecologist",
      "description": "Routine first-trimester prenatal check-up.",
      "reviewStatus": "PENDING_CLINICAL_REVIEW", "optional": false }
  ],
  "journeySetupRequired": false
}
```
If there's no active journey, `journey` is `null`, `journeySetupRequired` is `true`, and `nextActions` is empty — prompt the user to create one. **`nextActions` is a forward-looking roadmap, not just "the one next thing"**: it includes the user's current stage and everything after it (so late sign-ups never see earlier stages marked as missed), but never anything from an earlier stage. `professionalType` values today: `GYNECOLOGIST`, `NUTRITIONIST`, `LACTATION_CONSULTANT`, `PEDIATRICIAN`, `POSTPARTUM_PHYSIOTHERAPIST`, `CLINICAL_PSYCHOLOGIST`. The psychologist item (`optional: true`) always appears, at every stage — offer it, don't push it. **Every item is `reviewStatus: "PENDING_CLINICAL_REVIEW"` right now — this content hasn't been clinically signed off yet; say so if you surface it in the UI, don't present it as medical advice.**

## 8. Phase 2 contract — plans, checkout & entitlements

Verified live against the real stage Supabase DB with a real Firebase-signed-up, verified user — same rigor as §7. "Checkout" here means our own `/orders` + `/payments/create` + mock succeed/fail; there's no separate cart resource (plan selection goes straight to an order).

**New error codes**, same `{code, message}` shape as §7:
| Code | Status | When |
|---|---|---|
| `PLAN_NOT_FOUND` | 404 | `planId` on `POST /orders` has no active version |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | Same `Idempotency-Key` reused with a different `planId`/`paymentMode` |
| `PAYMENT_NOT_ALLOWED` | 409 | `POST /payments/create` for an order that isn't `PENDING` |
| `PAYMENT_NOT_FOUND` | 404 | Unknown payment id |
| `INVALID_WEBHOOK_SIGNATURE` | 401 | Webhook signature didn't verify |
| `REFUND_NOT_ALLOWED` | 409 | Refund on a non-paid order, or amount exceeds what's left refundable |
| `VALIDATION_FAILED` | 400 | Also fires if `Idempotency-Key` is missing on `POST /orders` |

**Plans — public, no auth needed:**
```
GET /api/v1/plans
GET /api/v1/plans/{id}
```
```json
{
  "id": 1, "code": "STANDARD", "name": "Standard",
  "oneTimePrice": 9999.00, "installmentTotalPrice": 11998.80, "installmentCount": 2,
  "totalSessions": 9, "validityDays": 365,
  "inclusions": ["9 total sessions across your assigned care team", "..."],
  "exclusions": ["Hospital delivery charges", "Sessions beyond the allocated count"],
  "allocations": [{ "professionalType": "GYNECOLOGIST", "sessionCount": 5 }, { "professionalType": "NUTRITIONIST", "sessionCount": 4 }]
}
```
3 tiers exist: `STANDARD` (₹9,999/9 sessions), `CARE` (₹14,999/12), `PREMIUM` (₹24,999/16). **The per-professional allocation, validity window (365 days everywhere) and inclusion/exclusion copy are placeholder defaults, not final "blueprint §5" numbers** — that source document wasn't available when these were seeded. Treat them as provisional; they'll change via a later migration, same caveat as Phase 1's DRAFT consent text. `installmentTotalPrice` = one-time price × 1.20, `installmentCount` is always 2 right now (also a placeholder).

**Orders — `Logged in + Verified`:**
```
POST /api/v1/orders    { "planId", "paymentMode": "ONE_TIME" | "INSTALLMENT" }
Idempotency-Key: <any client-generated unique string>   -- required header, 400 VALIDATION_FAILED if missing
GET  /api/v1/orders
GET  /api/v1/orders/{id}
```
Generate a fresh `Idempotency-Key` (e.g. a UUID) per checkout *attempt*, and **reuse the exact same key** if you retry the same request (network error, double-tap, etc.) — the server returns the original order instead of creating a duplicate. Reusing a key with a *different* `planId`/`paymentMode` is rejected (409 `IDEMPOTENCY_KEY_CONFLICT`) rather than silently doing the wrong thing.

Response:
```json
{
  "id": 1, "planVersionId": 1, "planCode": "STANDARD", "status": "PENDING",
  "paymentMode": "ONE_TIME", "price": 9999.00,
  "installmentSchedule": [],
  "refunds": [],
  "createdAt": "2026-10-28T23:25:56.49"
}
```
`price` is always server-computed from the plan's current active version — there's nowhere to send a price from the client at all. `status` is one of `PENDING, PAID, FAILED, REFUNDED, PARTIALLY_REFUNDED, DISPUTED` (`DISPUTED` isn't reachable yet — no real gateway sends dispute events until Phase 7). For `paymentMode: "INSTALLMENT"`, `installmentSchedule` is populated: `[{ "installmentNumber": 1, "amount": 5999.40, "dueDate": "2026-10-28" }, { "installmentNumber": 2, "amount": 5999.40, "dueDate": "2026-11-27" }]` — **this is a read-only breakdown for display only**. The mock payment in this phase always settles the *entire* order in one step regardless of installment vs one-time; real installment collection is Phase 7.

**Payments — the mock gateway:**
```
POST /api/v1/payments/create                          { "orderId" }   -- Logged in + Verified
POST /api/v1/payments/{id}/mock/succeed                               -- Logged in + Verified
POST /api/v1/payments/{id}/mock/fail                                  -- Logged in + Verified
POST /api/v1/payments/webhook                                         -- Public, signature-verified
```
`POST /payments/create` response — **this answers exactly what a "checkout" step hands back in this phase**:
```json
{
  "id": 1, "orderId": 1, "status": "CREATED", "amount": 9999.00, "provider": "MOCK",
  "mockSucceedPath": "/api/v1/payments/1/mock/succeed",
  "mockFailPath": "/api/v1/payments/1/mock/fail"
}
```
There's no hosted checkout page or redirect in Phase 2 — build your own "simulated payment" screen with two buttons that `POST` to `mockSucceedPath`/`mockFailPath` (same Firebase bearer token as everything else, no special auth). Each internally builds a real signed webhook event and runs it through the exact same processing path the public `/payments/webhook` endpoint uses, so the result is indistinguishable from a real gateway round-trip: `{"status": "PAYMENT_SUCCEEDED"}` or `{"status": "PAYMENT_FAILED"}`, and the order/entitlements update accordingly. Calling `mock/succeed` more than once is harmless (idempotent, no duplicate entitlements). **These two endpoints 404 once Phase 7 ships a real gateway** — don't build the "simulated checkout" screen as anything other than a clearly-temporary prototype affordance.

**Entitlements — the session wallet, `Logged in` (no Verified gate — viewing is lower-stakes than paying):**
```
GET /api/v1/entitlements
```
```json
[
  { "id": 1, "orderId": 1, "professionalType": "GYNECOLOGIST", "totalSessions": 5, "usedSessions": 0, "remainingSessions": 5, "expiresAt": "2027-10-28T23:26:06.76", "planCode": "STANDARD" },
  { "id": 2, "orderId": 1, "professionalType": "NUTRITIONIST", "totalSessions": 4, "usedSessions": 0, "remainingSessions": 4, "expiresAt": "...", "planCode": "STANDARD" }
]
```
Granted automatically the moment a payment succeeds — nothing to call to "claim" them. `usedSessions` is always `0` right now: there's no booking/session-completion flow yet (that's Phase 3) to consume one.

**Admin (finance) — not part of the UI's own build, documented for completeness:**
```
POST /api/v1/admin/orders/{id}/refunds   { "amount", "reason" }
GET  /api/v1/admin/finance/summary
```
Refunds are always an admin-entered amount — there's no "calculate the refund" button anywhere, by design (the refund/unused-session policy, decision log #4, is still open).

## 9. Phase 3 contract — providers, booking & sessions

Verified live against the real stage Supabase DB (the public endpoints and the `@RequireVerified` gate — the admin-only provider-onboarding chain is covered by the automated test suite instead; see the note at the end of this section).

**New error codes**, same `{code, message}` shape as §7/§8:
| Code | Status | When |
|---|---|---|
| `PROVIDER_NOT_FOUND` | 404 | Unknown `providerId` |
| `PROVIDER_NOT_BOOKABLE` | 409 | Provider isn't `VERIFIED`/`ACTIVE` |
| `INVALID_PROVIDER_STATUS_TRANSITION` | 409 | Illegal admin status change (admin-only, see below) |
| `SERVICE_OFFERING_NOT_FOUND` | 404 | Unknown/inactive `serviceOfferingId` for that provider |
| `SLOT_NOT_AVAILABLE` | 409 | Slot doesn't exist, isn't `OPEN`, or lost the booking race |
| `NO_ENTITLEMENT_AVAILABLE` | 409 | No remaining session of that provider's professional type |
| `BOOKING_NOT_ALLOWED` | 409 | Wrong-state action (e.g. completing an already-`COMPLETED` booking, cancelling a non-`BOOKED` one) |
| `FEEDBACK_NOT_ALLOWED` | 409 | Feedback before completion, or a second submission |

**Provider directory — public, no auth needed:**
```
GET /api/v1/providers                         ?professionalType=&language=&city=&modality=   (all optional)
GET /api/v1/providers/{id}
GET /api/v1/providers/{id}/slots               -- OPEN, future-only, ordered
```
Only `VERIFIED`/`ACTIVE` providers are ever listed. `professionalType` is one of `GYNECOLOGIST, NUTRITIONIST, LACTATION_CONSULTANT, PEDIATRICIAN, POSTPARTUM_PHYSIOTHERAPIST, CLINICAL_PSYCHOLOGIST, REGISTERED_NURSE`; `modality` is `VIRTUAL` or `PHYSICAL`. Provider response never includes credentials or the linked `userId`. **`services` carries the provider's active offerings — this is where a real `serviceOfferingId` for `POST /bookings` comes from** (added after initial publish once the UI team flagged that `POST /bookings` needs one and nothing exposed it):
```json
{
  "id": 1, "professionalType": "GYNECOLOGIST", "displayName": "Dr. ...", "bio": "...",
  "city": "Bengaluru", "languages": ["English", "Hindi"],
  "services": [
    { "id": 3, "name": "Consultation", "description": null, "modality": "VIRTUAL", "durationMinutes": 30 }
  ]
}
```
A provider with `services: []` has nothing bookable yet — don't guess an id, show a "not taking bookings yet" state instead. Slots: `{ "id": 7, "startAt": "2026-11-01T10:00:00", "endAt": "2026-11-01T10:30:00", "status": "OPEN" }`.

**No video-call integration in this phase** — `modality` on a provider's service offerings is filter/display metadata only; there's no meeting link anywhere in any response. Build the "join session" UI as an explicit placeholder if you need one at all right now.

**Bookings — `Logged in + Verified`:**
```
POST /api/v1/bookings          { "providerId", "serviceOfferingId", "slotId" }
GET  /api/v1/bookings          -- your own, as the booking user OR as the assigned provider if your account is one
GET  /api/v1/bookings/{id}     -- owner, assigned provider, or admin
POST /api/v1/bookings/{id}/cancel       { "reason" }           -- owner, assigned provider, or admin
POST /api/v1/bookings/{id}/reschedule   { "newSlotId", "reason" }
```
No `Idempotency-Key` here (unlike `POST /orders`) — the slot's own claim uniqueness makes a duplicate click safely 409 on its own. Response:
```json
{
  "id": 12, "userId": 7, "providerId": 1, "serviceOfferingId": 3, "slotId": 7,
  "professionalType": "GYNECOLOGIST", "status": "BOOKED",
  "scheduledStart": "2026-11-01T10:00:00", "scheduledEnd": "2026-11-01T10:30:00",
  "providerNote": null, "noteLockedAt": null, "rescheduledFromBookingId": null,
  "createdAt": "2026-10-29T08:03:00"
}
```
`status` is one of `BOOKED, CANCELLED, RESCHEDULED, IN_PROGRESS, COMPLETED, NO_SHOW_PROVIDER, NO_SHOW_USER`. **Booking a session does not consume anything from `GET /entitlements` yet** — only completing it does (see below). Rescheduling never cancels in the usual sense: the old booking becomes `RESCHEDULED` (not `CANCELLED`), freeing its slot, and a brand-new booking is created referencing it via `rescheduledFromBookingId`.

**Session lifecycle — provider-or-admin only for start/complete:**
```
POST  /api/v1/bookings/{id}/start                                  -- requires BOOKED
POST  /api/v1/bookings/{id}/complete      { "note" }                -- requires BOOKED or IN_PROGRESS; note is required
POST  /api/v1/bookings/{id}/no-show       { "party", "reason" }    -- party: "PROVIDER" | "USER"
PATCH /api/v1/bookings/{id}/note          { "note", "reason" }      -- only after COMPLETED
```
**`complete` is the only action that consumes a session** — it decrements the matching `GET /entitlements` row's `remainingSessions` by exactly 1 (FIFO by the entitlement's own expiry) and creates a payout record on the provider's side; nothing else in this list touches entitlements or payouts. Notes lock the moment a session completes — `PATCH .../note` after that writes a new version and is itself audited (an append-only history, not shown via the API yet, but every amendment is tracked server-side). **No-show reporting is party-restricted**: the booking's own user can only report `"PROVIDER"` (the provider didn't show), the assigned provider can only report `"USER"` — reporting your own side is rejected. No-show consumes nothing and pays out nothing (the refund/cancellation policy, decision log, is still open — this is a safe default, not a final rule).

**Feedback:**
```
POST /api/v1/bookings/{id}/feedback   { "rating": 1-5, "comment"? }   -- booking's own user only, only after COMPLETED, only once
```

**Admin provider onboarding — not part of the UI's own build, documented for completeness (and because "how do providers sign in" depends on it):**
```
POST   /api/v1/admin/providers                    { "userId", "professionalType", "displayName"?, "bio"?, "city"?, "languages"? }
GET    /api/v1/admin/providers[/{id}]
POST   /api/v1/admin/providers/{id}/verify         { "reason" }     -- requires current status UNDER_REVIEW
PATCH  /api/v1/admin/providers/{id}/status         { "status", "reason" }
POST   /api/v1/admin/providers/{id}/credentials    { "credentialType", "documentReference"?, "issuedAt"?, "expiresAt"? }
POST   /api/v1/admin/providers/{id}/services       { "name", "description"?, "modality", "durationMinutes" }
POST   /api/v1/admin/providers/{id}/slots          { "slots": [{ "startAt", "endAt" }, ...] }
```
Status machine: `INVITED → DOCUMENTS_PENDING → UNDER_REVIEW → VERIFIED → ACTIVE/PAUSED/SUSPENDED → DEACTIVATED` (terminal). `PATCH .../status` never accepts `VERIFIED` as a target — only `/verify` grants it, because that's also the moment the professional's account gains the matching role (e.g. `GYNECOLOGIST`) and becomes a bookable provider. **No self-service exists for providers yet** — a professional must already have signed up normally through Firebase before an admin can create their `Provider` profile; there's no pre-signup invite flow, and credentials/services/slots are admin-entered only (no provider portal in this phase, same reasoning the roadmap uses to defer it on the UI side too).

**Note on this section's verification**: the public endpoints and the `@RequireVerified`/`EMAIL_NOT_VERIFIED` gate on `POST /bookings` were confirmed live against the real stage database. The admin-only onboarding chain (create → verify → book → complete) could not be re-verified live the same way — doing so would have required extracting the stage DB password to hand-grant an admin role, which this session's safety guardrail correctly blocked (same situation as Phase 2's finance endpoints). It's covered instead by `ProviderTest`/`BookingTest`, including a dedicated test proving that verifying a provider grants their role immediately on the next request with no new Firebase ID token required.

## 10. Phase 4 contract — records vault & sharing

Migration applied live against the real stage Supabase DB (V10, clean on top of V1-V9). **Not live-verified**: actual upload/download against a real bucket — `SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY` don't exist yet for the stage/prod environments, so those two calls currently hit a placeholder host and will fail until the real Supabase Storage credentials are added (see the note at the end of this section). Everything else (CRUD, sharing, access log, authorization) is covered by the automated test suite (`RecordsTest`) using a mocked storage provider.

**New error codes**, same `{code, message}` shape as §7-§9:
| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Disallowed mime type, oversized file, invalid/missing share target, bad expiry |
| `RECORD_UPLOAD_NOT_CONFIRMED` | 409 | `POST /records` called before the file was actually uploaded to the signed URL |
| `RECORD_NOT_ALLOWED` | 409 | Wrong-state action on a record |
| `HEALTH_RECORD_NOT_FOUND` / `RECORD_SHARE_NOT_FOUND` | 404 | Reserved codes; current endpoints return a plain 404 body instead (see note below) |

**Upload — two calls, in order, `Logged in + Verified`:**
```
POST /api/v1/records/upload-url   { "category", "fileName", "mimeType", "sizeBytes" }
     -> { "objectPath", "uploadUrl", "token", "expiresAt" }     -- no DB row yet
```
Upload the file's bytes directly to `uploadUrl` (Supabase Storage's signed-upload mechanism — PUT/POST the raw bytes, not JSON), then:
```
POST /api/v1/records               { "category", "objectPath", "originalFileName", "mimeType", "sizeBytes" }
     -> HealthRecordResponse        -- server verifies the object actually exists before creating the row
```
`category` is one of `SCAN, LAB_REPORT, PRESCRIPTION, DISCHARGE_SUMMARY, OTHER` (matches the UI's existing client-side enum). Allowed mime types and the max size are config, not hardcoded in this guide — current placeholder defaults: `application/pdf, image/jpeg, image/png, image/heic`, 25 MB. `HealthRecordResponse`:
```json
{
  "id": 1, "userId": 7, "category": "LAB_REPORT", "originalFileName": "blood-work.pdf",
  "mimeType": "application/pdf", "sizeBytes": 1048576, "status": "UPLOADED", "scanStatus": "NOT_SCANNED",
  "createdAt": "2026-10-29T10:00:00", "updatedAt": "2026-10-29T10:00:00"
}
```
**`scanStatus` is always `NOT_SCANNED` right now — there is no real malware scanning yet.** This is deliberately explicit, not hidden behind a `CLEAN` default.

**List / view / edit / delete — owner, admin, or a provider the record is shared with:**
```
GET    /api/v1/records                    -- your own records, excludes deleted
GET    /api/v1/records/shared-with-me     -- as a provider: records shared with you directly or via one of your bookings; [] if your account isn't a provider
GET    /api/v1/records/{id}               -- owner / admin / shared provider; every view is audited, including your own
PATCH  /api/v1/records/{id}                { "category" }     -- owner or admin only
DELETE /api/v1/records/{id}                                   -- owner or admin only; soft delete, cascades share revocation
POST   /api/v1/records/{id}/download-url                      -- owner / admin / shared provider -> { "downloadUrl", "expiresAt" }
```

**Sharing — owner or admin only:**
```
POST /api/v1/records/{id}/shares   { "shareTargetType": "PROVIDER", "targetProviderId", "expiresAt" }
POST /api/v1/records/{id}/shares   { "shareTargetType": "BOOKING",  "targetBookingId",  "expiresAt" }
GET  /api/v1/records/{id}/shares                                -- owner or admin only
POST /api/v1/records/shares/{shareId}/revoke   { "reason"? }
```
Exactly one of `targetProviderId`/`targetBookingId` must be set, matching `shareTargetType`. A `BOOKING` share resolves live to whichever provider is currently assigned to that booking — if the booking's provider changes, so does who can see the record. `expiresAt` is always required (no indefinite shares), bounded by a placeholder max of 180 days. A `BOOKING` target must be a booking the caller themself owns (as the booking's user), or the request is rejected.

**Access log — owner or admin ONLY, never a shared provider:**
```
GET /api/v1/records/{id}/access-log
```
```json
[{ "id": 1, "recordId": 1, "recordName": "blood-work.pdf", "action": "VIEW", "actorRelationship": "OWNER", "occurredAt": "2026-10-29T10:05:00" }]
```
`action` is one of `UPLOAD, VIEW, DOWNLOAD, SHARE, REVOKE_SHARE, DELETE`. **`recordName` is the only place a filename appears in this entire log/sharing surface** — it's joined in at response time from the record itself, never stored on the log row. The underlying `record_access_events` table has no column for filename, mime type, size or category at all, by design (roadmap requirement: "no document names or health data in logs").

**Note on this section**: `GET /records/{id}` and `/{id}/shares`/`/access-log` return a plain 404 (no body/code) for a missing id, consistent with every other GET-by-id endpoint in this API (see Known Gaps) rather than the reserved `HEALTH_RECORD_NOT_FOUND`/`RECORD_SHARE_NOT_FOUND` codes, which exist in the error contract but aren't currently thrown anywhere. **Storage is Supabase Storage in `us-east-1`** (decision log #8) — same project/region as the DB, behind a `StorageProvider` interface so a later region move is contained. Real upload/download needs `SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY` added to the stage/prod environment — until then those two calls will fail against a placeholder host while everything else (metadata CRUD, sharing, access log) works normally.

## 11. Onboarding contract (Part A) — intake + onboarding-call booking

Added after Phase 4, per the product owner's post-signup onboarding requirement. **Part A only**: intake and call booking. Documents/e-signature/the completion endpoint (`POST /me/onboarding/documents/sign`, `POST /me/onboarding/complete`) are **not built yet** — e-signing is specified to email the signed copy, which needs Phase 5's notification service; those land once Phase 5 ships. Stage and EDD are **not** part of this contract — they already go through the real Journey API (`POST/PATCH /me/journeys`), unchanged.

**Every endpoint in this section requires `@RequireConsent`** (403 `CONSENT_REQUIRED` if pending), same gate as Journey/Dashboard — onboarding comes right after the consent step and collects health-adjacent data.

**New error codes**, same `{code, message}` shape as §7-§10:
| Code | Status | When |
|---|---|---|
| `ONBOARDING_CALL_SLOT_NOT_AVAILABLE` | 409 | Slot doesn't exist, isn't `OPEN`, or lost the booking race |
| `ONBOARDING_CALL_ALREADY_BOOKED` | 409 | Caller already has an active onboarding call |
| `ONBOARDING_DOCUMENT_NOT_FOUND` | 404 | Unknown `documentId` to `/documents/sign` |
| `ONBOARDING_INCOMPLETE` | 409 | `/complete` called before every required step is satisfied — body includes `missingSteps` |

**Status aggregate — `Logged in`:**
```
GET /api/v1/me/onboarding
```
```json
{
  "intake": { "id": 1, "firstPregnancy": true, "multiples": false,
    "riskFactors": ["DIABETES", "THYROID"], "supportWanted": ["Nutrition guidance"],
    "city": "Bengaluru", "language": "English", "preferredModality": "VIRTUAL",
    "phone": "9999999999", "notes": "...", "createdAt": "...", "updatedAt": "..." } | null,
  "call": { "id": 1, "slotId": 7, "scheduledStart": "...", "scheduledEnd": "...", "createdAt": "..." } | null,
  "callbackRequestedAt": "2026-10-29T12:00:00" | null,
  "documentsSigned": false,
  "completedAt": null
}
```
**`documentsSigned` and `completedAt` are now real** (Part B has shipped) — `documentsSigned` is `true` once every currently-active onboarding document has a signature from this user (vacuously `true` if there are none active); `completedAt` is set only after a successful `POST /me/onboarding/complete`. `callbackRequestedAt` reflects the caller's most recent `POST /me/onboarding/callback-request`, if any — added so the UI doesn't have to track that locally. `riskFactors` is one of `HIGH_BLOOD_PRESSURE, DIABETES, THYROID, PREVIOUS_C_SECTION, PREVIOUS_COMPLICATIONS`; `preferredModality` reuses the existing `Modality` enum (`VIRTUAL`/`PHYSICAL`) from Phase 3. `supportWanted` is free-form strings, not a closed enum on this side — send whatever the UI's own option set is.

**Intake — upsert, `Logged in`:**
```
PUT /api/v1/me/onboarding/intake   { "firstPregnancy", "multiples", "riskFactors": [...], "supportWanted": [...],
                                      "city", "language", "preferredModality", "phone", "notes" }
     -> same shape as the `intake` object above
```
All fields optional/nullable — call it with whatever's been answered so far; calling it again replaces the previous answers (not a merge/patch). No separate create vs. update call: the first `PUT` creates the row, every later `PUT` overwrites it.

**Call booking:**
```
GET  /api/v1/onboarding/call-slots           -- OPEN, future-only, ordered
POST /api/v1/me/onboarding/call   { "slotId" }
```
```json
{ "id": 7, "startAt": "2026-11-02T10:00:00", "endAt": "2026-11-02T10:30:00", "status": "OPEN" }
```
At most one active call booking per user — booking a second one before cancelling the first is a 409 `ONBOARDING_CALL_ALREADY_BOOKED`. **No cancel/reschedule endpoint yet** — not in the proposed contract, and the roadmap note on "no coordinator portal" applies here the same way it did to Phase 3's provider portal. Slots are seeded two ways: `DataInitializer` auto-seeds a placeholder weekday 10am slate for the next two weeks on every environment's first boot (so there's always something bookable), and `POST /admin/onboarding/call-slots` (same shape as Phase 3's `POST /admin/providers/{id}/slots`) lets an admin add more. There's no real coordinator-calendar integration behind either path.

**Callback-request fallback — for when `call-slots` returns `[]`:**
```
POST /api/v1/me/onboarding/callback-request   { "note"? }   -> { "id", "userId", "note", "createdAt" }
```
Use this instead of a browser-only "ask for a call" state — it's a real server-side record (plus an audit event) a coordinator can act on via `GET /admin/onboarding/callback-requests` (admin-only, since there's no coordinator portal). `note` is optional, max 500 characters.

**Documents, e-signature, completion (Part B) — `Logged in + Verified` for the mutating calls:**
```
GET  /api/v1/me/onboarding/documents
     -> [{ "id", "type": "ONBOARDING_AGREEMENT", "version", "title", "bodyMarkdown", "effectiveAt", "signed" }]
POST /api/v1/me/onboarding/documents/sign   { "documentId", "typedName" }
     -> { "id", "documentId", "typedName", "signedAt" }
POST /api/v1/me/onboarding/complete
     -> { "completedAt" }
```
`bodyMarkdown` is **DRAFT placeholder text pending legal review** — render it as-is, don't treat it as final copy. Signing is idempotent: signing the same document twice just returns the original signature (same `typedName`/`signedAt`), it doesn't error or create a second record. Signing triggers an email to the user's registered address confirming the signed document — **no real email is sent yet** (no provider decided, same as Phase 5's EMAIL channel), so don't tell the user "check your inbox," something closer to "a copy will be emailed to you" is more honest right now.

`POST /me/onboarding/complete` checks, in this order, and returns 409 `ONBOARDING_INCOMPLETE` with a `missingSteps` array (`"intake"`, `"onboarding call"`, `"payment"`, `"document signatures"`) naming everything still missing, not just the first one found:
1. An intake has been saved (`PUT .../intake` called at least once).
2. Either an onboarding call is booked **or** a callback request was made — a deliberate choice: a callback request is the sanctioned fallback when no slots exist, so it satisfies this step too, not just a booked call.
3. At least one of the caller's orders is `PAID`.
4. Every currently active onboarding document has this user's signature.

Completion is idempotent — calling it again after success just returns the same `completedAt`, never a second completion or an error. Once `completedAt` is non-null (either from this call or from `GET /me/onboarding`), the user is onboarded — per decision log #12, that's the whole rule, no coordinator confirmation step.

## 12. Phase 5 contract — reminders & notifications

Verified live against the real stage Supabase DB (migration V12 applies cleanly on top of V1-V11). Covered by `NotificationsTest`: preference defaults/overrides, idempotent reminder generation, ownership on dismiss, consent-skip, and the payment-reconciliation dedupe.

**New error codes**: none — these endpoints only 404/403, no new domain errors.

**Reminders — own, `Logged in`:**
```
GET  /api/v1/me/reminders                 -- ACTIVE only, newest first
POST /api/v1/me/reminders/{id}/dismiss    -- 403 if it isn't yours
```
```json
{ "id": 1, "type": "BOOKING_UPCOMING", "title": "Upcoming care session",
  "body": "You have an upcoming care session scheduled in the next 24 hours.",
  "dueAt": "2026-11-01T10:00:00", "sourceType": "BOOKING", "sourceId": 12,
  "status": "ACTIVE", "createdAt": "..." }
```
`type` is `BOOKING_UPCOMING` or `JOURNEY_NEXT_ACTION`. **Message text is deliberately generic** — never a protocol-step title, provider name, or any clinical specific — per the roadmap's "no health details in message text." Reminders are server-generated (a background job scans bookings starting in the next 24h and active journeys with pending next actions, every 15 minutes) — there's no `POST` to create one directly. Generation skips any user with pending required consents.

**Notification channel preferences — own, `Logged in`:**
```
GET /api/v1/me/notification-preferences
PUT /api/v1/me/notification-preferences/{channel}   { "enabled" }
```
```json
[{ "channel": "IN_APP", "enabled": true }, { "channel": "EMAIL", "enabled": false },
 { "channel": "SMS", "enabled": false }, { "channel": "WHATSAPP", "enabled": false }]
```
`channel` is `IN_APP, EMAIL, SMS, WHATSAPP`. **IN_APP defaults on, everything else defaults off** until the user explicitly enables it. **Enabling EMAIL/SMS/WHATSAPP changes nothing real right now** — no provider is wired in (decision log #9 is still blank), so those channels log intent server-side only. Don't build UI copy that implies a real email/SMS/WhatsApp message will be sent.

## 13. Phase 6 contract — P1 expansion (child, referrals, Joy Circle, recommendations, export/deletion)

> *Written 2026-10-30 by the UI agent, at the product owner's request, from the code at `firebase-setup` 43c0d60 (commits 2a4601a,
> 9999f26, 848ac87, 05cffec, 2fa71d8, 43c0d60). API agent: please correct anything that's wrong.*

Shared rules: every `/me/**` route needs a Bearer token **and** consent (403 `CONSENT_REQUIRED`). The exceptions are export and deletion (§13.5),
which deliberately skip the consent check. Dates are `yyyy-MM-dd` and times are ISO `LocalDateTime`. Errors use the usual `{code, message}` shape.
A resource owned by another user returns 404, not 403. **Feature flags** `childTracker`, `joyCircle` and `productRecommendations` are seeded **off**,
and turning them on is the product owner's call.

### 13.1 Child + vaccination tracker (2a4601a)
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/v1/me/children` | | `[Child]` |
| POST | `/api/v1/me/children` | `{name* (≤255), dateOfBirth* (past or today), sex?}` | `Child` (200) |
| PATCH | `/api/v1/me/children/{id}` | `{name?, dateOfBirth?, sex?}` | `Child` |
| GET | `/api/v1/me/children/{id}/vaccinations` | | `{childId, schedule:{id, version (int), title, reviewStatus}, items:[VaccinationItem]}` |
| PUT | `/api/v1/me/children/{id}/vaccinations/{scheduleItemId}` | `{givenAt* (past or today), givenWhere? (≤255)}` | `VaccinationItem` (idempotent upsert) |
| DELETE | same path | | 204 (unmark) |

`Child = {id, name, dateOfBirth, sex: FEMALE|MALE|UNDISCLOSED|null, createdAt}`.
`VaccinationItem = {scheduleItemId, vaccineName, dueAgeDays, dueAgeLabel, dueDate, status, givenAt, givenWhere}`. `status` is `DONE` if recorded,
otherwise `OVERDUE` if `dueDate` < today, `DUE` if within the next 30 days, else `UPCOMING`. The schedule is v1, `reviewStatus: PENDING_CLINICAL_REVIEW`.
Errors: 404 `CHILD_NOT_FOUND`, 404 `VACCINE_SCHEDULE_ITEM_NOT_FOUND`. Guardian/family sharing is not built.

### 13.2 Hospital referrals (9999f26)
| Method | Path | Access | Body / Returns |
|---|---|---|---|
| POST | `/api/v1/me/hospital-referrals` | Logged in + Verified + consent | `{hospitalId*, note? (≤1000)}` → `Referral`. Idempotent: an open referral to the same hospital is returned as-is |
| GET | `/api/v1/me/hospital-referrals` | Logged in + consent | `[Referral]` |

`Referral = {id, code ("NB-" + 8 chars, no user id), hospitalId, hospitalName, status: REQUESTED|CONFIRMED, note, createdAt, confirmedAt}`.
Error: 404 `HOSPITAL_NOT_FOUND`. `/api/v1/hospitals` is unchanged.

### 13.3 Joy Circle — non-clinical community events (848ac87)
| Method | Path | Access | Returns |
|---|---|---|---|
| GET | `/api/v1/joy-circle/events?includePast=false` | Public | `[Event]` (upcoming only by default) |
| GET | `/api/v1/joy-circle/events/{id}` | Public | `Event` |
| POST | `/api/v1/joy-circle/events/{id}/rsvp` | Logged in + consent | `Rsvp` (idempotent) |
| DELETE | `/api/v1/joy-circle/events/{id}/rsvp` | Logged in + consent | 204 |
| GET | `/api/v1/me/joy-circle/rsvps` | Logged in + consent | `[Rsvp]` |

`Event = {id, title, description, hostName, hostBio, category, startsAt, endsAt, modality: VIRTUAL|PHYSICAL, location, capacity (null = unlimited),
rsvpCount, spotsLeft (null if unlimited), status: SCHEDULED|CANCELLED, disclaimer}`.
`Rsvp = {eventId, event: Event (with the updated rsvpCount/spotsLeft), joinUrl, rsvpAt}`. **`joinUrl` is never on the public Event.**
Errors: 404 `EVENT_NOT_FOUND`, 409 `EVENT_FULL`, 409 `EVENT_NOT_OPEN` (cancelled or already started).

### 13.4 Product recommendations (848ac87)
| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/me/product-recommendations?category=&maxPrice=&includeDismissed=false` | `{stage, commissionDisclosure, items:[Recommendation]}` |
| POST | `/api/v1/me/product-recommendations/{productId}/dismiss` | 204 |
| DELETE | same path | 204 (undismiss) |

`stage` comes from the active Journey: trimester 1/2/3 → `MONTH_3`/`MONTH_6`/`MONTH_9`, and postpartum/childcare → `BABY_CARE`. With no journey,
`stage` is null and `items` is `[]`. `Recommendation = {productId, name, description, price, originalPrice, imageUrl, category, whyRecommended:[string], dismissed}`.
There's no `tier`, and SKIP-tier products are never returned. `whyRecommended` is non-clinical (e.g. "Within your budget of ₹1,000").
`clinicalRationale` is not exposed. Error: 404 `PRODUCT_NOT_FOUND`.

### 13.5 Data export + account deletion request (848ac87) — no consent required
| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/me/export` | JSON attachment `narturebelle-export-<yyyy-MM-dd>.json` with everything held for the user. Record files are not included, only their metadata |
| POST | `/api/v1/me/deletion-request` | `{confirm: "DELETE"}` → `DeletionRequest` |
| GET | `/api/v1/me/deletion-request` | `DeletionRequest`, or 404 `DELETION_REQUEST_NOT_FOUND` |
| DELETE | `/api/v1/me/deletion-request` | 204. Cancels only while `REQUESTED` |

`DeletionRequest = {id, status: REQUESTED|IN_PROGRESS|COMPLETED|REJECTED|CANCELLED, requestedAt, updatedAt}`. **Nothing is erased automatically.** An admin
processes requests by hand, because retention periods are still a product-owner decision. Errors: 400 `VALIDATION_FAILED` (confirm ≠ "DELETE"),
409 `DELETION_ALREADY_REQUESTED`, 409 `DELETION_NOT_CANCELLABLE` (already `IN_PROGRESS`), 404 `DELETION_REQUEST_NOT_FOUND`.

### 13.6 `RECORDS_STORAGE` consent (43c0d60) — changes §7 and §10
- `GET /api/v1/consent-documents/current` now returns a third document, `type: "RECORDS_STORAGE"` (version 1, DRAFT text). **Every document has
  `required`**: `TERMS`/`PRIVACY` are `true` and `RECORDS_STORAGE` is `false`. Only `required: true` documents belong on the sign-up consent step.
- Grant it with the usual `POST /api/v1/me/consents {documentType: "RECORDS_STORAGE", documentVersion: 1}`. It never appears in `/me` `pending` and never
  triggers `@RequireConsent`.
- **Enforced on `POST /records/upload-url` and `POST /records`:** without it, both return 403 `{code: "CONSENT_REQUIRED", pending: ["RECORDS_STORAGE"]}`.

### 13.7 Admin endpoints (all `*_ADMIN`)
| Method | Path | Notes |
|---|---|---|
| POST / PUT / DELETE | `/api/v1/admin/hospitals[/{id}]`, `/admin/products[/{id}]`, `/admin/checklists[/{id}]` | Bodies use the same fields as the public GET shapes. PUT is a full replacement. DELETE → 409 `CATALOG_ITEM_IN_USE` if referrals or dismissals point at the row. 404 `HOSPITAL_NOT_FOUND` / `PRODUCT_NOT_FOUND` / `CHECKLIST_TASK_NOT_FOUND` (05cffec) |
| GET, POST `/{id}/confirm` | `/api/v1/admin/hospital-referrals` | list and confirm referrals |
| POST, POST `/{id}/cancel` | `/api/v1/admin/joy-circle/events` | create (`{title, description, hostName, hostBio, category, startsAt, endsAt, modality, location, joinUrl, capacity}`) and cancel events |
| GET, PATCH `/{id}` | `/api/v1/admin/deletion-requests` | list; `{status}` moves a request on. Bad moves → `INVALID_DELETION_STATUS_TRANSITION` |

Required catalog fields: hospital `name`, `city`; product `name`, `price`, `stage`, `tier` (tier is used internally only, never shown
to users); checklist `stage`, `testName`. *(Corrected by the API agent.)*

Providers are **not** missing: create them with the Phase 3 endpoints under `/api/v1/admin/providers` (create → credentials → services →
verify → status `ACTIVE` → slots; see §9). There's no admin UI for any of these yet.

### 13.8 Not built in Phase 6
The AI record plan (needs clinical governance), regional languages and capacity forecasting.

## 14. Careers & ideas/feedback

**Public (no token):**
- `GET /api/v1/careers/jobs` → open roles: `[{id, title, team, location, employmentType: FULL_TIME|PART_TIME|CONTRACT|INTERNSHIP, workMode: ONSITE|REMOTE|HYBRID, summary, description (plain text, \n kept), requirements: [string], postedAt, status, closedAt}]`.
- `GET /api/v1/careers/jobs/{id}` → one role; a closed or missing role is `404 JOB_NOT_FOUND`.
- `POST /api/v1/careers/jobs/{id}/applications`, `multipart/form-data`: `fullName`* (≤120), `email`*, `phone`* (7–20 of `+0-9 ()-`), `location` (≤120), `linkedinUrl`/`portfolioUrl` (http(s), blank allowed), `yearsExperience` (0–50), `coverNote` (≤3000), `privacyConsent`* = `true`, `resume`* (PDF/DOC/DOCX, ≤4 MB, because Vercel caps request bodies at 4.5 MB; the file's bytes are checked, not just its name), `website` (honeypot, leave empty) → `201 {id, submittedAt}`.
  Errors: `400 VALIDATION_FAILED` + `fields` (a missing résumé is `fields.resume`), `400 RESUME_TYPE_NOT_ALLOWED`, `413 RESUME_TOO_LARGE`, `404 JOB_NOT_FOUND`, `409 JOB_CLOSED`, `429 TOO_MANY_REQUESTS` (10/hour per IP, 5/day per email).
- `POST /api/v1/feedback` JSON `{name?, email?, message* (≤3000), rating? (1–5), page? (≤300), website (honeypot)}` → `201 {id}`. The token is optional; when sent, the user id is stored. `429` after 10/hour per IP.
- A filled honeypot gets the normal `201` (with `id: 0`) and nothing is saved or emailed.

Every application (résumé attached, reply-to = the applicant) and every feedback message is emailed to `admin@narturebelle.com`, and the applicant gets a "we received your application" email. Until the email provider is configured, nothing is delivered — submissions are still saved and visible in the admin screens (`adminNotified: false`).

**Admin (any `*_ADMIN` role):**
- `GET /api/v1/admin/careers/jobs` → all roles incl. closed, each with `applicationCount`.
- `POST /api/v1/admin/careers/jobs` `{title*, team?, location*, employmentType*, workMode*, summary* (≤500), description* (≤10000), requirements: [string] (≤30)}` → the role, `OPEN`.
- `PUT /api/v1/admin/careers/jobs/{id}` (full replace, status unchanged), `POST …/{id}/close`, `POST …/{id}/reopen`.
- `GET /api/v1/admin/careers/jobs/{id}/applications` → `[{id, jobId, fullName, email, phone, location, linkedinUrl, portfolioUrl, yearsExperience, coverNote, resumeFileName, resumeSizeBytes, submittedAt, adminNotified}]`.
- `GET /api/v1/admin/careers/applications/{id}/resume` → the file itself (`Content-Disposition: attachment`; fetch with the admin token, save the blob). Every download is audited.

## 16. Referral links & the free Premium reward

Every member has a personal code. When **5 friends join through her link and buy a plan**, she gets a **free Premium plan**:
the full 9 months, with every session it includes. It's granted automatically, once per member. Only the member who
shared is rewarded; friends pay normally.

- **Share link:** the UI builds it from the code, e.g. `https://www.narturebelle.com/?ref=AB3K7Q9M`. Keep `ref` (in
  localStorage) through sign-up.
- `GET /api/v1/referrals/{code}` (public) → `{referrerName: "Asha R."}` for an "Asha R. invited you" banner. A bad link is `404 REFERRAL_CODE_NOT_FOUND`.
- `POST /api/v1/me/referral/claim {code}` → `{referrerName}`. Call it right after sign-in when a stored `ref` exists, then clear it.
  The code is case-insensitive. `409 REFERRAL_SELF` (her own code), `409 REFERRAL_ALREADY_CLAIMED`, `409 REFERRAL_NOT_NEW_CUSTOMER`
  (she has already bought a plan; only new customers count). Clear the stored `ref` on any 4xx too.
- `GET /api/v1/me/referral` → `{code, target: 5, invited, qualified, rewardGranted, rewardOrderId, friends: [{name, joinedAt, purchased}]}`.
  Show "qualified / 5". After the reward, the Premium plan appears in her orders and entitlements like any purchase,
  at price 0, and she gets an email.

A friend qualifies when her first real (price > 0) order becomes PAID. This is processed after the payment commits, so a
referral problem can never affect a payment. Signed-in endpoints need TERMS + PRIVACY consent.

## Quick product/content endpoints (no auth needed, safe to build against today)
`GET /api/v1/products`, `/api/v1/checklists`, `/api/v1/hospitals`, `/api/v1/doctor-sessions`, `/api/v1/nurse-companion/packages`, `/api/v1/features`, `/api/v1/consent-documents/current`, `/api/v1/plans`, `/api/v1/plans/{id}`, `/api/v1/providers`, `/api/v1/providers/{id}`, `/api/v1/providers/{id}/slots` — all public, all working, full details in Swagger.
