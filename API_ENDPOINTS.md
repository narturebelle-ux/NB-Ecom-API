# API Endpoints & Access Levels

**Interactive docs:** run the app and open `http://localhost:<PORT>/swagger-ui/index.html` (publicly reachable, no token needed just to view it). Click **Authorize** and paste a Firebase ID token to test endpoints that require login directly from the UI. Raw OpenAPI JSON is at `/v3/api-docs`.

Access tiers, per the AUTH CONTRACT between UI and API:

- **Public** — no `Authorization` header needed.
- **Logged in** — valid `Authorization: Bearer <Firebase ID token>` required; user doesn't need to be verified.
- **Logged in + Verified** — valid token AND (`email_verified == true` OR the token carries a verified `phone_number`).
- **Admin** — valid token AND the caller's `role` (from our DB) is `ADMIN`.
- **Dev-only** — bean doesn't exist at all when `SPRING_PROFILES_ACTIVE` is `prod` or `stage`; available in `default`/local/`test`.

Errors are JSON `{"code": "...", "message": "..."}` — `401 UNAUTHENTICATED`, `403 EMAIL_NOT_VERIFIED`, `403 FORBIDDEN`.

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

## Reserved, not yet implemented
Per the contract, these access levels exist but the underlying features (cart/checkout/orders) are out of scope for this change:
- **Logged in**: cart.
- **Logged in + Verified**: checkout, place/view orders.

When these are built, reuse `AuthorizationService.requireSelfOrAdmin(...)` for per-resource ownership and `@RequireVerified` for the verified gate — both already exist (`com.narturebelle.security`) for this purpose.
