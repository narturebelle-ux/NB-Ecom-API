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
No request body. This creates the user in our DB on first login, or updates email/phone/verification status on every later login. Response:
```json
{
  "id": 1,
  "firebaseUid": "uzVqamGnDuMKO19MzWXvE1JzDPx1",
  "email": "user@example.com",
  "emailVerified": false,
  "phone": null,
  "phoneVerified": false,
  "firstName": null,
  "lastName": null,
  "stage": null,
  "dueDate": null,
  "role": "USER"
}
```
`firstName`/`lastName`/`stage`/`dueDate` are null until the user fills out their profile via `PUT /users/me` — they're not derived from Firebase.

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

## Quick product/content endpoints (no auth needed, safe to build against today)
`GET /api/v1/products`, `/api/v1/checklists`, `/api/v1/hospitals`, `/api/v1/doctor-sessions`, `/api/v1/nurse-companion/packages`, `/api/v1/features` — all public, all working, full details in Swagger.
