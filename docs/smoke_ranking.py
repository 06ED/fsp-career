"""Deterministic *synthetic fixture* ranking/privacy/FSP matrix on the local Compose stack.

Uses exact user IDs and direct SQL inserts for precomputed assessment rows and a temporary
synthetic FSP registry participant. These fixtures do NOT prove the assessment scored real
answers or that FSP verified an achievement; see smoke_e2e.py for the genuine question
submission and docs/validation/matching-results.md for the offline ranking evaluation.
"""

import random
import subprocess
import uuid

import smoke_e2e as smoke

FSP_ID = f"FSP-9{random.randint(0, 99999):05d}"  # synthetic, outside the seeded FSP-1000xx range


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


def seed_registry_participant(email):
    # Temporary synthetic registry record owned by candidate A's verified e-mail.
    assert email.endswith("@example.invalid")
    fixture(f"""INSERT INTO fsp_registry_participants(fsp_id,email,full_name,region)
        VALUES ('{FSP_ID}','{email}','Synthetic A','Москва');
        INSERT INTO fsp_registry_results(id,fsp_id,event_name,discipline,event_date,level,place,result,team_name) VALUES
        ('R-{FSP_ID}-1','{FSP_ID}','Synthetic federal cup (демо)','продуктовое программирование',current_date - 100,'federal',2,'prize',NULL),
        ('R-{FSP_ID}-2','{FSP_ID}','Synthetic regional cup (демо)','алгоритмическое программирование',current_date - 400,'regional',7,'finalist',NULL);""")


def drop_registry_participant():
    fixture(f"DELETE FROM fsp_registry_participants WHERE fsp_id='{FSP_ID}';")


def matches(employer, query=""):
    return smoke.api("/api/matches" + query, employer)["matches"]


def names(items):
    return [item["fullName"] for item in items]


def factor(item, code):
    return next(f for f in item["factors"] if f["code"] == code)


