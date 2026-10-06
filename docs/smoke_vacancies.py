"""Synthetic vacancies / applications / weekly short tasks smoke run on the local stack.

Run from the repository root like smoke_e2e.py (KEYCLOAK_ADMIN_PASSWORD = local Compose admin
password). Creates temporary Keycloak users and a direct-grant test client, publishes the demo
fixtures from docs/fixtures through the API (seed_vacancies.seed) and checks:
salary-range validation, candidate search filters and fit text, application preconditions,
contact disclosure only after the candidate applied, status transitions (409 on invalid ones),
foreign-record 404s, withdrawal, close/reopen, the weekly task offer (stable, one per week),
answers without contacts, employer rating, and erasure on consent revocation.

The confirmed category of the applicant is an *injected synthetic assessment row* (as in
smoke_ranking.py), not a scored test. Everything is removed in smoke.cleanup().
"""

import uuid

import seed_vacancies
import smoke_e2e as smoke
from smoke_ranking import fixture


def assessed(subject, specialization, score, grade):
    identifier = str(uuid.UUID(subject))
    assert specialization in ("backend", "frontend") and grade in ("junior", "middle", "senior")
    fixture(f"""INSERT INTO assessment_attempts(candidate_id,specialization,question_ids,submitted_at,score,grade)
        SELECT id,'{specialization}',ARRAY[1,2,3,4,5]::bigint[],now(),{int(score)},'{grade}'
        FROM accounts WHERE subject='{identifier}';""")


def candidate_profile(bearer, name, specialization="backend", grade="middle"):
    profile = smoke.api("/api/candidates/me", bearer)
    profile.update(fullName=name, email="", phone="+70000000000", specialization=specialization,
                   desiredGrade=grade, processingConsent=True, consentPublication=True,
                   techStack=["Kotlin", "PostgreSQL"], experience="Синтетический профиль")
    return smoke.api("/api/candidates/me", bearer, "PUT", profile)


