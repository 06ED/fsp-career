-- Vacancies + candidate applications (classic secondary path) and regular short tasks.
-- Additive only: new tables, no changes to existing columns.

CREATE TABLE vacancies (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 employer_id UUID NOT NULL REFERENCES employer_companies(account_id) ON DELETE CASCADE,
 title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 200),
 description TEXT NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 6000),
 -- Same dictionary as candidate profiles / assessment bank.
 specialization TEXT NOT NULL CHECK (specialization IN ('backend','frontend','data','product','design')),
 grade TEXT NOT NULL CHECK (grade IN ('junior','middle','senior')),
 tech_stack JSONB NOT NULL DEFAULT '[]'::jsonb,
 -- Spec rule 2.2.1: the RUB salary range "от–до" is mandatory on every vacancy.
 salary_min_rub INTEGER NOT NULL CHECK (salary_min_rub > 0),
 salary_max_rub INTEGER NOT NULL CHECK (salary_max_rub >= salary_min_rub),
 work_format TEXT NOT NULL CHECK (work_format IN ('office','remote','hybrid')),
 city TEXT NOT NULL DEFAULT '',
 status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','closed')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX vacancies_open_idx ON vacancies(specialization, created_at DESC) WHERE status = 'open';
CREATE INDEX vacancies_employer_idx ON vacancies(employer_id, created_at DESC);

CREATE TABLE vacancy_applications (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 vacancy_id UUID NOT NULL REFERENCES vacancies(id) ON DELETE CASCADE,
 candidate_id UUID NOT NULL REFERENCES candidate_profiles(account_id) ON DELETE CASCADE,
 cover_note TEXT NOT NULL DEFAULT '' CHECK (length(cover_note) <= 2000),
 status TEXT NOT NULL DEFAULT 'sent' CHECK (status IN ('sent','viewed','accepted','rejected')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE (vacancy_id, candidate_id)
);
CREATE INDEX vacancy_applications_candidate_idx ON vacancy_applications(candidate_id, created_at DESC);

CREATE TABLE short_tasks (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 employer_id UUID NOT NULL REFERENCES employer_companies(account_id) ON DELETE CASCADE,
 title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 200),
 body TEXT NOT NULL CHECK (length(trim(body)) BETWEEN 1 AND 4000),
 specialization TEXT NOT NULL CHECK (specialization IN ('backend','frontend','data','product','design')),
 -- NULL = task suits every grade of the specialization.
 grade TEXT CHECK (grade IN ('junior','middle','senior')),
 active BOOLEAN NOT NULL DEFAULT true,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX short_tasks_active_idx ON short_tasks(specialization) WHERE active;
CREATE INDEX short_tasks_employer_idx ON short_tasks(employer_id, created_at DESC);

CREATE TABLE short_task_submissions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 task_id UUID NOT NULL REFERENCES short_tasks(id) ON DELETE CASCADE,
 candidate_id UUID NOT NULL REFERENCES candidate_profiles(account_id) ON DELETE CASCADE,
 -- 'solution' = full answer, 'approach' = proposed way to solve it (both allowed by the spec).
 kind TEXT NOT NULL CHECK (kind IN ('solution','approach')),
 answer TEXT NOT NULL CHECK (length(trim(answer)) BETWEEN 1 AND 8000),
 submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 rating INTEGER CHECK (rating BETWEEN 1 AND 5),
 rating_comment TEXT NOT NULL DEFAULT '' CHECK (length(rating_comment) <= 1000),
 rated_at TIMESTAMPTZ,
 CHECK ((rating IS NULL) = (rated_at IS NULL)),
 UNIQUE (task_id, candidate_id)
);
-- Serves Transaction.lastTaskActivity()/taskSignals(): latest submission per candidate.
CREATE INDEX short_task_submissions_candidate_idx ON short_task_submissions(candidate_id, submitted_at DESC);

-- One offered task per candidate per weekly period (Monday 00:00 UTC). Persisting the
-- offer keeps it stable for the whole week even if employers add tasks mid-week.
CREATE TABLE short_task_offers (
 candidate_id UUID NOT NULL REFERENCES candidate_profiles(account_id) ON DELETE CASCADE,
 period_start DATE NOT NULL,
 task_id UUID NOT NULL REFERENCES short_tasks(id) ON DELETE CASCADE,
 offered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (candidate_id, period_start)
);
