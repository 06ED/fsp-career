# Homelab GitOps deployment

The active deployment is `https://brokenbrain.online` in the `fsp-career`
namespace of the single-node `homelab` K3s cluster. Envoy Gateway terminates
HTTPS using a cert-manager Let's Encrypt certificate. The public apex A record
points to the homelab WAN IP, and external-dns maintains OpenWrt split-horizon
DNS so LAN/VPN clients reach Envoy directly. The previous
`fsp-career.homelab.lan` address redirects to the public origin.

## Delivery

`.github/workflows/deploy.yml` runs on GitHub-hosted runners:

1. Pull requests and `main` run Bun tests, Svelte checks, ESLint, a frontend
   build, and Gradle tests with an isolated PostgreSQL 18 service. GitOps
   promotion tests also validate complete-release updates.
2. Successful `main` runs publish API, web and themed Keycloak images to GHCR.
   The browser API URL is empty at build time, so all requests use the same
   origin. Images are tagged with the full source commit and pinned by digest.
3. The promotion job commits all three image digests together to this
   repository's `deploy/homelab/prod/app/kustomization.yaml` and
   `deploy/homelab/prod/identity/kustomization.yaml`. It uses the job-scoped
   `GITHUB_TOKEN` with `contents: write`; CI has no access to the platform
   repository. Promotion skips a release if newer source changes are present.
4. Flux reconciles `fsp-career-data`, then `fsp-career-identity`,
   `fsp-career-identity-config`, and finally `fsp-career`. The job reports promotion to Git, while Flux health checks
   determine whether the cluster rollout succeeds.

There are no cluster credentials in CI. `workflow_dispatch` republishes and
promotes `main`; concurrent releases are serialized. Push conflicts are retried
against the latest project revision. Deployment-only commits do not trigger
another image build, and pushes made with `GITHUB_TOKEN` do not recursively run
Actions. Manifest changes on `main` are reconciled directly by Flux.

## Deployment ownership and access contract

All application manifests live under `deploy/homelab/prod`:

- `data`: the two SOPS-encrypted API/Keycloak environment Secrets.
- `identity`: Keycloak, its initial realm and Mailpit.
- `identity-config`: the idempotent callback configuration Job.
- `app`: web/API workloads, Services and HTTPRoutes.
- `runtime`: shared non-root security context, runtime account, priority and DNS.

The platform repository `georgiy.gusev/homelab` owns tenant onboarding under
`clusters/homelab/tenants/fsp-career`, including namespace, read-only Git source,
Flux units, restricted RBAC, registry credentials, SOPS decrypt key, quotas,
network/admission policies and the shared Gateway certificate/listener. Flux
applies this repository as `flux-system/fsp-career-reconciler`, whose workload
permissions are confined to namespace `fsp-career`. The controller account is
outside the application namespace and cannot be mounted into project pods.

Allowed workload resources are Deployments, ReplicaSets, Jobs, Pods, Services,
ConfigMaps, HTTPRoutes and the two runtime environment Secrets. Cluster resources,
RBAC, ServiceAccounts, Flux resources, NetworkPolicies and platform pull
credentials are controlled by the platform. Routes are limited to
`brokenbrain.online` and `fsp-career.homelab.lan`. Services use ClusterIP.

Workloads use the unprivileged `fsp-career-runtime` account without an API token,
the non-preempting `fsp-career` PriorityClass, and restricted Pod Security.
Project registry images and the Keycloak/Mailpit runtime images are permitted.
Platform pull credentials cannot be consumed as environment variables or volumes.
ResourceQuota and LimitRange bound the namespace's CPU, memory, storage and pod
count. NetworkPolicy permits namespace-local traffic, Envoy ingress, project
PostgreSQL, tenant DNS and public HTTPS; other private cluster/host destinations
are denied. Tenant DNS at `10.43.240.53` avoids the router's public fake-DNS
addresses by using DNS-over-TLS, while retaining Kubernetes service discovery.

## Runtime

- `/`: SvelteKit web.
- `/api/*`, `/openapi.yaml`, `/health`: Ktor API.
- `/auth/*`: Keycloak in production mode, with the bundled login theme.
- `/mail/`: ephemeral Mailpit inbox for homelab verification mail.

The API and Keycloak have separate databases/roles, `fsp_career` and
`fsp_career_keycloak`, in goodman's shared PostgreSQL at `172.29.240.10:5432`.
Application startup runs Flyway migrations; Keycloak owns its schema upgrades.
Runtime passwords and OAuth client secrets are SOPS-encrypted in this repository.
The GHCR pull credential and read-only Flux source key are platform-managed.
Initial runtime credentials are also
stored root-only in `/etc/homelab/fsp-career.json` on goodman. The deployment
starts with empty databases and does not seed demo accounts.

`.sops.yaml` contains the project age recipient and engineer recipient. The
project decrypt key is held in `flux-system/sops-age-fsp-career`, with a root-only
backup at `/etc/homelab/fsp-career.agekey`; it is separate from other tenants'
keys. Encrypt environment changes under `deploy/homelab/prod/data/secrets` and
change the API Deployment restart annotation when its environment changes.
VK ID is configured for application `54810600` with callback
`https://brokenbrain.online/api/auth/vk/callback`.

Keycloak imports the realm only when it is absent. Its import sets
the public issuer/callbacks and reads `FSP_CLIENT_SECRET` from its environment.
An idempotent GitOps Job configures the browser/server client callbacks in an
existing realm without replacing users or client secrets. Use the admin API or
console for other realm/SMTP changes; changing an import file does not overwrite
existing users/settings.
Password reset is disabled while verification emails use the shared test
inbox. VK ID remains disabled until its credentials are configured.

## Operations and rollback

```sh
kubectl -n flux-system get kustomization fsp-career-data fsp-career-identity fsp-career-identity-config fsp-career
kubectl -n fsp-career get pods
kubectl -n fsp-career logs deployment/fsp-career-api
kubectl -n fsp-career logs deployment/fsp-career-keycloak
curl https://brokenbrain.online/health
```

For rollback, revert the relevant `deploy: promote ...` commit in this
repository and push to `main`. Flux restores all three pinned images. Database
migrations are not reverted by an image rollback; confirm schema compatibility
before reverting a release with migrations. Suspend `fsp-career` and
`fsp-career-identity` temporarily if a rollout needs investigation.

`infra/deploy/deploy.sh` and the Compose files remain available for manual
operation of the previous standalone server. GitHub Actions now delivers to
homelab through GitOps.
