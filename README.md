# FSP Career — local development

A prototype of a candidate/employer matching service. The planned flow is candidate registration with verified email, survey and test responses that produce a category, followed by employer ranked search and invitations; candidate contact details must remain hidden until an invitation has been accepted. This describes the product boundary, **not a claim that every flow is implemented or production-ready**. Consult the running API's OpenAPI document and application UI for the behavior currently available.

Any FSP achievements, category labels, scores, or candidate records shown as examples or demos are **synthetic prototype data**, not verified real-world FSP achievements or an endorsement of actual candidates. Never represent them as official credentials.

## Architecture

- `apps/web`: SvelteKit/TypeScript/StyleX/GSAP frontend built with Bun; Docker serves it with adapter-node at `http://localhost:3000` (optional dev origin `http://localhost:5173`). Browser login uses a public Keycloak client and authorization code + PKCE; never place a client secret in browser code. Buttons, cards, tags and fields share 8px corners; light actions gain a visible violet/rose tint on hover. Locally bundled Nunito (body) and Play (headings and labels) fonts come from the supplied archive with SIL OFL licenses in `apps/web/static/fonts/`. Moving Icons animate on parent button/link hover and keyboard focus, unless reduced motion is requested. Website action icons use MIT-licensed [Moving Icons](https://movingicons.dev/icons) (`@jis3r/icons`), with official [Lucide Svelte](https://lucide.dev/guide/packages/lucide-svelte) fallback where that collection lacks an icon; this does not affect the Keycloak theme.
- `apps/api`: Ktor API (typically `http://localhost:8080`) owns candidate/employer data, survey/test/category and invitation rules. JetBrains Exposed SQL DSL manages PostgreSQL access and transactions through HikariCP; Flyway owns schema migrations. **All authorization and contact gating must be enforced server-side**, not merely hidden in UI.
- `compose.yaml`: builds and runs **web + API + PostgreSQL + Keycloak + private Keycloak PostgreSQL + Mailpit**. Web `localhost:3000`, API `localhost:8080`, Keycloak `localhost:8081`, Mailpit `localhost:8025`, PostgreSQL `localhost:5432` are bound to loopback. Persistent named volumes survive ordinary `docker compose down`.
- `infra/keycloak/fsp-career-realm.json`: import of `fsp-career` realm, `candidate`/`employer` roles and public `fsp-web` client. New self-registrations receive `candidate` by default; only trusted administration should assign `employer`. Access tokens issued to the web client have the `fsp-api` audience. The bundled `infra/keycloak/themes/fsp-career` login theme gives registration and verification forms the same gently rounded controls and reduced-motion-aware interactions; existing local realms must select this theme in their realm settings after rebuilding the image.

## Start locally

**Entire stack with Docker:** Docker Engine + Compose v2 are the only host requirements. For local synthetic data, copy the template, change its example passwords, then build and start all six services:

```sh
cp .env.example .env
# Change the example passwords in .env before storing any sensitive data.
docker compose up --build -d
docker compose ps
```

Open the [platform](http://localhost:3000), [Keycloak admin console](http://localhost:8081/admin/), [development mail inbox](http://localhost:8025) and [OpenAPI document](http://localhost:8080/openapi.yaml). The API container verifies the browser token's external issuer `http://localhost:8081/realms/fsp-career` but obtains its JWKS via the private `http://keycloak:8080` network address (`KEYCLOAK_JWKS_URL`); issuer validation is **not** disabled. All host-exposed ports are loopback-only. The realm import applies only to an **empty Keycloak database**; edit a populated realm through the admin console instead of expecting re-import. `docker compose down` retains databases; `docker compose down -v` irreversibly destroys them.

**Optional host-run development:** install JDK 21, Gradle and Bun. Start the database, Keycloak and Mailpit with `docker compose up -d postgres keycloak-db mailpit keycloak`. Export the following in a shell (Compose does not export `.env` to host processes):

```sh
export DATABASE_URL='jdbc:postgresql://localhost:5432/fsp_career'
export DATABASE_USER='fsp_app'
export DATABASE_PASSWORD='<value of DATABASE_PASSWORD in .env>'
export KEYCLOAK_ISSUER='http://localhost:8081/realms/fsp-career'
export KEYCLOAK_AUDIENCE='fsp-api'
cd apps/api && gradle run
```

In another terminal run `cd apps/web && bun install --frozen-lockfile && bun run dev`, then open <http://localhost:5173>. There is no checked-in Gradle wrapper. Ports and Keycloak origins in the supplied realm target the documented defaults; changing them requires matching frontend build configuration, CORS, redirect URIs and JWT issuer values.

## Accounts and email verification

The realm allows self-registration, requires email verification, has reset-password and brute-force protection enabled, and includes **no preset demo users or real candidate data**. Development verification messages go to the local [Mailpit inbox](http://localhost:8025): use a synthetic email, register, then open its link in Mailpit. This proves only the local mail workflow, **not real mailbox ownership**. Replace Mailpit with a trusted SMTP provider before accepting real users. To test employer permissions, create a separate user in the Keycloak admin console and assign its `employer` realm role; never allow public registration to grant this role. Employer takes precedence over the default `candidate` role when both appear in a token.

## Security and privacy boundaries

This setup uses Keycloak `start-dev`, HTTP, example passwords, and local-only port publishing. It is **not** a production deployment: use TLS, production Keycloak mode, unique secrets, managed backups, network/firewall controls, a real SMTP provider, hardened CORS/redirects, audit logs, and reviewed authorization before using real identities. OAuth tokens authenticate users; the API must independently check JWT signature, issuer, audience, expiry and role, and enforce ownership/consent for every read and write. Survey answers, test responses, contact details and invitation history are sensitive personal data. Collect only what is necessary, minimize logging and retention, define deletion/export processes, and do not seed or publish real personal information in the repository. Ranked results must not leak hidden contact information through list/detail responses, errors, search filters or logs before acceptance.

## Validation and remaining checks

The six-service stack, Bun production build, Keycloak issuer (`localhost:8081`), API, Mailpit and browser-facing web endpoints were started and smoke-tested locally. With `TEST_DATABASE_URL` configured, **8 unit + 1 PostgreSQL migration/constraint/Exposed mapping tests passed**. The synthetic authenticated recruitment path (including reviewed PDF import/export and invitation privacy) also passed through real Keycloak-issued JWTs; a synthetic admin-created account received a Mailpit verification link and became `emailVerified=true` after its localhost confirmation action. To reproduce the local-only smoke runs after Compose is healthy:

```sh
export KEYCLOAK_ADMIN_PASSWORD='<value from .env>'
python3 docs/smoke_e2e.py
python3 docs/smoke_email.py
python3 docs/smoke_ranking.py
python3 docs/smoke_assessment.py
```

These scripts create temporary synthetic accounts and remove their Keycloak identities and exact API subjects; the end-to-end script uses a temporary password-grant **test-only** client and never changes the browser client's PKCE-only configuration. The ranking/retake matrices inject labeled synthetic assessment/achievement rows into the **local** database to make boundary checks deterministic; they do not establish scoring validity or official award verification. The recruitment smoke test admin-marks accounts verified; the separate email-action script confirms one synthetic, admin-created account via Mailpit while adapting Python's stricter Secure-cookie policy for HTTP localhost. Neither script proves interactive browser self-registration or ownership of a real mailbox. Full security, fairness, psychometric and employer-pair validation remains outstanding.

Further validation plan:

1. Run `docker compose --env-file .env.example config --quiet` and validate the realm JSON syntax; then `docker compose up -d` and check `docker compose ps` plus Keycloak discovery/issuer.
2. Register a synthetic candidate; follow the verification link in Mailpit; confirm email must be verified before login and that the token includes `candidate` and `fsp-api` audience. Assign `employer` manually to a separate synthetic account; confirm candidates cannot gain employer access.
3. Run API tests and compare its OpenAPI document with actual endpoints. Check unauthorized/wrong-audience/expired tokens and object-ownership failures, survey and test/category behavior, ranked employer results, invitation transitions, and the rule that contact details remain absent until accepted. Treat missing endpoints as outstanding work, not passing behavior.
4. Run frontend checks/build; manually exercise both roles and verification/invitation paths against the running API. Clear test data after use; `docker compose down -v` irreversibly removes both local databases.

See the reproducible [validation protocol](docs/validation.md) and [synthetic demonstration script](docs/demo.md). Remaining official-release dependencies are detailed in [external inputs](docs/external-dependencies.md): FSP ID binding, issuer-controlled achievement integration, calibrated assessment items and a measured expert validation study. Branded PDF résumé export exists; do not treat a self-reported CV or synthetic achievement as an official FSP credential. PDF résumé import uses text extraction into a review-only draft; scanned-image OCR is not implemented. Vacancies and candidate self-applications are optional enhancements. The observed local synthetic flow is not a production deployment, real-mail ownership test, comprehensive security audit, calibrated assessment or measured ranking-quality study.
