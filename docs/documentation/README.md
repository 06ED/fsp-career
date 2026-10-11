# Техническая документация FSP Career

Готовый документ: [`../FSP-Career-documentation.pdf`](../FSP-Career-documentation.pdf).
Редактируемый исходник: [`project.typ`](project.typ).

Документ описывает текущий код приложения: архитектуру и слои, модель PostgreSQL,
авторизацию, профили и PDF-резюме, CAT/IRT-оценку и пересдачи, ранжирование, приглашения,
вакансии, задания недели, интеграции, API, конфигурацию, запуск, тестирование и GitOps.
Каталог методов и кодов ответов автоматически читается из
`apps/api/src/main/resources/openapi.yaml` при сборке.

## Сборка

Нужны Bash, Git и Typst **0.14.2+**. Статические Play и Montserrat берутся из
`apps/web/static/fonts`, DejaVu Sans Mono и математический шрифт входят в Typst.
Системные и variable-шрифты при сборке исключены для одинакового отображения.
В документе нет внешних пакетов Typst или загружаемых из сети изображений.

Из корня репозитория:

```sh
bash docs/documentation/build.sh
```

Для Nix:

```sh
nix shell nixpkgs#typst --command bash docs/documentation/build.sh
```

Результат записывается в `docs/FSP-Career-documentation.pdf`.
Сборку можно запускать из любого рабочего каталога. На титуле указываются дата UTC
и ревизия исходного кода. Для повторной сборки с одинаковыми значениями:

```sh
DOC_DATE=2026-10-11 DOC_REVISION=<source-sha> bash docs/documentation/build.sh
```

После изменения бизнес-правил обновите текст `project.typ` и пересоберите PDF.
OpenAPI является источником каталога API, а реализация Kotlin/TypeScript — источником
описания поведения. Подробности текущего окружения находятся в `docs/homelab.md`
и `deploy/homelab/README.md`.

## Проверка результата

```sh
bash -n docs/documentation/build.sh
pdfinfo docs/FSP-Career-documentation.pdf
pdffonts docs/FSP-Career-documentation.pdf
pdftotext docs/FSP-Career-documentation.pdf -
```

Последние три команды входят в Poppler. Проверьте титул, оглавление, схемы,
длинные URL/пути, таблицы и страницы каталога API; текст должен выделяться и
содержать читаемую кириллицу. Для просмотра отдельных страниц:

```sh
pdftoppm -f 1 -l 1 -scale-to 1600 -png -singlefile \
  docs/FSP-Career-documentation.pdf /tmp/fsp-career-doc-cover
```
