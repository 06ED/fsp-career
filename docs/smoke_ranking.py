"""Deterministic *synthetic fixture* ranking/privacy matrix on the local stack.

Uses exact user IDs and direct SQL inserts for precomputed assessment/achievement
rows. These fixtures do NOT prove the assessment scored real answers or that FSP
verified an achievement; see smoke_e2e.py for the genuine question submission.
"""

import subprocess
import uuid

import smoke_e2e as smoke


def fixture(sql):
    result = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "fsp_app",
         "-d", "fsp_career", "-v", "ON_ERROR_STOP=1"],
        input=sql, text=True, capture_output=True, check=True, timeout=20,
    )
    return result.stdout


def account_subject(subject):
    return str(uuid.UUID(subject))


def seed_assessment(subject, specialization, score, grade):
    identifier = account_subject(subject)
    assert specialization in ("backend", "frontend")
    assert score in (60, 100) and grade in ("middle", "senior")
    fixture(f"""INSERT INTO assessment_attempts(candidate_id,specialization,question_ids,submitted_at,score,grade)
        SELECT id,'{specialization}',ARRAY[1,2,3,4,5]::bigint[],now(),{score},'{grade}'
        FROM accounts WHERE subject='{identifier}';""")


def add_achievement(subject, title, points):
    identifier = account_subject(subject)
    assert title in ("Synthetic-A", "Synthetic-B") and points in (12, 15)
    fixture(f"""INSERT INTO achievements(candidate_id,title,issuer,points,verified_at)
        SELECT id,'{title}','synthetic-test-only',{points},now()
        FROM accounts WHERE subject='{identifier}';""")


def matches(employer, query=""):
    return smoke.api("/api/matches" + query, employer)["matches"]


def main():
    client = smoke.create_test_client()
    users = {}
    for label in "ABCDE":
        email, subject = smoke.create_user(label)
        users[label] = (subject, smoke.token(client, email), email)
    email, employer_subject = smoke.create_user("employer")
    role = smoke.admin("/roles/employer")
    smoke.admin("/users/" + employer_subject + "/role-mappings/realm", "POST", [role], 204)
    employer = smoke.token(client, email)
    smoke.api("/api/employers/company", employer, "PUT", {"name": "Synthetic employer"})
    smoke.api("/api/employers/needs", employer, "PUT", {
        "specialization": "backend", "industry": "fintech", "minGrade": "junior",
        "description": "Ranking fixture only",
    })
    for label, (subject, bearer, _verified_email) in users.items():
        profile = smoke.api("/api/candidates/me", bearer)
        profile.update(
            fullName="Synthetic " + label,
            email="",  # Keycloak normalizes mixed-case synthetic addresses; API supplies verified email.
            phone="+70000000000", specialization="frontend" if label == "D" else "backend",
            industry="fintech" if label in "AB" else "retail",
            processingConsent=True, consentPublication=label != "C",
            techStack=["Kotlin", "SQL"] if label == "A" else ["SQL"],
            desiredGrade="senior", grade="senior", score=100,
        )
        actual = smoke.api("/api/candidates/me", bearer, "PUT", profile)
        assert actual["grade"] is None and actual["score"] is None, "Self-reported grade leaked into rank"
        if label in "ABC":
            seed_assessment(subject, "backend", 60, "middle")
        if label == "D":
            seed_assessment(subject, "frontend", 100, "senior")
    add_achievement(users["A"][0], "Synthetic-A", 12)
    add_achievement(users["A"][0], "Synthetic-B", 15)
    ranked = matches(employer)
    assert [entry["fullName"] for entry in ranked] == ["Synthetic A", "Synthetic B"]
    assert ranked[0]["achievementScore"] == 20 and ranked[0]["totalScore"] == 85
    assert ranked[1]["achievementScore"] == 0 and ranked[1]["totalScore"] == 65
    assert all("email" not in item and "phone" not in item for item in ranked)
    assert [item["fullName"] for item in matches(employer, "?verifiedAchievements=true")] == ["Synthetic A"]
    assert [item["fullName"] for item in matches(employer, "?techStack=kotlin,sql")] == ["Synthetic A"]
    assert not matches(employer, "?grade=senior")
    assert [item["fullName"] for item in matches(employer, "?specialization=frontend")] == ["Synthetic D"]
    smoke.api("/api/matches?grade=invalid", employer, expected=400)
    smoke.api("/api/matches?verifiedAchievements=perhaps", employer, expected=400)
    smoke.api("/api/matches?techStack=" + ",".join(["x"] * 11), employer, expected=400)
    # Make A/B equal and check deterministic UUID tie breaker, not fixture insertion order.
    fixture(f"DELETE FROM achievements WHERE candidate_id=(SELECT id FROM accounts WHERE subject='{account_subject(users['A'][0])}');")
    tied = matches(employer)
    assert len(tied) == 2 and all(item["totalScore"] == 65 for item in tied)
    assert [item["candidateId"] for item in tied] == sorted(item["candidateId"] for item in tied)
    # Publication is independent of processing: hiding A leaves saved profile intact.
    candidate_a = users["A"][1]
    private = smoke.api("/api/candidates/me", candidate_a)
    private["consentPublication"] = False
    smoke.api("/api/candidates/me", candidate_a, "PUT", private)
    assert [item["fullName"] for item in matches(employer)] == ["Synthetic B"]
    assert smoke.api("/api/candidates/me", candidate_a)["processingConsent"] is True
    print("PASS: injected synthetic ranking matrix: capped points, industry bonus, UUID ties, assessment gate, filters, publication/privacy")
    print("NOTE: synthetic DB fixtures are not assessment scoring evidence or official FSP achievements")


if __name__ == "__main__":
    try:
        main()
    finally:
        smoke.cleanup()
