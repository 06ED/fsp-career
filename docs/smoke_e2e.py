"""Synthetic local-only Keycloak + API smoke test; never point at real identities.

Run from the repository root with KEYCLOAK_ADMIN_PASSWORD set to the *local*
Compose admin password. Creates and removes two fake users plus a temporary
password-grant test client; the production fsp-web client retains PKCE-only auth.
This is functional smoke coverage, not assessment calibration or email ownership.
"""

from __future__ import annotations

import json
import os
import secrets
import subprocess
import uuid
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BASE = "http://localhost:8081"
API = "http://localhost:8080"
REALM = "fsp-career"
ADMIN_PASSWORD = os.environ["KEYCLOAK_ADMIN_PASSWORD"]
RUN = uuid.uuid4().hex[:10]
PASSWORD = secrets.token_urlsafe(20)
created_users: list[str] = []
created_client: str | None = None


def http(url: str, method: str = "GET", body=None, bearer=None, expected=200, form=False):
    headers = {}
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    if body is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
        body = urlencode(body).encode() if form else json.dumps(body).encode()
    try:
        with urlopen(Request(url, data=body, headers=headers, method=method), timeout=20) as response:
            status, data, location = response.status, response.read(), response.headers.get("Location")
    except HTTPError as error:
        status, data, location = error.code, error.read(), None
    if status != expected:
        raise AssertionError(f"{method} {url.split('?')[0]} returned {status}, expected {expected}: {data[:300]!r}")
    if data.startswith(b"%PDF-"):
        return data
    return json.loads(data) if data else location


def admin_token():
    return http(BASE + "/realms/master/protocol/openid-connect/token", "POST", {
        "client_id": "admin-cli", "grant_type": "password",
        "username": os.getenv("KEYCLOAK_ADMIN_USER", "admin"), "password": ADMIN_PASSWORD,
    }, form=True)["access_token"]


def admin(path, method="GET", body=None, expected=200):
    return http(BASE + "/admin/realms/" + REALM + path, method, body, admin_token(), expected)


def token(client_id, username):
    return http(BASE + "/realms/" + REALM + "/protocol/openid-connect/token", "POST", {
        "client_id": client_id, "grant_type": "password", "username": username,
        "password": PASSWORD,
    }, form=True)["access_token"]


def api(path, bearer, method="GET", body=None, expected=200):
    return http(API + path, method, body, bearer, expected)


def upload_resume(bearer, pdf):
    boundary = "----fsp-smoke-" + RUN
    body = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").encode() + pdf + ("\r\n--" + boundary + "--\r\n").encode()
    request = Request(API + "/api/candidates/me/resume-upload", data=body, method="POST",
                      headers={"Authorization": "Bearer " + bearer,
                               "Content-Type": "multipart/form-data; boundary=" + boundary})
    with urlopen(request, timeout=20) as response:
        assert response.status == 201
        return json.load(response)


def create_user(kind, email_verified=True):
    username = f"smoke-{RUN}-{kind}"
    location = admin("/users", "POST", {
        "username": username, "email": username + "@example.invalid", "enabled": True,
        "emailVerified": email_verified, "firstName": "Synthetic", "lastName": kind,
        "credentials": [{"type": "password", "value": PASSWORD, "temporary": False}],
    }, 201)
    user_id = location.rsplit("/", 1)[-1]
    created_users.append(user_id)
    return username + "@example.invalid", user_id


