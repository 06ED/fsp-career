# FSP Career — local development

> **Документация (RU, PDF):** [`docs/FSP-Career-documentation.pdf`](docs/FSP-Career-documentation.pdf) — архитектура, механики тестирования и подбора, валидация с результатами, интеграция с ФСП, API, развёртывание. Исходники: `docs/documentation/` (`build.sh`). Демо: <https://brokenbrain.online> (логины в разделе «Обзор решения»).

> **Скринкасты проекта:** [посмотреть на Яндекс Диске](https://disk.yandex.ru/d/8Kw-km6dKgXJEQ).

A prototype of a candidate/employer matching service. The planned flow is candidate registration with verified email, survey and test responses that produce a category, followed by employer ranked search and invitations; candidate contact details must remain hidden until an invitation has been accepted. This describes the product boundary, **not a claim that every flow is implemented or production-ready**. Consult the running API's OpenAPI document and application UI for the behavior currently available.

Any FSP achievements, category labels, scores, or candidate records shown as examples or demos are **synthetic prototype data**, not verified real-world FSP achievements or an endorsement of actual candidates. Never represent them as official credentials.

## Architecture

- `apps/web`: SvelteKit/TypeScript/native CSS/GSAP frontend built with Bun; Docker serves it with adapter-node at `http://localhost:3000` (optional dev origin `http://localhost:5173`). Browser login uses our email/password forms or optional FSP ID / VK ID buttons, then an HttpOnly API session cookie. OAuth secrets and tokens stay on the server. Buttons, cards, tags and fields share 8px corners; light actions gain a visible violet/rose tint on hover. Locally bundled Nunito (body) and Play (headings and labels) fonts come from the supplied archive with SIL OFL licenses in `apps/web/static/fonts/`. Moving Icons animate on parent button/link hover and keyboard focus, unless reduced motion is requested. Website action icons use MIT-licensed [Moving Icons](https://movingicons.dev/icons) (`@jis3r/icons`), with official [Lucide Svelte](https://lucide.dev/guide/packages/lucide-svelte) fallback where that collection lacks an icon; this does not affect the Keycloak theme.
- `apps/api`: Ktor API (typically `http://localhost:8080`) is a DDD modular monolith with `identity`, `profiles`, `assessment`, `matching`, `recruitment` and `achievements` contexts. Each separates `domain`, `application`, `infrastructure` and `http`; see [API architecture](apps/api/README.md#ddd-organization). Ktor DI connects use cases to repository ports; JetBrains Exposed DAO persists aggregates, with SQL DSL for batched read projections and bulk operations. HikariCP pools PostgreSQL connections; Flyway owns schema migrations. **All authorization and contact gating must be enforced server-side**, not merely hidden in UI.
- `compose.yaml`: builds and runs **web + API + PostgreSQL + Keycloak + private Keycloak PostgreSQL + Mailpit**. Web `localhost:3000`, API `localhost:8080`, Keycloak `localhost:8081`, Mailpit `localhost:8025`, PostgreSQL `localhost:5432` are bound to loopback. Persistent named volumes survive ordinary `docker compose down`.
- `infra/keycloak/fsp-career-realm.json`: our Keycloak temporarily acts as FSP ID. The confidential `fsp-service` client serves server-side OIDC login; `fsp-web` and the `fsp-api` audience remain for existing bearer API integrations. The candidate/employer role is chosen once in the application and stored in PostgreSQL. The bundled `infra/keycloak/themes/fsp-career` login theme is used on the provider's login/registration pages.

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
export FRONTEND_ORIGIN='http://localhost:5173'
export API_ORIGIN='http://localhost:8080'
cd apps/api && gradle run
```

In another terminal run `cd apps/web && bun install --frozen-lockfile && bun run dev`, then open <http://localhost:5173>. There is no checked-in Gradle wrapper. Ports and Keycloak origins in the supplied realm target the documented defaults; changing them requires matching frontend build configuration, CORS, redirect URIs and JWT issuer values.

## Server deployment

Every source push to `main` runs GitHub-hosted CI checks, publishes web/API/Keycloak images to GHCR, and updates their digests under `deploy/homelab/` in this repository. Flux reads these manifests directly and deploys to the isolated `fsp-career` namespace at `https://brokenbrain.online`. PostgreSQL stays on `goodman`, while web, API, Keycloak and Mailpit run in Kubernetes behind Envoy Gateway. Namespace RBAC, admission rules, Pod Security, quotas and network policies are owned by the homelab platform repository. See [homelab delivery and operations](docs/homelab.md) for the deployment contract, secrets and rollback.

The previous standalone deployment is still available manually with `bash infra/deploy/deploy.sh`. That script builds the images, then starts `compose.yaml` + `compose.prod.yaml` as the `fsp-career` project. Traefik (configured by labels in `compose.prod.yaml`) obtains Let's Encrypt HTTPS for `PUBLIC_HOST` (`brokenbrain.online`) and serves the web app at `/`, the API at `/api` (OpenAPI at `/openapi.yaml`) and Keycloak at `/auth`. Because the realm import only applies to an empty database, the script also sets the `fsp-web` client's redirect URIs and web origins on each manual deploy.

Secrets are generated on the first deploy into `~/.config/fsp-career/.env` on the server (never in the repository); the Keycloak admin console is `https://brokenbrain.online/auth/admin/` with `KEYCLOAK_ADMIN_USER`/`KEYCLOAK_ADMIN_PASSWORD` from that file. Mailpit stays loopback-only there: open it with `ssh -L 8025:127.0.0.1:8025 moverq@51.250.19.214` and <http://localhost:8025>. Run `bash infra/deploy/deploy.sh` from a checkout on the server to deploy by hand.

## Авторизация и подключение ФСП ID

Собственная форма входа доступна в `/login`, регистрация — в `/login?mode=register`. Это отдельная страница без сайдбара кабинета; `/app` перенаправляет туда неавторизованного пользователя. Кнопки ФСП ID и VK ID видны в обоих режимах: без credentials показывают «Не подключён» и недоступны, после настройки API становятся активными. Пароли хранятся как PBKDF2-SHA256 с индивидуальной солью; сессии — в PostgreSQL, cookie живёт семь дней. Реализация использует готовые плагины Ktor: `Authentication` (`session`, `oauth`, `jwt`), `Sessions`, `RateLimit`, `CORS`, `XForwardedHeaders`, `ContentNegotiation` и `StatusPages`. OAuth-плагин выполняет перенаправление и обмен кода для обоих провайдеров; его настройки добавляют PKCE и параметры VK ID. Одноразовый `state` привязан к cookie, а для OIDC дополнительно проверяется `nonce` и подпись ID token.

После первого входа пользователь один раз выбирает роль. Вход по почте, ФСП ID и VK ID по умолчанию создаёт отдельные учётные записи: совпадение почты не объединяет их. В профиле доступны кнопки «Привязать ФСП ID» и «Привязать VK ID»: native POST-форма с CSRF-токеном запускает OAuth-подтверждение и добавляет внешний ID к текущему аккаунту. Привязка к чужому аккаунту отклоняется; после подтверждения пользователь возвращается в свой профиль, а оба способа входа ведут в тот же аккаунт. Не настроенные провайдеры недоступны. Существующие аккаунты Keycloak сохраняются при совпадении проверенных `issuer` + `sub`. Локальная почта считается неподтверждённой: она отображается как логин, но не используется как подтверждённый контакт или доказательство владения участником ФСП. Подтверждение локальной почты и сброс локального пароля пока не реализованы.

Поле «Электронная почта для связи» в профиле специалиста редактируется отдельно от учётной записи. Оно предзаполняется почтой аккаунта, если она доступна, и позволяет указать другой контактный адрес, в том числе после входа через VK ID без почты. API проверяет формат и длину адреса; его сохранение не меняет логин, статус подтверждения почты или привязку ФСП. Импорт PDF не заменяет контактный email.

### 1. Настроить наш Keycloak как ФСП ID

1. Запустите стек и откройте [Keycloak Admin Console](http://localhost:8081/admin/). Войдите с `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` из `.env`, выберите realm `fsp-career`.
2. В **Clients → fsp-service** проверьте настройки. Клиент уже включён в импорт для новой БД. В существующем realm создайте его вручную через **Create client**:
   - **Client type:** `OpenID Connect`, **Client ID:** `fsp-service`.
   - **Client authentication:** `On` (confidential client).
   - **Standard flow:** `On`; **Implicit flow**, **Direct access grants**, **Service accounts roles:** `Off`.
   - **Valid redirect URIs:** строго `http://localhost:8080/api/auth/fsp/callback`, без `*`.
   - **Web origins:** пусто — обмен кода выполняет сервер.
   - В расширенных настройках **Proof Key for Code Exchange Code Challenge Method:** `S256`.
   - В **Client scopes** подключите стандартные `basic`, `profile`, `email`; ID token должен содержать `sub`, `email`, `email_verified`. Алгоритм подписи ID token — `RS256`.
3. На вкладке **Credentials** скопируйте **Client secret** в `.env`:

   ```dotenv
   FSP_CLIENT_ID=fsp-service
   FSP_CLIENT_SECRET=<секрет клиента из Keycloak>
   FRONTEND_ORIGIN=http://localhost:3000
   API_ORIGIN=http://localhost:8080
   ```

4. Пересоздайте API: `docker compose up -d --build api web`. Compose подставит следующие адреса:

   | Параметр | Локальное значение |
   | --- | --- |
   | `FSP_ISSUER` | `http://localhost:8081/realms/fsp-career` |
   | `FSP_AUTH_URL` | `http://localhost:8081/realms/fsp-career/protocol/openid-connect/auth` |
   | `FSP_TOKEN_URL` | `http://keycloak:8080/realms/fsp-career/protocol/openid-connect/token` |
   | `FSP_JWKS_URL` | `http://keycloak:8080/realms/fsp-career/protocol/openid-connect/certs` |

   Браузер использует публичный authorization endpoint, API — внутренние token/JWKS endpoints. Проверка `iss` всегда использует публичный issuer, независимо от адреса получения ключей. При запуске API на хосте внутренние URL не задавайте: по умолчанию все endpoints вычисляются из `FSP_ISSUER` (или `KEYCLOAK_ISSUER`). Экспортируйте секрет и `FRONTEND_ORIGIN=http://localhost:5173` в окружение процесса; `.env` автоматически читает только Compose.

5. Откройте `/login`: кнопка **«ФСП ID»** станет активной. API перенаправит в Keycloak, примет callback и вернёт в кабинет. Keycloak требует подтверждения своей почты; локальные письма приходят в [Mailpit](http://localhost:8025). Регистрация по кнопке в нашем сервисе создаёт локальный аккаунт; регистрация на странице Keycloak создаёт аккаунт провайдера.

### 2. Настроить серверное подключение

Для `https://<PUBLIC_HOST>` задайте у клиента **Valid redirect URIs**: `https://<PUBLIC_HOST>/api/auth/fsp/callback`. В серверном `~/.config/fsp-career/.env` добавьте `FSP_CLIENT_ID` и `FSP_CLIENT_SECRET`; затем запустите `bash infra/deploy/deploy.sh`. Скрипт актуализирует callback импортированного `fsp-service`; для существующего realm сначала создайте клиент по инструкции выше.

`compose.prod.yaml` задаёт `APP_ENV=production`, `FRONTEND_ORIGIN=https://<PUBLIC_HOST>`, `API_ORIGIN=https://<PUBLIC_HOST>`, issuer `https://<PUBLIC_HOST>/auth/realms/fsp-career` и публичный HTTPS authorization endpoint. Token/JWKS идут по приватной Compose-сети с `/auth` в пути. Production-cookie имеет `Secure`; прокси сохраняет `Origin`, нужный API для CORS/CSRF. API должен оставаться за Traefik: `XForwardedHeaders` использует последний адрес из установленного им `X-Forwarded-For` для ограничения попыток входа.

### 3. Заменить Keycloak настоящим ФСП ID

При получении доступа к ФСП ID зарегистрируйте **серверное OIDC-приложение** у провайдера с Authorization Code, PKCE `S256`, scopes `openid email`, confidential client и тем же callback `https://<PUBLIC_HOST>/api/auth/fsp/callback`. Из его `/.well-known/openid-configuration` возьмите реальные значения:

```dotenv
FSP_CLIENT_ID=<выданный client_id>
FSP_CLIENT_SECRET=<выданный client_secret>
FSP_ISSUER=<issuer из discovery>
FSP_AUTH_URL=<authorization_endpoint из discovery>
FSP_TOKEN_URL=<token_endpoint из discovery>
FSP_JWKS_URL=<jwks_uri из discovery>
```

Для Compose переопределите **все четыре URL-параметра**: иначе внутренние endpoints останутся адресами нашего Keycloak. Провайдер должен возвращать подписанный `RS256` ID token с `iss`, `aud`, `sub`, `exp`, переданным `nonce`; подтверждение почты принимается только при `email_verified=true`. Внешние production endpoints указывайте через HTTPS. Пересоздайте API — менять код фронтенда или callback не требуется. Конкретные официальные URL и credentials ФСП ID должны быть получены у оператора; текущий Keycloak является его заменой для разработки. Новый issuer создаёт отдельную идентичность, а не переносит старые аккаунты по почте. Подключение входа не подключает реестр достижений — у него отдельный API/коннектор.

### 4. Подключить VK ID

В кабинете VK ID создайте веб-приложение для серверного Authorization Code + PKCE flow. Для production укажите **базовый домен** `<PUBLIC_HOST>` (например, `brokenbrain.online`) и **доверенный Redirect URL** строго `https://<PUBLIC_HOST>/api/auth/vk/callback` — без завершающего `/`. Эти значения должны быть сохранены в том же приложении, чей ID используется в `VK_CLIENT_ID`. Для конфиденциального приложения добавьте публичный IP сервера API в список разрешённых IP. Для локального тестирования используйте публичный HTTPS-адрес сервиса/туннеля и согласуйте его с `API_ORIGIN` и callback в кабинете VK; HTTP localhost на порту 8080 кабинет может не принять. Укажите в окружении API:

```dotenv
VK_CLIENT_ID=<ID приложения VK ID>
VK_CLIENT_SECRET=<сервисный токен приложения>
```

После пересоздания API и обновления `/login` кнопка **«VK ID»** станет активной. Ktor OAuth использует `https://id.vk.ru/authorize` и `https://id.vk.ru/oauth2/auth`; настройки обмена передают `device_id`, `state`, `code_verifier` и `service_token`. Профиль запрашивается через `oauth2/user_info`, стабильный `user_id` сверяется с token response. Почта VK не считается подтверждённой почтой ФСП. Провайдер отключён, если не заданы оба его параметра; локальный вход работает независимо от них.

### Проверить подключение

- `GET /api/auth/session` возвращает `providers.fsp` / `providers.vk` и CSRF-токен. Если кнопка показывает «Не подключён», проверьте credentials **в окружении API**, а не только в `.env`.
- При ошибке входа проверьте точное совпадение callback, публичного issuer и клиента (`aud` ID token); frontend получает `/app?authError=failed`.
- Если на странице VK появляется «Ошибка загрузки», а VK возвращает `invalid_request` / `redirect_uri is missing or invalid`, проверьте базовый домен и доверенный Redirect URL в кабинете VK ID: он должен точно совпасть с `${API_ORIGIN}/api/auth/vk/callback`. Эта ошибка возникает до обмена кода; смена сервисного ключа её не исправляет.
- POST/PUT/DELETE из браузера отправляют cookie (`credentials: 'include'`), `Origin` и `X-CSRF-Token`; после входа/выхода cookie и CSRF-токен меняются. Секреты провайдеров не должны попадать в переменные `VITE_*`.
- `gradle test` в `apps/api`; с `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD` дополнительно запускаются PostgreSQL HTTP-проверки регистрации/входа/выхода, OAuth обоих провайдеров, защиты от повторного callback и ошибочного code exchange. OAuth-провайдеры в автоматических проверках имитируются; реальный вход проверяется с выданными credentials. В `apps/web`: `bun test && bun run check && bun run build`.

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
2. Register a synthetic local account, choose its role once, save its profile and sign out/sign in again. Confirm its email remains unverified and choosing a different role returns a conflict. Enable FSP ID as described above, register a separate provider account and confirm its Keycloak email via Mailpit; test provider login and existing-account access. Test VK with issued app credentials.
3. Run API tests and compare its OpenAPI document with actual endpoints. Check unauthorized/wrong-audience/expired tokens and object-ownership failures, survey and test/category behavior, ranked employer results, invitation transitions, and the rule that contact details remain absent until accepted. Treat missing endpoints as outstanding work, not passing behavior.
4. Run `cd apps/web && bun test && bun run check && bun run build`; manually exercise both roles and verification/invitation paths against the running API. Clear test data after use; `docker compose down -v` irreversibly removes both local databases.

See the reproducible [validation protocol](docs/validation.md) and [synthetic demonstration script](docs/demo.md). Remaining official-release dependencies are detailed in [external inputs](docs/external-dependencies.md): FSP ID binding, issuer-controlled achievement integration, calibrated assessment items and a measured expert validation study. Branded PDF résumé export exists; do not treat a self-reported CV or synthetic achievement as an official FSP credential. PDF résumé import uses text extraction into a review-only draft; scanned-image OCR is not implemented. Vacancies and candidate self-applications are optional enhancements. The observed local synthetic flow is not a production deployment, real-mail ownership test, comprehensive security audit, calibrated assessment or measured ranking-quality study.
