# Описание API {#api}

Полная спецификация OpenAPI 3.0 с запросами, ответами, схемами и кодами ошибок: `apps/api/src/main/resources/openapi.yaml`. Развёрнутый экземпляр отдаёт её по адресу <https://brokenbrain.online/openapi.yaml>; её можно открыть в Swagger UI или Postman. Все маршруты `/api/*` требуют токен доступа Keycloak (`Authorization: Bearer …`) с `iss` реалма fsp-career и `aud=fsp-api`. Ролевые маршруты проверяют роль, выбранную через `POST /api/me/role`. Ошибки возвращаются в едином формате `{"error": "…"}` с кодами 400 (валидация), 401 (нет или неверный токен), 403 (не та роль или нет согласия), 404 (нет записи или чужая запись), 409 (недопустимый переход статуса, пересдача раньше срока, конфликт привязки), 500.

## Служебные

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `GET` | `/health` | Public process liveness check |  |
| `GET` | `/openapi.yaml` | Public OpenAPI 3 specification |  |

## Аккаунт и роль

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `GET` | `/api/me` | Current user and the application role bound to the account (null before the first choice) | 401 |
| `POST` | `/api/me/role` | Choose the account role once (candidate or employer); repeating the same choice is idempotent | 400, 401, 409 |

## Профиль кандидата, резюме, приватность, ФСП

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `GET` | `/api/candidates/me` | Read own candidate profile (candidate role) | 401, 403 |
| `PUT` | `/api/candidates/me` | Update own profile with processingConsent true; publication consent is independent | 400, 401, 403 |
| `DELETE` | `/api/candidates/me/consent` | Revoke processing and erase own profile, assessment answers, drafts, achievements and invitations | 401, 403 |
| `GET` | `/api/candidates/me/fsp` | Own FSP ID link status and imported results (candidate role) | 401, 403 |
| `POST` | `/api/candidates/me/fsp` | Link (or re-sync) an FSP participant ID and import its results as verified achievements (candidate role) | 400, 401, 403, 404, 409 |
| `DELETE` | `/api/candidates/me/fsp` | Unlink the FSP ID and delete imported FSP achievements (candidate role) | 401, 403 |
| `GET` | `/api/candidates/me/privacy` | Read own privacy settings (candidate role) | 401, 403 |
| `PUT` | `/api/candidates/me/privacy` | Replace own privacy settings (candidate role, processing consent required) | 400, 401, 403 |
| `POST` | `/api/candidates/me/resume-confirm` | Explicitly review/edit draft fields and save profile; verified email and publication consent stay unchanged | 400, 401, 403, 404 |
| `POST` | `/api/candidates/me/resume-upload` | Extract review-only suggestions from text-based PDF (candidate role, processing consent required) | 400, 401, 403 |

Скачиваемое PDF-резюме формируется в браузере из сохранённого профиля, с Montserrat и цветами текущей темы. Сервер обрабатывает загрузку PDF и подтверждение распознанных данных.

## Тестирование

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `POST` | `/api/assessments/attempts` | Start an adaptive test for the declared grade (candidate role, processing consent required) | 400, 401, 403, 404, 409 |
| `GET` | `/api/assessments/attempts/current` | Open attempt with its current item, if any (candidate role) | 401, 403 |
| `POST` | `/api/assessments/attempts/{id}/answers` | Answer the current item; returns the next item or the final result (candidate role) | 400, 401, 403, 404, 409 |
| `POST` | `/api/assessments/blueprint` | Show how a vacancy becomes a test (any authenticated user) | 400, 401, 404 |
| `GET` | `/api/assessments/catalog` | Survey reference data, grades with cut-points, topics and demo vacancies (any authenticated user) | 401 |
| `GET` | `/api/assessments/eligibility` | Which grades the candidate may be tested for now (candidate role) | 400, 401, 403 |
| `GET` | `/api/assessments/history` | Own completed attempts with survey, target grade, θ, SE, score and confirmed grade, newest first (candidate role) | 401, 403 |

## Работодатель: компания и потребность

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `GET` | `/api/employers/company` | Read own employer company (employer role) | 401, 403 |
| `PUT` | `/api/employers/company` | Update own employer company (employer role) | 400, 401, 403 |
| `GET` | `/api/employers/needs` | Read own hiring needs (employer role) | 401, 403 |
| `PUT` | `/api/employers/needs` | Update own hiring needs (employer role) | 400, 401, 403 |

