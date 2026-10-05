# Career API

Kotlin 2.1 / Ktor 3 / PostgreSQL / Flyway. From this directory run `gradle test` or `gradle run` with Java 21. Startup migrates the database; no mock auth or public sample credentials exist. For optional live PostgreSQL integration tests, set `TEST_DATABASE_URL` (JDBC), `TEST_DATABASE_USER`, and `TEST_DATABASE_PASSWORD` before `gradle test`; without them that test is skipped.

Required environment:

- `DATABASE_URL=jdbc:postgresql://localhost:5432/fsp_career`
- `DATABASE_USER=fsp_app`, `DATABASE_PASSWORD=...`
- `KEYCLOAK_ISSUER=http://localhost:8081/realms/fsp-career` (must match JWT `iss` exactly)
- `KEYCLOAK_AUDIENCE=fsp-api` (must be in JWT `aud`; role `candidate` or `employer` must be present in `realm_access.roles` or this client's `resource_access[KEYCLOAK_AUDIENCE].roles`)
- `PORT=8080` (optional)

Set Keycloak audience mapper for `fsp-api` on frontend client and assign roles in Keycloak. A Keycloak account is bound to one application role on first access; when both roles are present employer takes precedence. Candidate email is sourced only from the Keycloak `email` claim when `email_verified=true`, never trusted from request JSON. JWKS fetched from issuer `/protocol/openid-connect/certs` by default (or the private-network `KEYCLOAK_JWKS_URL` in Compose), with cached signing keys; signature, expiry, issuer and audience are verified by the Ktor JWT verifier.

GET `/openapi.yaml` serves the route specification. GET `/health` is the public liveness endpoint. Every `/api/*` route requires `Authorization: Bearer <access_token>`. CORS allows the local development origin `localhost:5173` and Compose origin `localhost:3000` (also `127.0.0.1` variants).

GET `/api/candidates/me/resume.pdf` exports an on-demand standardized branded PDF for the authenticated candidate (requires processing consent and full name). The supplied Cyrillic-capable JetBrains Mono font and violet brand accent are bundled for export; see `src/main/resources/fonts/JetBrainsMono-OFL.txt` for the font license.

Upload a text-based PDF via multipart field `file` (maximum 5 MB and 30 pages). Parsed resume fields are *draft suggestions only*: the candidate explicitly submits corrected fields with `draftId` to `/api/candidates/me/resume-confirm`; upload never changes profile/consent and confirmation never changes verified email or publication consent. Unconfirmed drafts expire after one day; files are not persisted. CV history and profile skills are candidate-provided claims, not verified achievements.

Processing consent is required before a profile can be stored or assessed. Publication consent is separate and revocable through PUT; DELETE `/api/candidates/me/consent` revokes processing and irreversibly erases the candidate's personal profile, attempts, drafts, achievements and invitations while leaving a blank account that can opt in again.

For `backend`, `frontend`, `data`, `product`, or `design` specializations an assessment randomly combines three subject-specific questions and two general questions; other specializations use five general questions. GET `/api/assessments/history` returns submitted results. Employer match search supports optional `specialization`, minimum `grade`, comma-separated `techStack` (all skills), and `verifiedAchievements=true`; self-reported skills filter but never boost score.

The assessment picks five random active questions and stores IDs server-side; the answer key is never returned to clients. Open attempts expire after 30 minutes. A completed attempt imposes a 90-day cooldown (across specializations); specialization-specific highest grade is retained if a later attempt scores lower. Scores use correct-answer percentage and grades junior (<60), middle (60–79), senior (>=80). The survey's `desiredGrade` is independent of verified assessment grade. Only candidates who opt into publication and have an assessed grade are discoverable. Synthetic achievements exist only in the verified server-side table, never accepted from user input; no unverified achievement can influence rankings. Initially no achievement is attributed to real users; trusted back-office inserts can populate it. Ranking is test score plus capped verified achievement points (20) and matching industry (5), with score components visible in `explanation`. Invited candidates' email and phone are disclosed to either party only after acceptance.
