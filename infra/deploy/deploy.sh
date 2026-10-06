#!/usr/bin/env bash
# Builds and (re)starts the production stack on this host from the current checkout.
# The self-hosted GitHub Actions runner calls it on every push to main; it also works by hand.
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

PUBLIC_HOST="$(sed -n 's/^PUBLIC_HOST=//p' "$ENV_FILE")"

# Images are built before anything is replaced, so a failed build leaves the running version untouched.
compose build
compose up -d --remove-orphans --wait --wait-timeout 600
compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile

# The realm JSON is imported only into an empty Keycloak database and lists localhost URLs,
# so point the browser client at the public origin on every deploy.
# shellcheck disable=SC2016 # expanded by the shell inside the container
compose exec -T -e PUBLIC_URL="https://$PUBLIC_HOST" keycloak bash -euo pipefail -c '
  kcadm() { /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/kcadm.config; }
  kcadm config credentials --server http://localhost:8080/auth --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"
  id=$(kcadm get clients -r fsp-career -q clientId=fsp-web --fields id --format csv --noquotes)
  kcadm update "clients/$id" -r fsp-career \
    -s "redirectUris=[\"$PUBLIC_URL/*\"]" -s "webOrigins=[\"$PUBLIC_URL\"]"
'

docker image prune -f >/dev/null
echo "Deployed https://$PUBLIC_HOST"
