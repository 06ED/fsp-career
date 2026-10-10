#!/usr/bin/env bash
set -euo pipefail

kcadm() { /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/kcadm.config; }
trap 'rm -f /tmp/kcadm.config' EXIT

kcadm config credentials --server "$KEYCLOAK_URL" --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"

client_id() {
  kcadm get clients -r fsp-career -q "clientId=$1" --fields id --format csv --noquotes
}

web_id="$(client_id fsp-web)"
service_id="$(client_id fsp-service)"
test -n "$web_id"
test -n "$service_id"

kcadm update "clients/$web_id" -r fsp-career \
  -s "redirectUris=[\"$PUBLIC_ORIGIN/*\"]" -s "webOrigins=[\"$PUBLIC_ORIGIN\"]"
kcadm update "clients/$service_id" -r fsp-career \
  -s "redirectUris=[\"$PUBLIC_ORIGIN/api/auth/fsp/callback\"]" -s 'webOrigins=[]'

printf 'Keycloak client callbacks configured for %s\n' "$PUBLIC_ORIGIN"
