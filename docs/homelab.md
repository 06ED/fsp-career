# Развёртывание и GitOps

[← Документация](README.md) · [Манифесты](../deploy/homelab/README.md) ·
[CI/CD](../.github/workflows/deploy.yml)

Рабочее окружение — <https://brokenbrain.online>, namespace `fsp-career`
в K3s-кластере homelab. HTTPS обслуживает Envoy Gateway с сертификатом cert-manager.
GitHub Actions собирает релиз, Flux применяет manifests из этого репозитория.

## Топология

| Путь | Компонент |
| --- | --- |
| `/` | SvelteKit web |
| `/api/*`, `/health`, `/openapi.yaml` | Ktor API |
| `/auth/*` | Keycloak в production-режиме с темой ФСП |
| `/mail/` | Mailpit для демонстрационных verification-писем |

Web, API, Keycloak и Mailpit работают в Kubernetes. PostgreSQL находится на goodman;
API и Keycloak используют отдельные базы и роли `fsp_career` / `fsp_career_keycloak`.
Flyway обновляет схему приложения при старте, схемой Keycloak управляет сам Keycloak.

Публичная A-запись указывает на WAN homelab. external-dns поддерживает split-horizon
DNS на OpenWrt для LAN/VPN. Прежний `fsp-career.homelab.lan` перенаправляет на публичный origin.

## Путь релиза

```text
main → проверки → GHCR images → commit digests → Flux → Kubernetes rollout
```

1. **Проверки.** Web: Bun 1.4.2, Svelte/TypeScript, тесты, ESLint, build.
   API: JDK 21, Gradle 9.1.0, PostgreSQL 18 и unit/architecture/integration tests.
   GitOps: тесты promotion и валидация Kustomize-ресурсов.
2. **Публикация.** После успешных jobs `main` выпускает API, web и Keycloak
   в GHCR. Образы имеют SHA-тег и registry digest; frontend собирается с пустым API origin.
3. **Promotion.** Job одним коммитом обновляет digests трёх образов в
   `prod/app/kustomization.yaml` и `prod/identity/kustomization.yaml`.
   Релиз с устаревшим source пропускается, push-конфликты повторяются.
4. **Применение.** Flux последовательно сверяет data, identity, identity-config
   и app. Состояние workloads подтверждает фактический rollout.

CI не содержит cluster credentials. `workflow_dispatch` повторно публикует и продвигает
текущий `main`; релизы сериализованы. Коммиты только в `deploy/homelab` не запускают
новую image-сборку, а promotion через `GITHUB_TOKEN` не создаёт рекурсивный workflow.

## Ответственность проекта и платформы

Приложение владеет [namespace-scoped manifests](../deploy/homelab/prod):

| Набор | Содержание |
| --- | --- |
| `data` | SOPS-шифрованные Secrets API и Keycloak |
| `identity` | Keycloak, начальный realm, Mailpit |
| `identity-config` | Идемпотентная настройка callbacks существующего realm |
| `app` | Web/API Deployments, Services и HTTPRoutes |
| `runtime` | Общий security context, runtime account, priority и DNS |

Платформа `georgiy.gusev/homelab` владеет namespace, read-only Git source, Flux units,
RBAC, registry credentials, SOPS-ключом, квотами, admission/NetworkPolicy и Gateway.
Flux применяет проект как `flux-system/fsp-career-reconciler` с полномочиями
только внутри `fsp-career`.

Разрешённые ресурсы приложения: Deployments, ReplicaSets, Jobs, Pods, Services,
ConfigMaps, HTTPRoutes и runtime Secrets. Cluster-scoped ресурсы и platform RBAC
управляются платформой. Services используют ClusterIP.

## Runtime и конфигурация

- Workloads используют `fsp-career-runtime` без смонтированного API token.
- Runtime имеет restricted Pod Security и неперехватывающий PriorityClass.
- ResourceQuota/LimitRange ограничивают ресурсы namespace.
- NetworkPolicy разрешает трафик проекта, Envoy ingress, PostgreSQL, tenant DNS и публичный HTTPS.
- Tenant DNS сохраняет Kubernetes discovery и использует DNS-over-TLS для внешних имён.
- Registry и Flux source credentials предоставляются платформой.

Пароли и OAuth credentials зашифрованы в `prod/data/secrets` через SOPS/age.
Получатели заданы в [`.sops.yaml`](../.sops.yaml), ключ проекта хранится в
`flux-system/sops-age-fsp-career`. Root-only backup ключа и первоначальной
конфигурации находится на goodman в `/etc/homelab/`.

После изменения environment Secret обновляется restart annotation соответствующего
Deployment. Публичные SMTP-параметры API находятся в manifest; подробности —
[runtime-конфигурация](../deploy/homelab/README.md#подтверждение-email).

### Keycloak и OAuth

Realm импортируется, когда отсутствует. `identity-config` настраивает callbacks
в существующем realm, сохраняя пользователей и client secrets. Остальные изменения
провайдера выполняются через административный API/консоль Keycloak.

Callbacks:

```text
https://brokenbrain.online/api/auth/fsp/callback
https://brokenbrain.online/api/auth/vk/callback
```

Провайдер включается при наличии обоих credentials в API; состояние возвращается
в `GET /api/auth/session`. API и Keycloak отправляют verification-письма в Mailpit.
Локальный password reset не реализован; Keycloak reset отключён для общего демо-inbox.

## Проверка релиза

С настроенным доступом к кластеру:

```sh
kubectl -n flux-system get kustomization \
  fsp-career-data fsp-career-identity \
  fsp-career-identity-config fsp-career
kubectl -n fsp-career get pods
kubectl -n fsp-career rollout status deployment/fsp-career-api
kubectl -n fsp-career rollout status deployment/fsp-career-web
kubectl -n fsp-career logs deployment/fsp-career-api
curl --fail https://brokenbrain.online/health
```

Проверяют source/promotion SHA, image digests, observed revision Flux и pods.
`/health` проверяет процесс API; успешный promotion означает запись релиза в Git.

## Откат

Отмените нужный `deploy: promote ...` коммит и отправьте изменение в `main`:
Flux вернёт прежние digests. При откате images миграции PostgreSQL не отменяются,
поэтому версия приложения должна быть совместима с текущей схемой.

## Прежнее standalone-размещение

[compose.prod.yaml](../compose.prod.yaml) и
[infra/deploy/deploy.sh](../infra/deploy/deploy.sh) сохранены для ручного Compose/Traefik-размещения.
Активный workflow использует GHCR и homelab GitOps.
