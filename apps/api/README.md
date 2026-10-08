# Career API

Kotlin 2.1 / Ktor 3.2 with built-in DI / JetBrains Exposed DAO / PostgreSQL / Flyway. HikariCP pools connections; Flyway alone owns schema migrations. Exposed DAO persists aggregates and their children; SQL DSL handles batched read projections, aggregate queries and bulk erasure. From this directory run `./gradlew test` or `./gradlew run` with Java 21. Startup migrates the database; no mock auth or public sample credentials exist. For live PostgreSQL integration tests, set `TEST_DATABASE_URL` (JDBC URL), `TEST_DATABASE_USER`, and `TEST_DATABASE_PASSWORD` before `./gradlew test`; without them those tests are skipped.

## DDD organization

The API is a modular monolith. Code under `src/main/kotlin/ru/fsp/career` is grouped by bounded context, then by responsibility:

```text
ru/fsp/career/
├── Application.kt                  # composition root: server and Ktor plugins
├── Dependencies.kt                 # composition of all DI registration modules
├── PlatformDependencies.kt         # shared Database, AuthConfig and HttpClient factories
├── DemoSeed.kt                     # stable deployment CLI entry point
├── identity/                      # local/OAuth identities, sessions, one-time account role
├── profiles/                      # candidate/company profiles, needs, consent, privacy, resumes
├── assessment/                    # survey, adaptive attempts, grading and retake rules
├── matching/                      # candidate signals, relevance, explanations and candidate bank
├── recruitment/                   # invitations, vacancies, applications and weekly tasks
├── achievements/                  # FSP ownership, registry port and verified achievement import
└── shared/                        # shared domain vocabulary, HTTP errors, database connection
```

Each context has these packages:

| Package | Responsibility | Examples |
| --- | --- | --- |
| `domain` | Models, validation, business policies and repository ports; independent of HTTP and persistence | `ProfileRepository`, `AssessmentRepository`, `VacancyRepository`, `RetakePolicy`, `FspOwnership` |
| `application` | Use cases depending on ports and domain policies; query/document ports | `AuthService`, `ProfileService`, `AssessmentService`, `VacancyService`, `MatchingQueries`, `ResumeDocuments` |
| `infrastructure` | Exposed DAO entities and repository implementations, SQL read projections, credentials, JWT verification, PDF and registry adapters | `CandidateProfileEntity`, `AssessmentAttemptEntity`, `ExposedProfileRepository`, `ExposedVacancyRepository`, `MockFspRegistry` |
| `http` | Ktor routes, request parsing, response/status mapping and authentication plugin configuration | `BrowserAuth`, `ProfileRoutes`, `MatchingRoutes`, `TaskRoutes` |

`domain` depends only on Kotlin/JDK, serialization and other domain vocabulary. Serializable data classes preserve the existing JSON contract without duplicate transport models. `application` depends on repository/query/document/password ports, never on infrastructure or HTTP adapters. Repository implementations execute each persistence operation in one transaction, including account ownership checks and domain policies that require persisted state. DAO entities and `EntityID` stay inside infrastructure and never leave a transaction; callers receive domain values. PostgreSQL remains shared so row locks, consent erasure and cross-context operations stay atomic.

`Dependencies.kt` composes the DI registration modules. `PlatformDependencies.kt` registers lazy factories for the shared `Database`, `AuthConfig` and auth `HttpClient`. Each context registers its repository adapters and services in a root-level `*Dependencies.kt` file (`identity/IdentityDependencies.kt`, `profiles/ProfileDependencies.kt`, etc.), outside the domain/application/infrastructure packages. These are ordinary `Application` functions using Ktor's `ktor-server-di`; all modules share one application-scoped container. Constructor injection resolves ports; modules and HTTP routes obtain dependencies via `application.dependencies`. Ktor DI creates one instance per application and closes `AutoCloseable` resources, including the database pool and HTTP client, when the application stops. Tests register replacement providers before calling `careerModule`; fixture cleanup runs before application shutdown. Flyway keeps ownership of DDL; DAO mappings include existing UUID, string, generated long and composite primary keys without schema changes. Matching and list views use batched SQL projections rather than loading entity graphs.

