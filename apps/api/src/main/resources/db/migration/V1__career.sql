CREATE TABLE accounts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), subject TEXT NOT NULL UNIQUE,
 role TEXT NOT NULL CHECK (role IN ('candidate','employer')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE candidate_profiles (
 account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
 full_name TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '',
 consent_publication BOOLEAN NOT NULL DEFAULT false,
 specialization TEXT NOT NULL DEFAULT '', industry TEXT NOT NULL DEFAULT '',
 desired_grade TEXT NOT NULL DEFAULT 'junior' CHECK (desired_grade IN ('junior','middle','senior')),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE employer_companies (
 account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
 name TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', website TEXT NOT NULL DEFAULT ''
);
CREATE TABLE employer_needs (
 account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
 specialization TEXT NOT NULL DEFAULT '', industry TEXT NOT NULL DEFAULT '',
 min_grade TEXT NOT NULL DEFAULT 'junior' CHECK (min_grade IN ('junior','middle','senior')),
 description TEXT NOT NULL DEFAULT ''
);
CREATE TABLE questions (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 specialization TEXT NOT NULL, prompt TEXT NOT NULL,
 options JSONB NOT NULL, correct_option TEXT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX questions_specialization_idx ON questions(specialization) WHERE active;
CREATE TABLE assessment_attempts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), candidate_id UUID NOT NULL REFERENCES candidate_profiles(account_id),
 specialization TEXT NOT NULL, question_ids BIGINT[] NOT NULL,
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(), submitted_at TIMESTAMPTZ,
 score INTEGER CHECK (score BETWEEN 0 AND 100), grade TEXT CHECK (grade IN ('junior','middle','senior')),
 CHECK ((submitted_at IS NULL AND score IS NULL AND grade IS NULL) OR (submitted_at IS NOT NULL AND score IS NOT NULL AND grade IS NOT NULL))
);
CREATE UNIQUE INDEX one_open_attempt ON assessment_attempts(candidate_id) WHERE submitted_at IS NULL;
CREATE INDEX assessment_completed_idx ON assessment_attempts(candidate_id, submitted_at DESC) WHERE submitted_at IS NOT NULL;
CREATE TABLE assessment_answers (
 attempt_id UUID NOT NULL REFERENCES assessment_attempts(id) ON DELETE CASCADE,
 question_id BIGINT NOT NULL REFERENCES questions(id), option_id TEXT NOT NULL,
 PRIMARY KEY(attempt_id,question_id)
);
CREATE TABLE achievements (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 candidate_id UUID NOT NULL REFERENCES candidate_profiles(account_id) ON DELETE CASCADE,
 title TEXT NOT NULL, issuer TEXT NOT NULL, points INTEGER NOT NULL CHECK (points BETWEEN 1 AND 20),
 verified_at TIMESTAMPTZ NOT NULL,
 UNIQUE(candidate_id,title,issuer)
);
CREATE TABLE invitations (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), employer_id UUID NOT NULL REFERENCES employer_companies(account_id),
 candidate_id UUID NOT NULL REFERENCES candidate_profiles(account_id),
 description TEXT NOT NULL CHECK (length(trim(description)) > 0),
 salary_min_rub INTEGER NOT NULL CHECK (salary_min_rub > 0),
 salary_max_rub INTEGER NOT NULL CHECK (salary_max_rub >= salary_min_rub),
 status TEXT NOT NULL DEFAULT 'sent' CHECK (status IN ('sent','viewed','accepted','rejected')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX invitations_candidate_idx ON invitations(candidate_id,created_at DESC);
CREATE INDEX invitations_employer_idx ON invitations(employer_id,created_at DESC);
INSERT INTO questions(specialization,prompt,options,correct_option) VALUES
('general','Which HTTP method is conventionally idempotent?', '[{"id":"a","label":"POST"},{"id":"b","label":"PUT"},{"id":"c","label":"PATCH"}]','b'),
('general','Which database property prevents partial transactions?', '[{"id":"a","label":"Atomicity"},{"id":"b","label":"Isolation"},{"id":"c","label":"Durability"}]','a'),
('general','Which index generally helps equality lookups?', '[{"id":"a","label":"B-tree"},{"id":"b","label":"No index"},{"id":"c","label":"Heap scan"}]','a'),
('general','What does a 401 HTTP response indicate?', '[{"id":"a","label":"Unauthenticated"},{"id":"b","label":"Missing resource"},{"id":"c","label":"Rate limited"}]','a'),
('general','Which practice reduces SQL injection risk?', '[{"id":"a","label":"String concatenation"},{"id":"b","label":"Prepared parameters"},{"id":"c","label":"Escaping HTML"}]','b'),
('general','What should a retry-safe API operation be?', '[{"id":"a","label":"Idempotent"},{"id":"b","label":"Stateful"},{"id":"c","label":"Unbounded"}]','a'),
('general','What is the purpose of a database foreign key?', '[{"id":"a","label":"Referential integrity"},{"id":"b","label":"Encryption"},{"id":"c","label":"Compression"}]','a'),
('general','Which status indicates a successful creation?', '[{"id":"a","label":"201"},{"id":"b","label":"404"},{"id":"c","label":"503"}]','a'),
('general','What is a safe password storage mechanism?', '[{"id":"a","label":"Plaintext"},{"id":"b","label":"Adaptive password hash"},{"id":"c","label":"Base64"}]','b'),
('general','What does TLS protect in transit?', '[{"id":"a","label":"Confidentiality and integrity"},{"id":"b","label":"Database indexes"},{"id":"c","label":"CPU scheduling"}]','a');
