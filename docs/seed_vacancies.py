"""Idempotent demo seeding of vacancies and short tasks through the public API.

Vacancies and tasks belong to an employer account, so they are never seeded by a Flyway
migration. Pass an access token of the (synthetic) demo employer; items whose title already
exists for that employer are skipped, so the script can be re-run safely.

    python3 docs/seed_vacancies.py --api http://localhost:8080 --token "$EMPLOYER_TOKEN" \
        [--company-name "Демо-компания"]

Library use (e.g. from the lead's demo seeding script):

    from seed_vacancies import seed
    seed("http://localhost:8080", employer_token)

The employer company name must be set first (the API answers 400 otherwise); --company-name
sets it only when it is still blank.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

# Shared with the API demo seeder (DemoSeed.kt), so they live in the API resources.
FIXTURES = Path(__file__).resolve().parents[1] / "apps" / "api" / "src" / "main" / "resources" / "demo"


def _call(api: str, token: str, method: str, path: str, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Authorization": "Bearer " + token}
    if data is not None:
        headers["Content-Type"] = "application/json"
    try:
        with urlopen(Request(api.rstrip("/") + path, data=data, headers=headers, method=method), timeout=20) as response:
            raw = response.read()
    except HTTPError as error:
        raise SystemExit(f"{method} {path} -> {error.code}: {error.read()[:300]!r}") from error
    return json.loads(raw) if raw else None


def seed(api: str, token: str, company_name: str | None = None) -> dict[str, int]:
    """Creates missing fixture vacancies/tasks for the employer behind ``token``; returns counts."""
    company = _call(api, token, "GET", "/api/employers/company")
    if not company["name"].strip():
        if not company_name:
            raise SystemExit("Employer company name is blank; pass --company-name")
        _call(api, token, "PUT", "/api/employers/company", dict(company, name=company_name))

    existing = {v["title"] for v in _call(api, token, "GET", "/api/employers/vacancies")["vacancies"]}
    created_vacancies = 0
    for vacancy in json.loads((FIXTURES / "vacancies.json").read_text(encoding="utf-8")):
        if vacancy["title"] not in existing:
            _call(api, token, "POST", "/api/employers/vacancies", vacancy)
            created_vacancies += 1

    existing = {t["title"] for t in _call(api, token, "GET", "/api/employers/tasks")["tasks"]}
    created_tasks = 0
    for task in json.loads((FIXTURES / "short_tasks.json").read_text(encoding="utf-8")):
        if task["title"] not in existing:
            _call(api, token, "POST", "/api/employers/tasks", task)
            created_tasks += 1
    return {"vacancies": created_vacancies, "tasks": created_tasks}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--token", required=True, help="Employer access token (synthetic demo account only)")
    parser.add_argument("--company-name")
    arguments = parser.parse_args()
    print("created:", seed(arguments.api, arguments.token, arguments.company_name))
