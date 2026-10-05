"""Synthetic 90-day retake, answer validation and preserved-grade matrix.

An older senior result is *injected into the isolated local DB*. The new five
answers are submitted through the real API and checked against the public seed
bank; this does not establish psychometric validity or independent form quality.
"""

import subprocess
import uuid

import smoke_e2e as smoke


def sql(statement):
    return subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "fsp_app",
         "-d", "fsp_career", "-v", "ON_ERROR_STOP=1", "-AtF", "|", "-c", statement],
        check=True, capture_output=True, text=True, timeout=20,
    ).stdout.strip()


def main():
    client = smoke.create_test_client()
    email, subject = smoke.create_user("retake")
    bearer = smoke.token(client, email)
    profile = smoke.api("/api/candidates/me", bearer)
    profile.update(fullName="Synthetic retake", email="", processingConsent=True,
                   specialization="backend", desiredGrade="senior")
    smoke.api("/api/candidates/me", bearer, "PUT", profile)
    subject = str(uuid.UUID(subject))
    assert sql(f"""INSERT INTO assessment_attempts(candidate_id,specialization,question_ids,submitted_at,score,grade)
        SELECT id,'backend',ARRAY[1,2,3,4,5]::bigint[],now()-interval '91 days',80,'senior'
        FROM accounts WHERE subject='{subject}' RETURNING grade;""").splitlines()[0] == "senior"
    assignment = smoke.api("/api/assessments/questions", bearer)
    assert len(assignment["questions"]) == 5
    same = smoke.api("/api/assessments/questions", bearer)
    assert same["attemptId"] == assignment["attemptId"]
    ids = [int(q["id"]) for q in assignment["questions"]]
    rows = sql("SELECT id,correct_option FROM questions WHERE id IN (" + ",".join(map(str, ids)) + ")")
    correct = {int(line.split("|")[0]): line.split("|")[1] for line in rows.splitlines()}
    assert set(correct) == set(ids)
    answers = []
    for index, question in enumerate(assignment["questions"]):
        actual = correct[question["id"]]
        alternatives = [option["id"] for option in question["options"] if option["id"] != actual]
        answers.append({"questionId": question["id"],
                        "optionId": actual if index < 2 else alternatives[0]})
    payload = {"attemptId": assignment["attemptId"], "answers": answers}
    smoke.api("/api/assessments", bearer, "POST", {
        "attemptId": payload["attemptId"], "answers": answers[:-1],
    }, expected=400)
    smoke.api("/api/assessments", bearer, "POST", {
        "attemptId": payload["attemptId"], "answers": [*answers[:-1], answers[0]],
    }, expected=400)
    smoke.api("/api/assessments", bearer, "POST", {
        "attemptId": payload["attemptId"],
        "answers": [dict(answers[0], optionId="invalid"), *answers[1:]],
    }, expected=400)
    other_email, _other_subject = smoke.create_user("foreign")
    other = smoke.token(client, other_email)
    smoke.api("/api/assessments", other, "POST", payload, expected=404)
    result = smoke.api("/api/assessments", bearer, "POST", payload, expected=201)
    assert result["score"] == 40 and result["grade"] == "senior"
    history = smoke.api("/api/assessments/history", bearer)["results"]
    assert [entry["score"] for entry in history] == [40, 80]
    saved = smoke.api("/api/candidates/me", bearer)
    assert saved["grade"] == "senior" and saved["score"] == 80
    smoke.api("/api/assessments/questions", bearer, expected=409)
    smoke.api("/api/assessments", bearer, "POST", payload, expected=409)
    general_email, _general_subject = smoke.create_user("general")
    general = smoke.token(client, general_email)
    general_profile = smoke.api("/api/candidates/me", general)
    general_profile.update(fullName="Synthetic general", email="", processingConsent=True,
                           specialization="general")
    smoke.api("/api/candidates/me", general, "PUT", general_profile)
    general_questions = smoke.api("/api/assessments/questions", general)["questions"]
    assert len(general_questions) == 5 and len({q["id"] for q in general_questions}) == 5
    print("PASS: older senior retained after real 40/100 retake; 90-day cooldown, five unique general questions, input and ownership checks")
    print("NOTE: original senior attempt is a synthetic DB fixture, not a scored test")


if __name__ == "__main__":
    try:
        main()
    finally:
        smoke.cleanup()
