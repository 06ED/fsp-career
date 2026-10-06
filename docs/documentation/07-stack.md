# Библиотеки и компоненты {#stack}

Всё ПО открытое: лицензии MIT, Apache-2.0, BSD, PostgreSQL License. Код решения распространяется под лицензией MIT (`LICENSE`).

## Инфраструктура

| Компонент | Версия | Лицензия | Назначение |
|---|---|---|---|
| Docker Engine / Compose | 29.x / v2.38+ | Apache-2.0 | контейнеризация, оркестрация стека |
| Traefik | 3.7.13 | MIT | обратный прокси, HTTPS (Let's Encrypt), маршрутизация по путям |
| PostgreSQL | 16 (alpine) | PostgreSQL | данные платформы и отдельная БД Keycloak |
| Keycloak | 26.0.8 | Apache-2.0 | аутентификация, регистрация с подтверждением e-mail, совместимость с ФСП ID |
| Mailpit | 1.31.4 | MIT | демо-почта для писем подтверждения |
| GitHub Actions (self-hosted runner) | 2.337 | MIT | CI/CD: деплой при каждом push в `main` |

## Backend (`apps/api`)

| Библиотека | Версия | Назначение |
|---|---|---|
| Kotlin (JVM) | 2.1.20 | язык, JDK 21 (Eclipse Temurin) |
| Ktor Server (core, netty, auth, auth-jwt, content-negotiation, status-pages, cors) | 3.1.2 | HTTP API |
| kotlinx.serialization | (Kotlin 2.1.20) | JSON |
| JetBrains Exposed (core, jdbc, java-time, json) | 0.61.0 | SQL DSL, транзакции |
| Flyway | 11.3.4 | миграции схемы |
| HikariCP | 6.2.1 | пул соединений |
| PostgreSQL JDBC | 42.7.5 | драйвер БД |
| Auth0 java-jwt / jwks-rsa (через ktor-auth-jwt) | — | проверка подписи JWT по JWKS |
| Apache PDFBox | 3.0.4 | разбор PDF-резюме и генерация PDF-профиля |
| Logback | 1.5.18 | логирование |
| Gradle | 9.1 | сборка |

## Frontend (`apps/web`)

| Библиотека | Версия | Назначение |
|---|---|---|
| SvelteKit | 2.70.3 | веб-приложение, SSR, adapter-node 5.5.7 |
| Svelte | 5.57.1 | UI (runes) |
| Vite | 7.3.6 | сборка |
| TypeScript | 5.9.3 | типизация |
| StyleX (+ unplugin) | 0.19.1 | атомарные стили |
| keycloak-js | 26.2.4 | вход по OIDC Authorization Code + PKCE |
| GSAP | 3.15.0 | анимации лендинга |
| @jis3r/icons (Moving Icons), @lucide/svelte | 2.9.0, 1.52.0 | иконки |
| Bun | 1.4.2 | пакетный менеджер и рантайм |
| svelte-check, Prettier | 4.7.6, 3.9.9 | проверки и форматирование |

## Шрифты и бренд

Шрифты Montserrat, Nunito и Play распространяются под лицензией SIL OFL и подключены локально. Знак и цвета взяты из брендбука ФСП.
