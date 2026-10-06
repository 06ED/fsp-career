-- Adaptive assessment (see apps/api/src/main/kotlin/ru/fsp/career/assessment).
-- Additive only: legacy five-question attempts keep their rows (engine_version = 'legacy-v1');
-- the legacy `questions` bank stays in place but is no longer served.
ALTER TABLE assessment_attempts
 ADD COLUMN target_grade TEXT CHECK (target_grade IN ('junior','middle','senior')),
 ADD COLUMN industry TEXT NOT NULL DEFAULT '',
 ADD COLUMN survey JSONB NOT NULL DEFAULT '{}'::jsonb,
 ADD COLUMN vacancy_id TEXT,
 ADD COLUMN vacancy_title TEXT,
 ADD COLUMN blueprint JSONB NOT NULL DEFAULT '{}'::jsonb,
 ADD COLUMN planned_items INTEGER NOT NULL DEFAULT 0 CHECK (planned_items >= 0),
 ADD COLUMN expires_at TIMESTAMPTZ,
 ADD COLUMN theta DOUBLE PRECISION,
 ADD COLUMN theta_se DOUBLE PRECISION CHECK (theta_se IS NULL OR theta_se >= 0),
 ADD COLUMN passed BOOLEAN,
 ADD COLUMN confident BOOLEAN,
 ADD COLUMN expired BOOLEAN NOT NULL DEFAULT false,
 ADD COLUMN engine_version TEXT NOT NULL DEFAULT 'legacy-v1';

-- Retake rules look up the latest completed attempt per candidate and specialization.
CREATE INDEX assessment_spec_completed_idx ON assessment_attempts(candidate_id, specialization, submitted_at DESC)
 WHERE submitted_at IS NOT NULL;

-- One row per served item instance. `answer_key` is server-only and never serialized to clients;
-- `seed` reproduces the instance from its family generator (audit / regrading).
CREATE TABLE assessment_items (
 attempt_id UUID NOT NULL REFERENCES assessment_attempts(id) ON DELETE CASCADE,
 position INTEGER NOT NULL CHECK (position >= 0),
 family_id TEXT NOT NULL,
 seed BIGINT NOT NULL,
 view JSONB NOT NULL,
 answer_key TEXT NOT NULL,
 tolerance DOUBLE PRECISION NOT NULL DEFAULT 0,
 response TEXT,
 correct BOOLEAN,
 served_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 answered_at TIMESTAMPTZ,
 PRIMARY KEY (attempt_id, position)
);
-- Exposure control counts how often each family has been served.
CREATE INDEX assessment_items_family_idx ON assessment_items(family_id);
