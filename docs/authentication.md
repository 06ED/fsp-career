# Авторизация и внешние провайдеры

[← Документация](README.md) · [Разработка](development.md) · [API](../apps/api/README.md)

## Вход по email

Страница входа — `/login`, регистрации — `/login?mode=register`.
Локальная регистрация принимает email до 320 символов и пароль от 12 до 128 символов.
Пароль хранится как PBKDF2-HMAC-SHA256 с индивидуальной солью.

1. Пользователь создаёт аккаунт и получает письмо подтверждения.
2. Открывает ссылку из письма и нажимает **«Подтвердить почту»**.
3. Выбирает роль специалиста или работодателя.

В демо письма API и Keycloak попадают в [Mailpit](https://brokenbrain.online/mail/),
локально — в <http://localhost:8025>. Текущий Mailpit не пересылает их во внешние ящики.
Ссылка действует 24 часа; новая отправка заменяет прежнюю, интервал — одна минута.

При сбое SMTP аккаунт может уже быть создан. Пользователь входит с паролем и нажимает
**«Отправить письмо ещё раз»**. Подтверждение ссылки в анонимном браузере само по себе
не авторизует этот браузер. Восстановление пароля локального аккаунта пока не реализовано.

## Сессия и прикладная роль

- Cookie `career_session`: HttpOnly, `/api`, SameSite=Lax, 7 дней; Secure в production.
- Сессии и одноразовые OAuth-транзакции хранятся в PostgreSQL.
- Изменяющий browser-запрос передаёт cookie, точный `Origin` и `X-CSRF-Token`.
- Роль выбирается один раз через `POST /api/me/role` и хранится в аккаунте.
- Неподтверждённый локальный email блокирует ресурсные маршруты кабинета.

Контактный email специалиста можно изменить в профиле. Он не меняет логин и статус
подтверждения email идентичности.

## ФСП ID через локальный Keycloak

В локальном стенде Keycloak выполняет роль OIDC-провайдера ФСП ID.
Конфиденциальный клиент `fsp-service` присутствует в [импортируемом realm](../infra/keycloak/fsp-career-realm.json).
Realm импортируется только в пустую базу Keycloak.

### Настройка клиента

Откройте <http://localhost:8081/admin/> и войдите с параметрами администратора из `.env`.
В realm `fsp-career` проверьте `Clients → fsp-service`:

| Параметр | Значение |
| --- | --- |
| Client type | OpenID Connect |
| Client authentication | On |
| Standard flow | On |
| Implicit flow / Direct access grants | Off |
| Redirect URI | `http://localhost:8080/api/auth/fsp/callback` |
| Web origins | Пусто: обмен выполняется сервером |
| PKCE | S256 |
| Scopes | `openid`, `email`; claims `sub`, `email`, `email_verified` |
| Подпись ID token | RS256 |

Если realm существовал до добавления клиента, создайте его с этими параметрами.
В `Credentials` скопируйте secret и задайте в `.env`:

```dotenv
FSP_CLIENT_ID=fsp-service
FSP_CLIENT_SECRET=<секрет клиента>
```

Из корня пересоздайте API:

```sh
docker compose up -d --build api
```

На странице входа станет активна кнопка **«ФСП ID»**.

### URL провайдера в Compose

| Переменная | Значение по умолчанию |
| --- | --- |
| `FSP_ISSUER` | `http://localhost:8081/realms/fsp-career` |
| `FSP_AUTH_URL` | `http://localhost:8081/realms/fsp-career/protocol/openid-connect/auth` |
| `FSP_TOKEN_URL` | `http://keycloak:8080/realms/fsp-career/protocol/openid-connect/token` |
| `FSP_JWKS_URL` | `http://keycloak:8080/realms/fsp-career/protocol/openid-connect/certs` |

Issuer остаётся публичным и проверяется по ID token. Token/JWKS могут загружаться
по внутреннему адресу контейнера. Для API на хосте используются доступные host URLs.

## Подключение внешнего ФСП ID

Зарегистрируйте серверное OIDC-приложение с Authorization Code, PKCE S256,
confidential client и callback `${API_ORIGIN}/api/auth/fsp/callback`.
Из discovery провайдера задайте в окружении API:

```dotenv
FSP_CLIENT_ID=<client_id>
FSP_CLIENT_SECRET=<client_secret>
FSP_ISSUER=<issuer>
FSP_AUTH_URL=<authorization_endpoint>
FSP_TOKEN_URL=<token_endpoint>
FSP_JWKS_URL=<jwks_uri>
```

В Compose при замене провайдера переопределяются все четыре URL-параметра.
API проверяет RS256-подпись, issuer, audience, срок, nonce и subject.
Email считается подтверждённым только при `email_verified=true`.
В production задаются явные HTTPS `FRONTEND_ORIGIN`, `API_ORIGIN`, issuer и auth URL.

Реестр достижений — отдельный адаптер `FspRegistry`. Его демонстрационные данные
импортируются при совпадении подтверждённой почты активной идентичности и участника.
Вход через провайдера не является автоматическим импортом достижений.

## VK ID

В кабинете VK ID создайте web-приложение. Базовый домен и доверенный Redirect URL
должны относиться к этому же приложению и точно совпадать с окружением:

```text
https://brokenbrain.online/api/auth/vk/callback
```

Задайте API:

```dotenv
VK_CLIENT_ID=<ID приложения>
VK_CLIENT_SECRET=<сервисный токен приложения>
```

Оба значения необходимы для включения кнопки. Для локального OAuth используйте
согласованный публичный HTTPS origin/туннель и обновите `API_ORIGIN` и callback.

Серверный обмен передаёт `device_id`, `state`, `code_verifier` и `service_token`.
Стабильный `user_id` сверяется между token response и `user_info`.
Текущий адаптер VK создаёт идентичность без email: почту для связи пользователь
сам указывает в профиле.

## Привязка к существующему аккаунту

В **«Мой профиль → Связанные аккаунты»** пользователь нажимает
**«Привязать ФСП ID»** или **«Привязать VK ID»**. Native POST-форма с `csrfToken`
запускает OAuth-подтверждение и связывает внешний ID с текущим аккаунтом.

Совпадение email не объединяет аккаунты. Идентичность, уже принадлежащая другому
аккаунту, не переносится. Legacy-аккаунты Keycloak сопоставляются по проверенным
issuer и subject. Привязка реестра и привязка входа — независимые действия.

## Диагностика

| Симптом | Что проверить |
| --- | --- |
| «Не подключён» | Наличие обоих credentials в runtime API, `session.providers` |
| Письмо не пришло | Mailpit, SMTP endpoint, результат `/api/auth/verification-email` |
| 403 на изменяющий запрос | Cookie, Origin, свежий CSRF, роль и подтверждение local email |
| OAuth callback завершился ошибкой | Redirect URI, issuer, audience, state и nonce |
| VK пишет `redirect_uri is missing or invalid` | Доверенный URI в том же приложении, чей ID задан API |
| Импорт ФСП отклонён | Подтверждённый email идентичности совпадает с записью реестра |

Состояние сессии — `GET /api/auth/session`. Полный контракт —
[OpenAPI](../apps/api/src/main/resources/openapi.yaml), детали реализации —
[техническая документация](FSP-Career-documentation.pdf).
