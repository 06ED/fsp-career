# Локальная разработка и проверки

[← Документация](README.md) · [Авторизация](authentication.md) · [Развёртывание](homelab.md)

## Требования

| Режим | Инструменты |
| --- | --- |
| Полный стек | Docker Engine и Docker Compose v2 |
| Frontend на хосте | Bun 1.4.2 |
| API на хосте | JDK 21 и Gradle wrapper из `apps/api` |
| Сборка PDF | Typst 0.14.2+ |

Все команды Docker ниже выполняются из корня репозитория. Пароли и локальные
настройки хранятся в `.env`; образец — [`.env.example`](../.env.example).

## Полный стек в Docker

```sh
cp .env.example .env
# Укажите свои локальные пароли в .env.
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/health
```

Compose запускает web, API, PostgreSQL приложения, Keycloak, его отдельную PostgreSQL
и Mailpit. API при старте применяет Flyway-миграции; web запускается после готовности API.

| Сервис | Адрес |
| --- | --- |
| Web | <http://localhost:3000> |
| API | <http://localhost:8080> |
| OpenAPI | <http://localhost:8080/openapi.yaml> |
| Keycloak | <http://localhost:8081> |
| Mailpit | <http://localhost:8025> |
| PostgreSQL приложения | `localhost:5432` |

После регистрации по email откройте Mailpit и перейдите по ссылке подтверждения.
Для локального входа по email подключение ФСП ID или VK ID не требуется.

### Управление окружением

```sh
docker compose logs -f api web
docker compose up -d --build api web
docker compose down
```

Обычный `down` сохраняет именованные volumes. `docker compose down -v` удаляет
локальные базы вместе с аккаунтами и результатами. Keycloak импортирует realm только
при первом создании: существующий realm редактируется через его административный интерфейс.

## Frontend на хосте

Backend и инфраструктура могут работать в Compose, а Vite — на хосте с hot reload.
Для этого API должен разрешать origin `http://localhost:5173`:

```sh
FRONTEND_ORIGIN=http://localhost:5173 docker compose up -d --build api
```

Затем в каталоге `apps/web`:

```sh
bun install --frozen-lockfile
bun run dev
```

Откройте <http://localhost:5173>. По умолчанию frontend обращается к
`http://localhost:8080`. Для возврата к контейнерному web обновите API с обычным
`FRONTEND_ORIGIN` из `.env`:

```sh
docker compose up -d api web
```

`VITE_API_URL` — параметр сборки клиента. В production он пустой, поэтому запросы
используют origin сайта. Runtime-переменная не меняет уже собранный bundle.

## API на хосте

Сначала поднимите зависимости:

```sh
docker compose stop api web
docker compose up -d postgres keycloak-db mailpit keycloak
```

В каталоге `apps/api` экспортируйте параметры и запустите wrapper:

```sh
export DATABASE_URL=jdbc:postgresql://localhost:5432/fsp_career
export DATABASE_USER=fsp_app
export DATABASE_PASSWORD='<DATABASE_PASSWORD из .env>'
export KEYCLOAK_ISSUER=http://localhost:8081/realms/fsp-career
export KEYCLOAK_AUDIENCE=fsp-api
export FRONTEND_ORIGIN=http://localhost:5173
export API_ORIGIN=http://localhost:8080
./gradlew run
```

Значение `DATABASE_USER` должно совпадать с `.env`. Compose не экспортирует переменные
в оболочку host-процесса. Для host-run API нужен свободный порт 8080.

### SMTP для host-run API

Compose публикует UI Mailpit на 8025, а SMTP 1025 оставляет внутри своей сети.
Чтобы API на хосте тоже отправлял письма, добавьте локальный `compose.override.yaml`:

```yaml
services:
  mailpit:
    ports:
      - "127.0.0.1:1025:1025"
```

Примените `docker compose up -d mailpit`, затем перед запуском host API задайте:

```sh
export SMTP_HOST=localhost
export SMTP_PORT=1025
export SMTP_FROM=noreply@career.local
export SMTP_STARTTLS=false
```

Имя `mailpit` используется контейнерами, `localhost` — API на хосте.
Если host API уже запущен, перезапустите его из оболочки с этими переменными.

## Проверки

### Frontend

В каталоге `apps/web`:

```sh
bun install --frozen-lockfile
bun run check
bun test
bun run lint
VITE_API_URL= bun run build
```

`check` проверяет Svelte/TypeScript, `bun test` — регрессии, `lint` — ESLint.

### API

В каталоге `apps/api`:

```sh
./gradlew --no-daemon --console=plain test
```

Для PostgreSQL/HTTP-тестов нужна отдельная база. Например, в локальном Compose
создайте её командой из корня:

```sh
docker compose exec postgres sh -c 'createdb -U "$POSTGRES_USER" career_test'
```

Затем перед тестами задайте в `apps/api`:

```sh
export TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/career_test
export TEST_DATABASE_USER=fsp_app
export TEST_DATABASE_PASSWORD='<DATABASE_PASSWORD из .env>'
./gradlew --no-daemon --console=plain test
```

Без `TEST_DATABASE_URL` интеграционные тесты пропускаются. CI использует отдельный
PostgreSQL 18 service. Wrapper закреплён на Gradle 9.6.0; image и CI используют
Gradle 9.1.0 с JDK 21.

### GitOps и документация

Из корня, с Python 3/PyYAML и kubectl:

```sh
python3 -m unittest discover -s infra/deploy/tests -v
kubectl kustomize deploy/homelab/prod/app \
  | python3 infra/deploy/validate_manifests.py
```

Для PDF:

```sh
bash docs/documentation/build.sh
```

Полный набор CI-команд описан в [workflow](../.github/workflows/deploy.yml),
параметры сборки документа — в [руководстве Typst](documentation/README.md).
