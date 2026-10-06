"""Synthetic smoke test of the adaptive assessment against a running local stack.

Flow: survey → declared grade (senior) → adaptive test answered wrongly → immediate lower-grade test
(middle) answered correctly → retake rules, history, profile category, ownership and input checks.
Answer keys are read from the isolated local DB (they are never served by the API), so outcomes are
deterministic. This checks the mechanics end to end; psychometric quality is in docs/validation/.
"""

import json
import subprocess
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import smoke_e2e as smoke

SURVEY = {"industry": "fintech", "specialization": "backend", "experienceYears": 4,
          "teamRoles": ["individual"], "workFormat": "remote"}


def sql(statement):
    return subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "fsp_app",
         "-d", "fsp_career", "-v", "ON_ERROR_STOP=1", "-AtF", "|", "-c", statement],
        check=True, capture_output=True, text=True, timeout=20,
    ).stdout.strip()


def choose_role(bearer, role="candidate"):
    """Accounts are bound to a role in-app; repeated calls for the same role are tolerated."""
    request = Request(smoke.API + "/api/me/role", data=json.dumps({"role": role}).encode(), method="POST",
                      headers={"Authorization": "Bearer " + bearer, "Content-Type": "application/json"})
    try:
        with urlopen(request, timeout=20) as response:
            assert response.status in (200, 201, 204)
    except HTTPError as error:
        assert error.code == 409, f"role choice failed: {error.code}"


def candidate(kind, client):
    email, _subject = smoke.create_user(kind)
    bearer = smoke.token(client, email)
    choose_role(bearer)
    profile = smoke.api("/api/candidates/me", bearer)
    profile.update(fullName="Synthetic " + kind, email="", processingConsent=True)
    smoke.api("/api/candidates/me", bearer, "PUT", profile)
    return bearer


def current_key(attempt_id):
    row = sql(f"SELECT position, answer_key, view->>'format' FROM assessment_items "
              f"WHERE attempt_id='{uuid.UUID(attempt_id)}' AND answered_at IS NULL")
    position, key, fmt = row.split("|")
    return int(position), key, fmt


def finish(bearer, attempt, correctly):
    """Answers every item via the API; the key only decides which answer to send."""
    while True:
        position, key, fmt = current_key(attempt["attemptId"])
        assert attempt["position"] == position and not {"answer", "answerKey", "tolerance"} & attempt["item"].keys()
        if fmt == "choice":
            answer = "abcd"[int(key)] if correctly else "abcd"[(int(key) + 1) % 4]
        else:
            answer = key if correctly else str(float(key) + 1000)
        outcome = smoke.api(f"/api/assessments/attempts/{attempt['attemptId']}/answers", bearer, "POST",
                            {"position": position, "answer": answer})
        if outcome["done"]:
            return outcome["result"]
        assert outcome["result"] is None and outcome["attempt"]["position"] == position + 1
        attempt = outcome["attempt"]


def main():
    client = smoke.create_test_client()
    bearer = candidate("assessment", client)

    catalog = smoke.api("/api/assessments/catalog", bearer)
    assert catalog["testLength"] == 12 and {g["id"] for g in catalog["grades"]} == {"junior", "middle", "senior"}
    assert any(v["id"] == "fintech-backend" for v in catalog["vacancies"])
    preview = smoke.api("/api/assessments/blueprint", bearer, "POST", {"vacancyId": "fintech-backend"})
    assert preview["specialization"] == "backend" and sum(t["items"] for t in preview["topics"]) == 12
    assert {"kotlin", "postgresql"} <= {s["id"] for s in preview["skills"]}
    assert preview["sampleItems"] and all(not {"answer", "answerKey"} & item.keys() for item in preview["sampleItems"])
    smoke.api("/api/assessments/blueprint", bearer, "POST", {"vacancyId": "missing"}, expected=404)

    smoke.api("/api/assessments/attempts", bearer, "POST",
              {"survey": dict(SURVEY, industry="unknown"), "targetGrade": "senior"}, expected=400)
    first = smoke.api("/api/assessments/attempts", bearer, "POST",
                      {"survey": SURVEY, "targetGrade": "senior", "vacancyId": "fintech-backend"}, expected=201)
    assert first["position"] == 0 and first["total"] == 12 and first["item"]
    assert smoke.api("/api/assessments/attempts/current", bearer)["attempt"]["attemptId"] == first["attemptId"]
    smoke.api("/api/assessments/attempts", bearer, "POST", {"survey": SURVEY, "targetGrade": "senior"}, expected=409)
    profile = smoke.api("/api/candidates/me", bearer)
    assert (profile["specialization"], profile["industry"], profile["desiredGrade"]) == ("backend", "fintech", "senior")

    answers = f"/api/assessments/attempts/{first['attemptId']}/answers"
    smoke.api(answers, bearer, "POST", {"position": 5, "answer": "a"}, expected=409)
    if first["item"]["format"] == "choice":
        smoke.api(answers, bearer, "POST", {"position": 0, "answer": "z"}, expected=400)
    else:
        smoke.api(answers, bearer, "POST", {"position": 0, "answer": "много"}, expected=400)
    other = candidate("foreign", client)
    smoke.api(answers, other, "POST", {"position": 0, "answer": "a"}, expected=404)

    failed = finish(bearer, first, correctly=False)
    assert failed["passed"] is False and failed["grade"] == "junior" and failed["score"] < 20
    assert [o["grade"] for o in failed["nextOptions"] if o["allowed"]] == ["middle"]
    smoke.api(answers, bearer, "POST", {"position": 11, "answer": "a"}, expected=409)
    blocked = smoke.api("/api/assessments/eligibility?specialization=backend", bearer)
    assert [o["grade"] for o in blocked["options"] if o["allowed"]] == ["middle"]
    smoke.api("/api/assessments/attempts", bearer, "POST", {"survey": SURVEY, "targetGrade": "senior"}, expected=409)

    lower = smoke.api("/api/assessments/attempts", bearer, "POST", {"survey": SURVEY, "targetGrade": "middle"}, expected=201)
    passed = finish(bearer, lower, correctly=True)
    assert passed["passed"] is True and passed["grade"] == "middle" and passed["previousGrade"] == "junior"
    assert not any(o["allowed"] for o in passed["nextOptions"]), "senior was failed in the window"

    history = smoke.api("/api/assessments/history", bearer)["results"]
    assert [h["targetGrade"] for h in history] == ["middle", "senior"]
    assert history[1]["vacancyId"] == "fintech-backend" and history[0]["survey"]["industry"] == "fintech"
    saved = smoke.api("/api/candidates/me", bearer)
    assert saved["grade"] == "middle" and saved["score"] == passed["score"]
    smoke.api("/api/assessments/attempts", bearer, "POST", {"survey": SURVEY, "targetGrade": "junior"}, expected=409)
    print("PASS: survey → senior test failed → immediate middle test passed; grade never lowered, 90-day rule, "
          "keys never served, ownership and input checks")


if __name__ == "__main__":
    try:
        main()
    finally:
        smoke.cleanup()
