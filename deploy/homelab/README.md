# Homelab runtime configuration

Flux reconciles `prod/data`, `prod/identity`, `prod/identity-config` and
`prod/app` from this repository into the isolated `fsp-career` namespace.
See `docs/homelab.md` for the platform access contract and release process.

## Email verification

Native email/password registration requires email confirmation. The API uses
the namespace-local Mailpit SMTP service:

```dotenv
SMTP_HOST=fsp-career-mailpit
SMTP_PORT=1025
SMTP_FROM=no-reply@brokenbrain.online
SMTP_STARTTLS=false
```

Mailpit is unauthenticated inside the project namespace. NetworkPolicy allows
namespace-local SMTP; these public connection settings belong in the API
Deployment. Messages from both the API and Keycloak are captured in the test
inbox at `https://brokenbrain.online/mail/`. Mailpit does not forward messages
to external inboxes, and its message store is ephemeral.

After registration, open the confirmation link from Mailpit and press
**Подтвердить почту**. An existing unverified account can request a new message
with **Отправить письмо ещё раз**. Resends have a one-minute cooldown, and a new
link replaces the old one. Creating another account with the same email is not
necessary when the initial SMTP delivery failed.

A real external SMTP provider requires its corresponding host/TLS settings,
SOPS-encrypted credentials, and a platform-approved network egress rule for
the provider's SMTP port. Do not use the Compose-only `mailpit` hostname in
Kubernetes; the service here is named `fsp-career-mailpit`.