def cleanup():
    # Remove temporary Keycloak credentials even if an assertion failed.
    for user_id in created_users:
        try:
            admin("/users/" + user_id, "DELETE", expected=204)
        except Exception as error:
            print("Keycloak user cleanup failed:", error)
    if created_client:
        try:
            admin("/clients/" + created_client, "DELETE", expected=204)
        except Exception as error:
            print("Keycloak test-client cleanup failed:", error)
    # Delete only this run's exact synthetic subjects; referential FKs require
    # invitation removal first if the scenario failed before consent revocation.
    if created_users:
        ids = [str(uuid.UUID(value)) for value in created_users]
        quoted = ",".join("'" + value + "'" for value in ids)
        sql = f"DELETE FROM invitations WHERE candidate_id IN (SELECT id FROM accounts WHERE subject IN ({quoted})) OR employer_id IN (SELECT id FROM accounts WHERE subject IN ({quoted})); DELETE FROM assessment_attempts WHERE candidate_id IN (SELECT id FROM accounts WHERE subject IN ({quoted})); DELETE FROM accounts WHERE subject IN ({quoted});"
        try:
            subprocess.run(["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "fsp_app", "-d", "fsp_career", "-v", "ON_ERROR_STOP=1"], input=sql, text=True, capture_output=True, check=True, timeout=20)
        except Exception as error:
            print("Synthetic API fixture cleanup failed:", error)


def create_test_client():
    global created_client
    client_id = "fsp-smoke-" + RUN
    location = admin("/clients", "POST", {
        "clientId": client_id, "enabled": True, "publicClient": True,
        "protocol": "openid-connect", "standardFlowEnabled": False,
        "directAccessGrantsEnabled": True,
        "protocolMappers": [{
            "name": "fsp-api-audience", "protocol": "openid-connect",
            "protocolMapper": "oidc-audience-mapper", "consentRequired": False,
            "config": {"included.client.audience": "fsp-api", "id.token.claim": "false", "access.token.claim": "true"},
        }],
    }, 201)
    created_client = location.rsplit("/", 1)[-1]
    return client_id


def main():
    client_id = create_test_client()
    candidate_name, candidate_id = create_user("candidate")
    employer_name, employer_id = create_user("employer")
    employer_role = admin("/roles/employer")
    admin("/users/" + employer_id + "/role-mappings/realm", "POST", [employer_role], 204)
    candidate = token(client_id, candidate_name)
    employer = token(client_id, employer_name)
    header, claims, signature = candidate.split(".")
    invalid_signature = ("A" if signature[0] != "A" else "B") + signature[1:]
    api("/api/candidates/me", header + "." + claims + "." + invalid_signature, expected=401)
    api("/api/employers/company", candidate, expected=403)
    api("/api/candidates/me", employer, expected=403)
    profile = api("/api/candidates/me", candidate)
    assert profile["processingConsent"] is False
    api("/api/candidates/me", candidate, "PUT", dict(profile, fullName="No consent"), expected=400)
    api("/api/candidates/me", candidate, "PUT", dict(profile, processingConsent=True,
        email="spoof@example.invalid"), expected=400)
    profile.update(fullName="Тестовый специалист", email=candidate_name,
                   phone="+70000000000", specialization="backend", industry="fintech",
                   desiredGrade="senior", processingConsent=True, consentPublication=True,
                   techStack=["Kotlin", "SQL"], experience="Синтетический профиль")
    saved = api("/api/candidates/me", candidate, "PUT", profile)
    assert saved["email"] == profile["email"] and saved["grade"] is None
    resume = api("/api/candidates/me/resume.pdf", candidate)
    assert resume.startswith(b"%PDF-") and len(resume) > 1000
    draft = upload_resume(candidate, resume)
    assert draft["draftId"] and api("/api/candidates/me", candidate)["fullName"] == profile["fullName"]
    reviewed = api("/api/candidates/me/resume-confirm", candidate, "POST", {
        "draftId": draft["draftId"], "fullName": profile["fullName"], "phone": profile["phone"],
        "experience": profile["experience"], "techStack": profile["techStack"],
    })
    assert reviewed["email"] == profile["email"] and reviewed["consentPublication"] is True
    questions = api("/api/assessments/questions", candidate)
    assert len(questions["questions"]) == 5
    assert all("correctOption" not in item for item in questions["questions"])
    answers = [{"questionId": item["id"], "optionId": item["options"][0]["id"]}
               for item in questions["questions"]]
    outcome = api("/api/assessments", candidate, "POST", {
        "attemptId": questions["attemptId"], "answers": answers,
    }, 201)
    assert outcome["grade"] in ("junior", "middle", "senior")
    assert len(api("/api/assessments/history", candidate)["results"]) == 1
    api("/api/assessments/questions", candidate, expected=409)
    api("/api/employers/company", employer, "PUT", {"name": "Тестовая компания"})
    api("/api/employers/needs", employer, "PUT", {
        "specialization": "backend", "industry": "fintech", "minGrade": "junior",
        "description": "Только синтетический тест",
    })
    matches = api("/api/matches?techStack=Kotlin,SQL", employer)["matches"]
    assert len(matches) == 1 and matches[0]["fullName"] == profile["fullName"]
    assert "email" not in matches[0] and "phone" not in matches[0]
    invitation = api("/api/invitations", employer, "POST", {
        "candidateId": matches[0]["candidateId"], "description": "Тестовый оффер",
        "salaryMinRub": 120000, "salaryMaxRub": 180000,
    }, 201)
    assert invitation["contactEmail"] is None and invitation["contactPhone"] is None
    candidate_inbox = api("/api/invitations", candidate)["invitations"]
    assert candidate_inbox[0]["contactEmail"] is None
    api("/api/invitations/" + invitation["id"] + "/accept", employer, "POST", expected=403)
    accepted = api("/api/invitations/" + invitation["id"] + "/accept", candidate, "POST", expected=200)
    assert accepted["contactEmail"] == profile["email"]
    assert api("/api/invitations", employer)["invitations"][0]["contactPhone"] == profile["phone"]
    cleared = api("/api/candidates/me/consent", candidate, "DELETE")
    assert not cleared["processingConsent"] and not cleared["fullName"]
    assert not api("/api/matches", employer)["matches"]
    assert not api("/api/invitations", employer)["invitations"]
    mappers = admin("/clients/" + created_client + "/protocol-mappers/models")
    audience_mapper = next(item for item in mappers if item["name"] == "fsp-api-audience")
    admin("/clients/" + created_client + "/protocol-mappers/models/" + audience_mapper["id"], "DELETE", expected=204)
    api("/api/candidates/me", token(client_id, candidate_name), expected=401)
    print("PASS: real Keycloak JWT, bad-signature/audience rejection, role boundaries, consent, five-question assessment, cooldown, matching, reviewed PDF import/export, invitation contact gate, erasure")
    print("NOTE: accounts are synthetic admin-created/verified; browser self-registration and psychometric quality are not tested")


if __name__ == "__main__":
    try:
        main()
    finally:
        cleanup()
