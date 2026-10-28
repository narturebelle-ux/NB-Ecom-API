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
Default port **8085** (`.env`'s `PORT` value). Health check: `GET http://localhost:8085/health/db` → `{"database":"ok"}`.

**CORS:** `cors.allowed-origins` includes `http://localhost:*` (any local dev port) alongside the real domains — verified live with a preflight request from `http://localhost:4200` (Angular's default dev port): got back `Access-Control-Allow-Origin: http://localhost:4200`, `Access-Control-Allow-Credentials: true`. No action needed on the UI side; any localhost port works.

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
| `/api/v1/admin/**` | Admin | reserved namespace — no concrete endpoints implemented yet |

## 6. Known gaps — don't build UI assuming these exist

- **No cart/checkout/order endpoints exist at all.** The contract reserves "Logged in" for cart and "Logged in + Verified" for checkout/orders, but none of that is implemented yet — this is a products/content + lead-capture API today, not a functioning checkout flow.
- **No admin endpoints exist** — `/api/v1/admin/**` is reserved and role-gated, but nothing is mapped under it yet.
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

## Quick product/content endpoints (no auth needed, safe to build against today)
`GET /api/v1/products`, `/api/v1/checklists`, `/api/v1/hospitals`, `/api/v1/doctor-sessions`, `/api/v1/nurse-companion/packages`, `/api/v1/features`, `/api/v1/consent-documents/current` — all public, all working, full details in Swagger.