`shared/domain` defines `DomainError` and semantic failure kinds; `shared/http` translates them into the existing API status codes and error JSON. `FspRegistry` is the existing port for the external registry; its synthetic adapter is in `achievements/infrastructure`. `assessment/validation` and test-side `matching/validation` retain their CLI entry points. Flyway migrations, table/column names, API paths and `ApplicationKt` / `DemoSeedKt` entry points are preserved.

Run `gradle test`: `ArchitectureTest` enforces the layer boundaries in addition to the domain and HTTP/database regression tests. With `TEST_DATABASE_URL` configured, the suite exercises both authentication modes, profile consent/privacy, assessment, matching, invitations, applications and weekly tasks against PostgreSQL.

## Configuration and API

Required environment:

- `DATABASE_URL=jdbc:postgresql://localhost:5432/fsp_career`
- `DATABASE_USER=fsp_app`, `DATABASE_PASSWORD=...`
- `KEYCLOAK_ISSUER=http://localhost:8081/realms/fsp-career` (must match JWT `iss` exactly)
- `KEYCLOAK_AUDIENCE=fsp-api` (must be in legacy bearer JWT `aud`)
- `PORT=8080` (optional)

The application role is chosen once through POST `/api/me/role` and stored in PostgreSQL. Existing bearer integrations retain the `fsp-api` audience mapper and recognition of admin-assigned employers. Candidate email is sourced only from a verified provider identity (`email_verified=true`), never trusted from request JSON. Legacy bearer JWKS is fetched from issuer `/protocol/openid-connect/certs` by default (or the private-network `KEYCLOAK_JWKS_URL` in Compose), with cached signing keys; signature, expiry, issuer and audience are verified by the Ktor JWT verifier.

GET `/openapi.yaml` serves the resource and browser-auth specification. GET `/health` is public. Existing `/api/*` resource routes accept the original Keycloak bearer token **or** a browser session; bearer JWT verification and account subjects remain unchanged. Browser cookie auth uses a server-side, seven-day PostgreSQL session (opaque SHA-256-hashed ID, HttpOnly, host-only, `/api`, SameSite=Lax, Secure in production). Browser requests that mutate `/api/*` require exact `Origin: FRONTEND_ORIGIN` and `X-CSRF-Token` from GET `/api/auth/session`; bearer-only requests without a session cookie retain their original behavior. CORS credentials are permitted only for `FRONTEND_ORIGIN` (default `http://localhost:5173`). Send `credentials: 'include'` from the browser.

Browser auth contract: GET `/api/auth/session` creates/refreshes an anonymous cookie and returns `{authenticated:boolean,email:string|null,csrfToken:string,providers:{fsp:boolean,vk:boolean}}`. POST `/api/auth/register` and `/api/auth/login` accept JSON `{email,password}`, require that cookie, Origin and CSRF header, rotate the cookie/CSRF and return the same shape (register 201, login 200). POST `/api/auth/logout` requires the same CSRF checks, revokes the old session and returns the anonymous session shape (200). Passwords must be 12–128 characters, emails at most 320; local email is **unverified** (no profile email/FSP registry linking until verified by a trusted provider). Password reset and email verification are not provided. Errors for unknown users and incorrect passwords are identical; login/register are rate-limited. Choose a role with POST `/api/me/role` after authentication. Neither matching emails nor choosing a role silently links accounts between providers.