def main():
    client = smoke.create_test_client()
    users = {}
    for label in "ABCDE":
        email, subject = smoke.create_user(label)
        users[label] = (subject, smoke.token(client, email), email.lower())
    email, employer_subject = smoke.create_user("employer")
    role = smoke.admin("/roles/employer")
    smoke.admin("/users/" + employer_subject + "/role-mappings/realm", "POST", [role], 204)
    employer = smoke.token(client, email)
    smoke.api("/api/employers/company", employer, "PUT", {"name": "Synthetic employer", "contactEmail": "bad"}, expected=400)
    company = smoke.api("/api/employers/company", employer, "PUT", {
        "name": "Synthetic employer", "industry": "fintech", "contactTelegram": "t.me/synthetic_hr",
    })
    assert company["contactTelegram"] == "@synthetic_hr"
    need = smoke.api("/api/employers/needs", employer, "PUT", {
        "specialization": "backend", "industry": "fintech", "minGrade": "middle",
        "description": "Payment services on Kotlin and SQL", "techStack": ["Kotlin", "SQL", "kotlin"],
    })
    assert need["techStack"] == ["Kotlin", "SQL"]
    for label, (subject, bearer, _verified_email) in users.items():
        profile = smoke.api("/api/candidates/me", bearer)
        profile.update(
            fullName="Synthetic " + label,
            email="",  # Keycloak normalizes mixed-case synthetic addresses; API supplies verified email.
            phone="+70000000000", specialization="frontend" if label == "D" else "backend",
            industry="fintech" if label in "AB" else "retail",
            processingConsent=True, consentPublication=label != "C",
            techStack=["Kotlin", "SQL"] if label == "A" else ["SQL"],
            experience="Payment services on Kotlin and SQL" if label == "A" else "Internal reports in SQL",
            desiredGrade="senior", grade="senior", score=100,
        )
        actual = smoke.api("/api/candidates/me", bearer, "PUT", profile)
        assert actual["grade"] is None and actual["score"] is None, "Self-reported grade leaked into rank"
        if label in "ABC":
            seed_assessment(subject, "backend", 60, "middle")
        if label == "D":
            seed_assessment(subject, "frontend", 100, "senior")

    # FSP ID link: ownership is proven by the verified e-mail, not by the typed ID.
    seed_registry_participant(users["A"][2])
    try:
        assert smoke.api("/api/candidates/me/fsp", users["B"][1])["linked"] is False
        smoke.api("/api/candidates/me/fsp", users["B"][1], "POST", {"fspId": FSP_ID}, expected=404)
        smoke.api("/api/candidates/me/fsp", users["A"][1], "POST", {"fspId": "not-an-id"}, expected=400)
        linked = smoke.api("/api/candidates/me/fsp", users["A"][1], "POST", {"fspId": FSP_ID.lower()})
        assert linked["linked"] and linked["fspId"] == FSP_ID
        assert len(linked["achievements"]) == 2 and linked["totalPoints"] == 9 + 4

        selection = smoke.api("/api/matches", employer)
        ranked = selection["matches"]
        assert selection["mode"] == "need" and selection["formula"]
        assert names(ranked) == ["Synthetic A", "Synthetic B"]  # C unpublished, D other specialization, E unassessed
        assert factor(ranked[0], "fsp")["points"] > 0 and factor(ranked[1], "fsp")["points"] == 0
        assert "Нет истории ФСП" in ranked[1]["flags"]
        assert ranked[0]["relevance"] > ranked[1]["relevance"]
        assert all(set(f) >= {"code", "label", "weight", "points", "text"} for item in ranked for f in item["factors"])
        assert all("email" not in item and "phone" not in item for item in ranked)
        assert any(c["key"] == "backend:middle" and c["recommended"] for c in selection["categories"])
        assert names(matches(employer, "?verifiedAchievements=true")) == ["Synthetic A"]
        assert names(matches(employer, "?techStack=kotlin,sql")) == ["Synthetic A"]
        assert not matches(employer, "?grade=senior")  # middle candidates are below the required grade
        assert names(matches(employer, "?grade=senior&allowLowerGrade=true")) == ["Synthetic A", "Synthetic B"]
        assert names(matches(employer, "?specialization=frontend")) == ["Synthetic D"]
        for bad in ("?grade=invalid", "?verifiedAchievements=perhaps", "?techStack=" + ",".join(["x"] * 11),
                    "?limit=0", "?category=backend"):
            smoke.api("/api/matches" + bad, employer, expected=400)

        bank = smoke.api("/api/bank/candidates?category=backend:middle", employer)
        assert bank["mode"] == "bank" and names(bank["matches"])[:2] == ["Synthetic A", "Synthetic B"]
        a_id = ranked[0]["candidateId"]
        card = smoke.api("/api/bank/candidates/" + a_id, employer)
        assert card["contactsDisclosed"] is False and card["contactEmail"] is None and len(card["achievements"]) == 2
        smoke.api("/api/bank/candidates/" + str(uuid.uuid4()), employer, expected=404)

        # Privacy: hidden name/achievements neither show nor score.
        candidate_a = users["A"][1]
        smoke.api("/api/candidates/me/privacy", candidate_a, "PUT", {
            "fullName": False, "techStack": True, "experience": True, "cvHistory": True,
            "softSkills": True, "fspAchievements": False,
        })
        hidden = smoke.api("/api/bank/candidates/" + a_id, employer)
        assert hidden["fullName"].startswith("Кандидат №") and hidden["achievements"] is None
        assert not matches(employer, "?verifiedAchievements=true")
        smoke.api("/api/candidates/me/privacy", candidate_a, "PUT", {
            "fullName": True, "techStack": True, "experience": True, "cvHistory": True,
            "softSkills": True, "fspAchievements": True,
        })

        # Equal profiles without FSP tie deterministically by candidate UUID.
        assert smoke.api("/api/candidates/me/fsp", candidate_a, "DELETE")["linked"] is False
        profile_a = smoke.api("/api/candidates/me", candidate_a)
        profile_a.update(techStack=["SQL"], experience="Internal reports in SQL", email="")
        smoke.api("/api/candidates/me", candidate_a, "PUT", profile_a)
        tied = matches(employer)
        assert len(tied) == 2 and tied[0]["relevance"] == tied[1]["relevance"]
        assert [item["candidateId"] for item in tied] == sorted(item["candidateId"] for item in tied)

        # Invitation carries company name + contact method; candidate contacts stay closed.
        invitation = smoke.api("/api/invitations", employer, "POST", {
            "candidateId": a_id, "description": "Synthetic offer", "salaryMinRub": 150000, "salaryMaxRub": 200000,
        }, 201)
        assert invitation["contactMethod"] == "Telegram: @synthetic_hr" and invitation["contactEmail"] is None
        inbox = smoke.api("/api/invitations", candidate_a)["invitations"][0]
        assert inbox["companyName"] == "Synthetic employer" and inbox["contactMethod"] == "Telegram: @synthetic_hr"

        # Publication is independent of processing: hiding A leaves saved profile intact.
        private = smoke.api("/api/candidates/me", candidate_a)
        private["consentPublication"] = False
        smoke.api("/api/candidates/me", candidate_a, "PUT", private)
        assert names(matches(employer)) == ["Synthetic B"]
        assert smoke.api("/api/candidates/me", candidate_a)["processingConsent"] is True
    finally:
        drop_registry_participant()
    print("PASS: synthetic ranking matrix: category gate, FSP link by verified e-mail, explanations, filters, "
          "bank/card, privacy, UUID ties, invitation contact method, publication withdrawal")
    print("NOTE: synthetic DB fixtures are not assessment scoring evidence or official FSP achievements")


if __name__ == "__main__":
    try:
        main()
    finally:
        smoke.cleanup()
