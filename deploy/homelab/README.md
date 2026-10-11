# Production-манифесты FSP Career

[← О проекте](../../README.md) · [Развёртывание](../../docs/homelab.md) ·
[Авторизация](../../docs/authentication.md)

Kustomize-конфигурация приложения для namespace `fsp-career`.
Flux читает эти файлы напрямую из репозитория и применяет их в порядке зависимостей.

## Структура

| Каталог | Назначение |
| --- | --- |
| [`prod/data`](prod/data) | Зашифрованные runtime Secrets |
| [`prod/identity`](prod/identity) | Keycloak, realm и Mailpit |
| [`prod/identity-config`](prod/identity-config) | Job настройки OAuth callbacks |
| [`prod/app`](prod/app) | API/web, Services и HTTPRoutes |
| [`prod/runtime`](prod/runtime) | Общий security context и параметры workloads |

Все application-ресурсы ограничены namespace. Namespace/RBAC, NetworkPolicy,
Flux controllers, registry access и общие Gateway-ресурсы управляются платформой.

## Версии образов

CI/CD публикует API, web и Keycloak в GHCR и одним promotion-коммитом закрепляет
их digests в `prod/app/kustomization.yaml` и `prod/identity/kustomization.yaml`.
Ручное обновление release должно сохранять согласованность всех трёх образов.

Описание pipeline, доступов и отката: [развёртывание](../../docs/homelab.md).

## Проверка manifests

Из корня репозитория, с kubectl, Python 3 и PyYAML:

```sh
kubectl kustomize deploy/homelab/prod/app \
  | python3 infra/deploy/validate_manifests.py
```

Аналогично проверяются `prod/data`, `prod/identity` и `prod/identity-config`.
CI выполняет проверки всех наборов до публикации images.

## Подтверждение email

Локальная регистрация требует подтверждения почты. API использует namespace-local SMTP:

```dotenv
SMTP_HOST=fsp-career-mailpit
SMTP_PORT=1025
SMTP_FROM=no-reply@brokenbrain.online
SMTP_STARTTLS=false
```

Mailpit не требует авторизации внутри namespace; NetworkPolicy разрешает этот SMTP-трафик.
Публичные параметры подключения заданы в [API Deployment](prod/app/api-deployment.yaml).
Credentials БД и OAuth находятся в SOPS-шифрованных Secrets.

Письма API и Keycloak доступны в <https://brokenbrain.online/mail/>.
Mailpit не пересылает их во внешние ящики; хранилище сообщений эфемерно.

После регистрации пользователь открывает ссылку и нажимает **«Подтвердить почту»**.
Повторная отправка доступна через **«Отправить письмо ещё раз»** раз в минуту;
новая ссылка заменяет старую. При первоначальном сбое доставки можно войти
в созданный аккаунт и запросить письмо заново.

Для внешнего SMTP задаются host/TLS-настройки, шифрованные credentials и соответствующий
платформенный egress. Compose-имя `mailpit` отличается от Kubernetes-сервиса
`fsp-career-mailpit`.