Set `FRONTEND_ORIGIN` and `API_ORIGIN` to exact scheme/host/port origins (required HTTPS with `APP_ENV=production`); the latter constructs callbacks. Compose defaults to frontend `:3000`; a host-run API defaults to `:5173`. To enable FSP ID set `FSP_CLIENT_ID=fsp-service`, `FSP_CLIENT_SECRET` (Keycloak confidential client secret), `FSP_ISSUER` (public issuer, default `KEYCLOAK_ISSUER`), and optionally `FSP_AUTH_URL`, `FSP_TOKEN_URL`, `FSP_JWKS_URL` for custom endpoints (issuer must still match the signed RS256 ID token). Internal token/JWKS URLs may use HTTP on the private Compose network; the production browser authorization URL must use HTTPS. Register `${API_ORIGIN}/api/auth/fsp/callback` as redirect URI. Existing Keycloak accounts are recognized by matching the *verified issuer and subject* only, excluding synthetic demo subjects. To enable VK ID set `VK_CLIENT_ID` and `VK_CLIENT_SECRET` (VK confidential app **service token**) and register `${API_ORIGIN}/api/auth/vk/callback` with VK ID. Without both credentials each provider is disabled. Navigate the browser to GET `/api/auth/fsp/start` or `/api/auth/vk/start`; callbacks consume single-use browser-bound state and redirect to the configured `${FRONTEND_ORIGIN}/app` (or `?authError=failed`). Sign out before switching providers. Both flows use Ktor `Authentication.oauth`: `extraAuthParameters`, `accessTokenInterceptor` and a PostgreSQL-backed `NonceManager` add PKCE/nonce and VK's device_id/state/service_token exchange. VK user_info stable user_id is checked against the token response, not a VK email claim. No tokens or user-controlled redirect URLs are sent to the browser. Sessions, session/JWT authentication, rate limiting, CORS, forwarded headers, JSON negotiation and error responses also use existing Ktor plugins. Setup instructions: [root README](../../README.md#авторизация-и-подключение-фсп-id).

GET `/api/candidates/me/resume.pdf` exports an on-demand standardized branded PDF for the authenticated candidate (requires processing consent and full name). The supplied Cyrillic-capable JetBrains Mono font and violet brand accent are bundled for export; see `src/main/resources/fonts/JetBrainsMono-OFL.txt` for the font license.

Upload a text-based PDF via multipart field `file` (maximum 5 MB and 30 pages). Parsed resume fields are *draft suggestions only*: the candidate explicitly submits corrected fields with `draftId` to `/api/candidates/me/resume-confirm`; upload never changes profile/consent and confirmation never changes verified email or publication consent. Unconfirmed drafts expire after one day; files are not persisted. CV history and profile skills are candidate-provided claims, not verified achievements.

Processing consent is required before a profile can be stored or assessed. Publication consent is separate and revocable through PUT; DELETE `/api/candidates/me/consent` revokes processing and irreversibly erases the candidate's personal profile, attempts, drafts, achievements and invitations while leaving a blank account that can opt in again.

For `backend`, `frontend`, `data`, `product`, or `design` specializations an assessment randomly combines three subject-specific questions and two general questions; other specializations use five general questions. GET `/api/assessments/history` returns submitted results. Employer selection (`GET /api/matches`, need-based) and the candidate bank (`GET /api/bank/candidates`) support `specialization`, `grade`, `category`, comma-separated `techStack` (all skills), `verifiedAchievements`/`fsp`, `industry`, free-text `q` and `limit`; `GET /api/bank/candidates/{id}` is the privacy-filtered candidate card. Self-reported data never creates or raises a category.

The assessment picks five random active questions and stores IDs server-side; the answer key is never returned to clients. Open attempts expire after 30 minutes. A completed attempt imposes a 90-day cooldown (across specializations); specialization-specific highest grade is retained if a later attempt scores lower. Scores use correct-answer percentage and grades junior (<60), middle (60–79), senior (>=80). The survey's `desiredGrade` is independent of verified assessment grade. Only candidates who opt into publication and have an assessed grade are discoverable. Synthetic achievements exist only in the verified server-side table, never accepted from user input; no unverified achievement can influence rankings. Initially no achievement is attributed to real users; trusted back-office inserts can populate it. Ranking (package `matching/`, formula in `MatchingEngine.kt`): category gate by assessed specialization and grade (exact ×1.00, higher ×0.85/×0.70, lower only on request ×0.60), then test 30 + TF-IDF text 25 + stack 20 + FSP 15 + industry 5 + freshness 5; the bank orders by 65·test + 25·FSP + 10·freshness. Every result carries `factors[]` with Russian explanations. `gradle validateMatching` writes the synthetic offline evaluation to `docs/validation/`. Candidates link an FSP participant ID via `/api/candidates/me/fsp` (ownership = registry e-mail equals the verified Keycloak e-mail; current connector is a synthetic demo registry seeded in V4). Invited candidates' email and phone are disclosed to either party only after acceptance.