## Подбор и банк кандидатов

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `GET` | `/api/bank/candidates` | Search the whole candidate bank by category, ordered by confirmed-profile strength (employer role) | 400, 401, 403 |
| `GET` | `/api/bank/candidates/{id}` | Employer-facing candidate card respecting the candidate's privacy settings (employer role) | 400, 401, 403, 404 |
| `GET` | `/api/matches` | Need-based selection ranked by relevance, with explanations and recommended categories (employer role) | 400, 401, 403 |

## Приглашения

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `GET` | `/api/invitations` | List own role's invitations (candidate or employer) | 401, 403 |
| `POST` | `/api/invitations` | Send invitation to published candidate with mandatory RUB salary range and a contact method (employer role) | 400, 401, 403, 404 |
| `POST` | `/api/invitations/{id}/accept` | Invited candidate accepts sent/viewed invitation; contact disclosure begins | 400, 401, 403, 404, 409 |
| `POST` | `/api/invitations/{id}/reject` | Invited candidate rejects sent/viewed invitation | 400, 401, 403, 404, 409 |
| `POST` | `/api/invitations/{id}/view` | Invited candidate marks sent invitation viewed | 400, 401, 403, 404, 409 |

## Вакансии и отклики

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `GET` | `/api/candidates/me/applications` | Own applications with current status and vacancy conditions, newest first (candidate role) | 401, 403 |
| `DELETE` | `/api/candidates/me/applications/{id}` | Withdraw own sent/viewed application (candidate role); the employer loses access to it and to the contacts | 400, 401, 403, 404, 409 |
| `PUT` | `/api/employers/applications/{id}/status` | Change status of an application to own vacancy (employer role) | 400, 401, 403, 404, 409 |
| `GET` | `/api/employers/vacancies` | List own vacancies with application counters, newest first (employer role) | 401, 403 |
| `POST` | `/api/employers/vacancies` | Publish a vacancy with a mandatory RUB salary range (employer role) | 400, 401, 403 |
| `PUT` | `/api/employers/vacancies/{id}` | Edit own vacancy (employer role); status is unchanged | 400, 401, 403, 404 |
| `GET` | `/api/employers/vacancies/{id}/applications` | Incoming applications to own vacancy with category, score, fit and contacts (employer role) | 400, 401, 403, 404 |
| `POST` | `/api/employers/vacancies/{id}/close` | Close own open vacancy (employer role); it disappears from the candidate list, applications stay | 400, 401, 403, 404, 409 |
| `POST` | `/api/employers/vacancies/{id}/reopen` | Reopen own closed vacancy (employer role) | 400, 401, 403, 404, 409 |
| `GET` | `/api/vacancies` | Browse open vacancies with company and salary range (candidate role) | 400, 401, 403 |
| `POST` | `/api/vacancies/{id}/applications` | Apply to an open vacancy (candidate role); discloses own contacts to that employer | 400, 401, 403, 404, 409 |

## Регулярные задания

| Метод | Путь | Назначение | Ошибки |
|---|---|---|---|
| `GET` | `/api/candidates/me/task-submissions` | Own short-task answers with employer ratings, newest first (candidate role) | 401, 403 |
| `POST` | `/api/candidates/me/task-submissions` | Answer this week's task with a solution or a proposed approach (candidate role) | 400, 401, 403, 409 |
| `GET` | `/api/candidates/me/tasks/current` | This week's short task (candidate role; processing consent and specialization required) | 400, 401, 403 |
| `PUT` | `/api/employers/task-submissions/{id}/rating` | Rate an answer to own task 1–5 with an optional comment; re-rating overwrites (employer role) | 400, 401, 403, 404 |
| `GET` | `/api/employers/tasks` | List own short tasks with answer counters (employer role) | 401, 403 |
| `POST` | `/api/employers/tasks` | Publish a short task for a specialization, optionally for one grade (employer role) | 400, 401, 403 |
| `POST` | `/api/employers/tasks/{id}/activate` | Return own task to rotation (employer role) | 400, 401, 403, 404, 409 |
| `POST` | `/api/employers/tasks/{id}/deactivate` | Remove own task from rotation (employer role); answers stay, open weekly offers are re-picked | 400, 401, 403, 404, 409 |
| `GET` | `/api/employers/tasks/{id}/submissions` | Answers to own task with candidate name and confirmed category, never contacts (employer role) | 400, 401, 403, 404 |
