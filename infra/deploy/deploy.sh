#!/usr/bin/env bash
# Builds and (re)starts the production stack on this host from the current checkout.
# Legacy standalone deployment, run manually; GitHub Actions now promotes homelab GitOps images.
set -euo pipefail

cd "$(dirname "$0")/../.."

ENV_FILE="${ENV_FILE:-$HOME/.config/fsp-career/.env}"

compose() {
  docker compose --project-name fsp-career --env-file "$ENV_FILE" \
    -f compose.yaml -f compose.prod.yaml "$@"
}

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not reachable for $(id -un); install it and add the user to the docker group." >&2
  exit 1
fi

# First deploy: generate unique secrets once. They live only on the server, outside the runner workspace.
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Creating $ENV_FILE with generated passwords"
  mkdir -p "$(dirname "$ENV_FILE")"
  (
    umask 077
    cat >"$ENV_FILE" <<EOF
PUBLIC_HOST=brokenbrain.online
DATABASE_USER=fsp_app
DATABASE_PASSWORD=$(openssl rand -hex 24)
KEYCLOAK_DB_PASSWORD=$(openssl rand -hex 24)
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 16)
EOF
  )
fi

# Public demo logins for the jury (documented password); set DEMO_PASSWORD= (empty) to disable them.
grep -q '^DEMO_PASSWORD=' "$ENV_FILE" || echo "DEMO_PASSWORD=fsp-demo-2026" >>"$ENV_FILE"

env_value() { sed -n "s/^$1=//p" "$ENV_FILE"; }
PUBLIC_HOST="$(env_value PUBLIC_HOST)"

# Images are built before anything is replaced, so a failed build leaves the running version untouched.
# One at a time: parallel Gradle + Vite builds next to the running stack exhaust the server's ~4 GB RAM.
for service in keycloak api web; do
  compose build "$service"
done
compose up -d --remove-orphans --wait --wait-timeout 600

# The realm JSON is imported only into an empty Keycloak database and lists localhost URLs,
# so point the browser client at the public origin on every deploy.
# E-mail: with SMTP_HOST in the env file verification mail goes to that real server; otherwise it stays in
# the public Mailpit demo inbox (/mail/), and password reset is disabled there since anyone could read the link.
# shellcheck disable=SC2016 # expanded by the shell inside the container
compose exec -T -e PUBLIC_URL="https://$PUBLIC_HOST" \
  -e SMTP_HOST="$(env_value SMTP_HOST)" -e SMTP_PORT="$(env_value SMTP_PORT)" \
  -e SMTP_USER="$(env_value SMTP_USER)" -e SMTP_PASSWORD="$(env_value SMTP_PASSWORD)" \
  -e SMTP_FROM="$(env_value SMTP_FROM)" -e SMTP_STARTTLS="$(env_value SMTP_STARTTLS)" \
  keycloak bash -euo pipefail -c '
  kcadm() { /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/kcadm.config; }
  kcadm config credentials --server http://localhost:8080/auth --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"
  id=$(kcadm get clients -r fsp-career -q clientId=fsp-web --fields id --format csv --noquotes)
  kcadm update "clients/$id" -r fsp-career \
    -s "redirectUris=[\"$PUBLIC_URL/*\"]" -s "webOrigins=[\"$PUBLIC_URL\"]"
  id=$(kcadm get clients -r fsp-career -q clientId=fsp-service --fields id --format csv --noquotes)
  if [ -n "$id" ]; then
    kcadm update "clients/$id" -r fsp-career \
      -s "redirectUris=[\"$PUBLIC_URL/api/auth/fsp/callback\"]" -s "webOrigins=[]"
  fi
  # The imported realm is named for local development and has no locales; the public login page is Russian.
  kcadm update realms/fsp-career -s "displayName=ФСП Карьера" -s "displayNameHtml=ФСП Карьера" \
    -s internationalizationEnabled=true -s "supportedLocales=[\"ru\",\"en\"]" -s defaultLocale=ru
  if [ -n "$SMTP_HOST" ]; then
    starttls=${SMTP_STARTTLS:-false}
    ssl=true; [ "$starttls" = true ] && ssl=false
    kcadm update realms/fsp-career -s resetPasswordAllowed=true \
      -s "smtpServer.host=$SMTP_HOST" -s "smtpServer.port=${SMTP_PORT:-465}" \
      -s "smtpServer.from=${SMTP_FROM:-$SMTP_USER}" -s "smtpServer.fromDisplayName=ФСП Карьера" \
      -s smtpServer.auth=true -s "smtpServer.user=$SMTP_USER" -s "smtpServer.password=$SMTP_PASSWORD" \
      -s "smtpServer.ssl=$ssl" -s "smtpServer.starttls=$starttls"
  else
    kcadm update realms/fsp-career -s resetPasswordAllowed=false \
      -s smtpServer.host=mailpit -s smtpServer.port=1025 -s smtpServer.auth=false \
      -s smtpServer.ssl=false -s smtpServer.starttls=false
  fi
'

# Demo data: Keycloak logins (employer, an assessed candidate, a fresh candidate whose e-mail is in the synthetic
# FSP registry) plus an idempotent synthetic candidate bank written by DemoSeed through the API's own code.
DEMO_PASSWORD="$(env_value DEMO_PASSWORD)"
if [[ -n "$DEMO_PASSWORD" ]]; then
  # shellcheck disable=SC2016 # expanded by the shell inside the container
  demo_ids="$(compose exec -T -e DEMO_PASSWORD="$DEMO_PASSWORD" keycloak bash -euo pipefail -c '
    kcadm() { /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/kcadm.config; }
    kcadm config credentials --server http://localhost:8080/auth --realm master \
      --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1
    user_id() { kcadm get users -r fsp-career -q email="$1" -q exact=true --fields id --format csv --noquotes; }
    ensure_user() {
      [ -n "$(user_id "$1")" ] || kcadm create users -r fsp-career -s username="$1" -s email="$1" \
        -s firstName="$2" -s lastName="$3" -s enabled=true -s emailVerified=true >/dev/null
      kcadm set-password -r fsp-career --username "$1" --new-password "$DEMO_PASSWORD"
      user_id "$1"
    }
    echo "$(ensure_user employer@demo.fsp.local Демо Работодатель) $(ensure_user maria.volkova@demo.fsp.local Мария Волкова)"
    ensure_user ivan.petrov@demo.fsp.local Иван Петров >/dev/null
  ')"
  read -r demo_employer demo_assessed <<<"$demo_ids"
  compose exec -T -e DEMO_EMPLOYER_SUBJECT="$demo_employer" -e DEMO_ASSESSED_SUBJECT="$demo_assessed" api \
    java -Xmx256m -cp '/app/lib/*' ru.fsp.career.DemoSeedKt
fi

# The server disk is small: drop replaced images and build cache untouched for a week.
docker image prune -f >/dev/null
docker builder prune -f --filter until=168h >/dev/null
echo "Deployed https://$PUBLIC_HOST"
