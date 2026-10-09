# Homelab GitOps deployment

The active deployment is `https://fsp-career.homelab.lan` in the `fsp-career`
namespace of the single-node `homelab` K3s cluster. Envoy Gateway terminates
HTTPS using the existing homelab CA certificate; external-dns maintains the
OpenWrt DNS entry. Clients use the homelab CA and LAN/VPN DNS.

## Delivery

`.github/workflows/deploy.yml` runs on GitHub-hosted runners:

1. Pull requests and `main` run Bun tests, Svelte checks, ESLint, a frontend
   build, and Gradle tests with an isolated PostgreSQL 18 service. GitOps
   promotion tests also validate complete-release updates.
2. Successful `main` runs publish API, web and themed Keycloak images to GHCR.
   The browser API URL is empty at build time, so all requests use the same
   origin. Images are tagged with the full source commit and pinned by digest.
3. The promotion job commits all three image digests to
   `georgiy.gusev/homelab` on GitLab. `GITOPS_SSH_KEY` is a dedicated writable
   GitLab deploy key stored in this repository's Actions secrets. The GitLab
   SSH host key is pinned in the workflow.
4. Flux reconciles `fsp-career-data`, then `fsp-career-identity`, then
   `fsp-career`. The job reports promotion to Git, while Flux health checks
   determine whether the cluster rollout succeeds.

There are no cluster credentials in CI. `workflow_dispatch` republishes and
promotes `main`; concurrent releases are serialized. GitLab push conflicts are
retried against the latest GitOps revision, preserving unrelated changes.

## Runtime

- `/`: SvelteKit web.
- `/api/*`, `/openapi.yaml`, `/health`: Ktor API.
- `/auth/*`: Keycloak in production mode, with the bundled login theme.
- `/mail/`: ephemeral Mailpit inbox for homelab verification mail.

The API and Keycloak have separate databases/roles, `fsp_career` and
`fsp_career_keycloak`, in goodman's shared PostgreSQL at `172.29.240.10:5432`.
Application startup runs Flyway migrations; Keycloak owns its schema upgrades.
Passwords, the confidential OIDC client secret and the GHCR pull credential
are SOPS-encrypted in the homelab repository. Initial credentials are also
stored root-only in `/etc/homelab/fsp-career.json` on goodman. The deployment
starts with empty databases and does not seed demo accounts.

Keycloak imports the realm only when it is absent. Its homelab import sets
the public issuer/callbacks and reads `FSP_CLIENT_SECRET` from its environment.
For an existing realm, change clients/SMTP through Keycloak's admin API or
console; changing an import file does not overwrite existing users/settings.
Password reset is disabled while verification emails use the shared test
inbox. VK ID remains disabled until its credentials are configured.

## Operations and rollback

```sh
kubectl -n flux-system get kustomization fsp-career-data fsp-career-identity fsp-career
kubectl -n fsp-career get pods
kubectl -n fsp-career logs deployment/fsp-career-api
kubectl -n fsp-career logs deployment/fsp-career-keycloak
curl --cacert docs/certs/homelab-root-ca.crt https://fsp-career.homelab.lan/health
```

For rollback, revert the relevant `apps/fsp-career: promote ...` commit in
`homelab` and push to `main`. Flux restores all three pinned images. Database
migrations are not reverted by an image rollback; confirm schema compatibility
before reverting a release with migrations. Suspend `fsp-career` and
`fsp-career-identity` temporarily if a rollout needs investigation.

`infra/deploy/deploy.sh` and the Compose files remain available for manual
operation of the previous standalone server. GitHub Actions now delivers to
homelab through GitOps.
