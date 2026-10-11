# FSP Career · Web

[← О проекте](../../README.md) · [Документация](../../docs/README.md) ·
[Разработка](../../docs/development.md) · [API](../api/README.md)

**SvelteKit-интерфейс** кабинетов специалиста и работодателя.
Стек: Svelte 5, SvelteKit 2, TypeScript, Vite, Bun 1.4.2 и adapter-node.

## Запуск

В этом каталоге:

```sh
bun install --frozen-lockfile
bun run dev
```

Dev-сервер — <http://localhost:5173>. API по умолчанию — `http://localhost:8080`.
Backend должен разрешать frontend origin; настройка описана в
[руководстве по разработке](../../docs/development.md#frontend-на-хосте).
Полный контейнерный web доступен на <http://localhost:3000>.

## Структура

```text
src/routes/              маршруты и layout SvelteKit
src/lib/
  pages/                 композиция страниц и состояние dashboard
  widgets/               крупные блоки интерфейса
  features/              пользовательские действия и сценарии
  entities/              предметные типы, справочники и API-клиенты
  shared/                авторизация, HTTP, UI и стили
static/                  изображения и локальные шрифты
test/                    Bun regression tests
```

Состояние dashboard передаётся через контекст Svelte. Тестирование, фильтры
подбора, вакансии и задания хранят локальное состояние в своих features.

## Маршруты

| Маршрут | Назначение |
| --- | --- |
| `/` | Лендинг |
| `/login` | Вход, регистрация и подтверждение email |
| `/app/role` | Выбор роли |
| `/app` | Обзор |
| `/app/profile` | Профиль, PDF-резюме, приватность и интеграции специалиста |
| `/app/assessment` | Опрос, тест, история и план заданий по вакансии |
| `/app/company`, `/app/needs` | Компания и потребность |
| `/app/matches` | Подборка и банк кандидатов |
| `/app/invitations` | Приглашения и их статусы |
| `/app/vacancies` | Вакансии специалиста или управление вакансиями компании |
| `/app/applications` | Отклики специалиста |
| `/app/tasks` | Задание недели или задания работодателя |

Layout кабинета использует `ssr=false` и проверяет сессию, подтверждение почты,
роль и допустимость раздела. Сервер API независимо проверяет доступ к ресурсам.

## Взаимодействие с API

`src/lib/shared/auth/index.ts` хранит состояние сессии и отправляет запросы
с `credentials: "include"`. Изменяющие запросы получают `X-CSRF-Token`;
FormData передаётся с браузерным multipart Content-Type. Ошибка 401 сбрасывает
локальную авторизацию.

`VITE_API_URL` подставляется в bundle при сборке:

- `http://localhost:8080` — локальный API по умолчанию;
- пустая строка — same-origin запросы в production.

Для production-сборки:

```sh
VITE_API_URL= bun run build
```

Docker запускает результат adapter-node через Bun на порту 3000.
Переменные `ORIGIN`, `HOST`, `PORT` задают runtime-конфигурацию web-сервера.

## Интерфейс и резюме

- Native CSS, GSAP и поддержка reduced motion.
- Иконки Moving Icons с Lucide fallback.
- Локальные Nunito и Play; лицензии лежат в `static/fonts`.
- pdfmake и Montserrat для экспорта PDF-резюме в браузере.
- API/PDFBox для импорта PDF в проверяемый черновик.

## Проверки

```sh
bun run check
bun test
bun run lint
bun run build
```

Форматирование: `bun run format`, проверка форматирования: `bun run format:check`.
Пакеты и версии закреплены в [package.json](package.json) и [bun.lock](bun.lock).
