"""End-to-end check of a deployed stand through the public demo logins (no admin access needed).

Logs in exactly like the browser does — Keycloak login form + Authorization Code with PKCE for the public
`fsp-web` client — and walks the main scenario: the employer's need-based selection with explanations,
the assessed candidate's incoming invitation, and contact disclosure only after acceptance.

    python3 docs/smoke_demo.py --base https://brokenbrain.online --password fsp-demo-2026 [--accept]

Without --accept the script is read-only. With --accept it accepts the demo candidate's pending invitation.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import html
import http.cookiejar
import json
import re
import secrets
import sys
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: D401 - keep the 302 visible
        return None


def login(base: str, email: str, password: str) -> str:
    """Returns an access token for the fsp-web client, obtained through the real login form."""
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect)
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    redirect = base + "/app"
    realm = base + "/auth/realms/fsp-career/protocol/openid-connect"
    query = urllib.parse.urlencode({
        "client_id": "fsp-web", "redirect_uri": redirect, "response_type": "code", "scope": "openid",
        "state": secrets.token_hex(8), "code_challenge": challenge, "code_challenge_method": "S256",
    })
    page = opener.open(f"{realm}/auth?{query}").read().decode()
    action = html.unescape(re.search(r'<form[^>]+id="kc-form-login"[^>]+action="([^"]+)"', page).group(1))
    form = urllib.parse.urlencode({"username": email, "password": password, "credentialId": ""}).encode()
    try:
        opener.open(action, data=form)
        raise SystemExit(f"login for {email} did not redirect (wrong password?)")
    except urllib.error.HTTPError as error:
        if error.code not in (302, 303):
            raise
        location = error.headers["Location"]
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)["code"][0]
    token_form = urllib.parse.urlencode({
        "grant_type": "authorization_code", "client_id": "fsp-web", "code": code,
        "redirect_uri": redirect, "code_verifier": verifier,
    }).encode()
    with urllib.request.urlopen(f"{realm}/token", data=token_form) as response:
        return json.load(response)["access_token"]


def api(base: str, token: str, path: str, method: str = "GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(base + path, data=data, method=method, headers={
        "Authorization": "Bearer " + token, **({"Content-Type": "application/json"} if data else {}),
    })
    with urllib.request.urlopen(request) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="https://brokenbrain.online")
    parser.add_argument("--password", required=True)
    parser.add_argument("--accept", action="store_true")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    employer = login(base, "employer@demo.fsp.local", args.password)
    assert api(base, employer, "/api/me")["role"] == "employer"
    selection = api(base, employer, "/api/matches")
    matches = selection["matches"]
    assert matches, "employer selection is empty"
    print(f"Employer selection: {selection['total']} candidates, {len(selection['categories'])} recommended categories")
    for match in matches[:3]:
        print(f"  {match['fullName']}: {match['relevance']:.1f} — {match['explanation']}")
        assert not {"email", "phone", "contactEmail", "contactPhone"} & set(match), "contacts leaked in selection"
    bank = api(base, employer, "/api/bank/candidates?specialization=backend")
    print(f"Candidate bank (backend): {bank['total']} candidates")
    sent = api(base, employer, "/api/invitations")["invitations"]
    print("Employer invitations:", ", ".join(f"{i['candidateName']}={i['status']}" for i in sent))
    for invitation in sent:
        has_contacts = bool(invitation.get("contactEmail") or invitation.get("contactPhone"))
        assert has_contacts == (invitation["status"] == "accepted"), f"contact rule broken: {invitation}"

    candidate = login(base, "maria.volkova@demo.fsp.local", args.password)
    assert api(base, candidate, "/api/me")["role"] == "candidate"
    profile = api(base, candidate, "/api/candidates/me")
    print(f"Demo candidate: {profile['fullName']}, {profile['specialization']} {profile.get('grade')} ({profile.get('score')}/100)")
    incoming = api(base, candidate, "/api/invitations")["invitations"]
    for invitation in incoming:
        print(f"  invitation from {invitation['companyName']}: {invitation['salaryMinRub']}–{invitation['salaryMaxRub']} ₽,"
              f" contact: {invitation.get('contactMethod')}, status {invitation['status']}")
        assert invitation["salaryMinRub"] > 0 and invitation["salaryMaxRub"] >= invitation["salaryMinRub"]
    pending = [i for i in incoming if i["status"] in ("sent", "viewed")]
    if args.accept and pending:
        accepted = api(base, candidate, f"/api/invitations/{pending[0]['id']}/accept", "POST")
        print("Accepted:", accepted["status"])
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