def main():
    client = smoke.create_test_client()
    role = smoke.admin("/roles/employer")
    employers = {}
    for label in ("employer", "foreign"):
        email, subject = smoke.create_user(label)
        smoke.admin("/users/" + subject + "/role-mappings/realm", "POST", [role], 204)
        employers[label] = smoke.token(client, email)
    employer, foreign = employers["employer"], employers["foreign"]
    anna_email, anna_subject = smoke.create_user("anna")
    anna = smoke.token(client, anna_email)
    boris_email, _ = smoke.create_user("boris")
    boris = smoke.token(client, boris_email)

    # Employer side: company is required, salary range is mandatory and consistent.
    smoke.api("/api/employers/vacancies", employer, "POST", {
        "title": "x", "description": "x", "specialization": "backend", "grade": "middle",
        "salaryMinRub": 100, "salaryMaxRub": 200, "workFormat": "remote"}, expected=400)
    created = seed_vacancies.seed(smoke.API, employer, "Синтетическая компания")
    assert created == {"vacancies": 6, "tasks": 6}, created
    assert seed_vacancies.seed(smoke.API, employer) == {"vacancies": 0, "tasks": 0}, "seeding must be idempotent"
    smoke.api("/api/employers/company", foreign, "PUT", {"name": "Чужая компания"})
    own = smoke.api("/api/employers/vacancies", employer)["vacancies"]
    kotlin = next(v for v in own if "Kotlin" in v["title"])
    smoke.api("/api/employers/vacancies", employer, "POST", dict(
        {k: kotlin[k] for k in ("title", "description", "specialization", "grade", "techStack", "workFormat", "city")},
        salaryMinRub=300000, salaryMaxRub=200000), expected=400)
    smoke.api("/api/employers/vacancies/" + kotlin["id"], foreign, "PUT", dict(
        {k: kotlin[k] for k in ("title", "description", "specialization", "grade", "techStack", "workFormat", "city")},
        salaryMinRub=1, salaryMaxRub=2), expected=404)

    # Candidate side: conditions visible before contact, filters, fit explanation.
    candidate_profile(anna, "Синтетическая Анна")
    candidate_profile(boris, "Синтетический Борис", grade="junior")
    assessed(anna_subject, "backend", 72, "middle")
    smoke.api("/api/vacancies", employer, expected=403)
    cards = smoke.api("/api/vacancies?specialization=backend", anna)["vacancies"]
    card = next(v for v in cards if v["id"] == kotlin["id"])
    assert card["salaryMinRub"] == 220000 and card["companyName"] == "Синтетическая компания"
    assert card["categoryMatch"] is True and "соответствует требованию Middle" in card["fit"]
    assert all(v["specialization"] == "backend" for v in cards)
    assert not any(v["id"] == kotlin["id"] for v in smoke.api("/api/vacancies?salaryFrom=300001", anna)["vacancies"])
    assert any(v["id"] == kotlin["id"] for v in smoke.api("/api/vacancies?techStack=kotlin,KAFKA&workFormat=hybrid", anna)["vacancies"])
    smoke.api("/api/vacancies?workFormat=moon", anna, expected=400)

    # Applying: unassessed candidate is refused; contacts reach the employer only via the application.
    smoke.api("/api/vacancies/" + kotlin["id"] + "/applications", boris, "POST", {"coverNote": ""}, expected=409)
    application = smoke.api("/api/vacancies/" + kotlin["id"] + "/applications", anna, "POST",
                            {"coverNote": "Синтетическое письмо"}, expected=201)
    assert application["status"] == "sent"
    smoke.api("/api/vacancies/" + kotlin["id"] + "/applications", anna, "POST", {}, expected=409)
    smoke.api("/api/employers/vacancies/" + kotlin["id"] + "/applications", foreign, expected=404)
    applicant = smoke.api("/api/employers/vacancies/" + kotlin["id"] + "/applications", employer)["applications"][0]
    assert applicant["contactEmail"] == anna_email.lower() and applicant["contactPhone"] == "+70000000000"
    assert applicant["assessedGrade"] == "middle" and applicant["score"] == 72
    assert all("email" not in m and "phone" not in m for m in smoke.api("/api/matches?specialization=backend", employer)["matches"])

    # Status transitions (employer) and candidate view.
    status = "/api/employers/applications/" + application["id"] + "/status"
    smoke.api(status, foreign, "PUT", {"status": "viewed"}, expected=404)
    smoke.api(status, employer, "PUT", {"status": "viewed"})
    smoke.api(status, employer, "PUT", {"status": "viewed"}, expected=409)
    smoke.api(status, employer, "PUT", {"status": "accepted"})
    smoke.api(status, employer, "PUT", {"status": "rejected"}, expected=409)
    mine = smoke.api("/api/candidates/me/applications", anna)["applications"]
    assert [a["status"] for a in mine] == ["accepted"]
    assert smoke.api("/api/candidates/me/applications", boris)["applications"] == []
    smoke.api("/api/candidates/me/applications/" + application["id"], anna, "DELETE", expected=409)

    # Withdrawal removes the application and its contact disclosure; close/reopen.
    go = next(v for v in own if "Go" in v["title"])
    second = smoke.api("/api/vacancies/" + go["id"] + "/applications", anna, "POST", {}, expected=201)
    smoke.api("/api/candidates/me/applications/" + second["id"], anna, "DELETE", expected=204)
    assert smoke.api("/api/employers/vacancies/" + go["id"] + "/applications", employer)["applications"] == []
    smoke.api("/api/employers/vacancies/" + go["id"] + "/close", employer, "POST")
    smoke.api("/api/employers/vacancies/" + go["id"] + "/close", employer, "POST", expected=409)
    smoke.api("/api/vacancies/" + go["id"] + "/applications", anna, "POST", {}, expected=409)
    smoke.api("/api/employers/vacancies/" + go["id"] + "/reopen", employer, "POST")

    # Weekly short task. The pool may also hold tasks of other (demo) employers, so assert the
    # eligibility rule rather than an exact pick: same specialization, grade empty or equal.
    tasks = {t["title"]: t for t in smoke.api("/api/employers/tasks", employer)["tasks"]}
    offer = smoke.api("/api/candidates/me/tasks/current", anna)
    assert offer["state"] == "offered" and offer["task"]["specialization"] == "backend", offer
    assert offer["task"]["grade"] in (None, "middle"), offer
    assert smoke.api("/api/candidates/me/tasks/current", anna)["task"]["id"] == offer["task"]["id"], "offer is stable"
    boris_offer = smoke.api("/api/candidates/me/tasks/current", boris)
    assert boris_offer["task"]["grade"] in (None, "junior"), "middle-only tasks are not offered to a junior"
    wrong = tasks["Доступный выпадающий список"]["id"]
    smoke.api("/api/candidates/me/task-submissions", anna, "POST",
              {"taskId": wrong, "kind": "solution", "answer": "x"}, expected=409)
    answer = smoke.api("/api/candidates/me/task-submissions", anna, "POST",
                       {"taskId": offer["task"]["id"], "kind": "approach", "answer": "Синтетический подход"}, expected=201)
    smoke.api("/api/candidates/me/task-submissions", anna, "POST",
              {"taskId": offer["task"]["id"], "kind": "solution", "answer": "ещё раз"}, expected=409)
    assert smoke.api("/api/candidates/me/tasks/current", anna)["state"] == "answered"
    if offer["task"]["id"] in {t["id"] for t in tasks.values()}:
        smoke.api("/api/employers/tasks/" + offer["task"]["id"] + "/submissions", foreign, expected=404)
        answers = smoke.api("/api/employers/tasks/" + offer["task"]["id"] + "/submissions", employer)["submissions"]
        assert [a["id"] for a in answers] == [answer["id"]] and anna_email.lower() not in str(answers)
        smoke.api("/api/employers/task-submissions/" + answer["id"] + "/rating", employer, "PUT", {"rating": 6}, expected=400)
        smoke.api("/api/employers/task-submissions/" + answer["id"] + "/rating", foreign, "PUT", {"rating": 5}, expected=404)
        smoke.api("/api/employers/task-submissions/" + answer["id"] + "/rating", employer, "PUT", {"rating": 5, "comment": "Отлично"})
        assert smoke.api("/api/candidates/me/task-submissions", anna)["submissions"][0]["rating"] == 5
        signal = smoke.api("/api/employers/vacancies/" + kotlin["id"] + "/applications", employer)["applications"][0]
        assert signal["taskSubmissions"] == 1 and signal["taskAverageRating"] == 5.0
    else:
        print("NOTE: weekly task came from another employer on this stack; rating checks skipped")

    # Consent revocation erases applications, answers and offers.
    smoke.api("/api/candidates/me/consent", anna, "DELETE")
    assert smoke.api("/api/employers/vacancies/" + kotlin["id"] + "/applications", employer)["applications"] == []
    assert smoke.api("/api/candidates/me/task-submissions", anna, expected=200)["submissions"] == []
    print("PASS: vacancies with mandatory RUB range, filters/fit, application gate, contact disclosure on application, "
          "status transitions, withdrawal, close/reopen, weekly task offer/answer/rating, consent erasure")
    print("NOTE: the applicant's assessment row is an injected synthetic fixture")


if __name__ == "__main__":
    try:
        main()
    finally:
        smoke.cleanup()
