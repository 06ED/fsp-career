ALTER TABLE candidate_profiles
 ADD COLUMN processing_consent BOOLEAN NOT NULL DEFAULT false,
 ADD COLUMN tech_stack JSONB NOT NULL DEFAULT '[]'::jsonb,
 ADD COLUMN experience TEXT NOT NULL DEFAULT '',
 ADD COLUMN soft_skills JSONB NOT NULL DEFAULT '[]'::jsonb,
 ADD COLUMN target_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
 ADD COLUMN cv_history JSONB NOT NULL DEFAULT '[]'::jsonb;
CREATE TABLE resume_drafts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 candidate_id UUID NOT NULL REFERENCES candidate_profiles(account_id) ON DELETE CASCADE,
 parsed_fields JSONB NOT NULL,
 uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 confirmed_at TIMESTAMPTZ
);
CREATE INDEX resume_drafts_candidate_idx ON resume_drafts(candidate_id,uploaded_at DESC);
-- The API never returns correct_option; this server-only migration provides specialized items.
INSERT INTO questions(specialization,prompt,options,correct_option) VALUES
('backend','Which database isolation level rules out dirty reads?', '[{"id":"a","label":"Read uncommitted"},{"id":"b","label":"Read committed"},{"id":"c","label":"No transactions"}]','b'),
('backend','What does a database transaction rollback do?', '[{"id":"a","label":"Undo uncommitted changes"},{"id":"b","label":"Delete all tables"},{"id":"c","label":"Rebuild indexes"}]','a'),
('backend','Which approach limits connection creation overhead?', '[{"id":"a","label":"Connection pooling"},{"id":"b","label":"Per-query server restart"},{"id":"c","label":"Disabling indexes"}]','a'),
('frontend','What does semantic HTML improve?', '[{"id":"a","label":"Accessibility"},{"id":"b","label":"CPU clock"},{"id":"c","label":"DNS latency"}]','a'),
('frontend','What helps prevent XSS when rendering untrusted text?', '[{"id":"a","label":"Escaping output"},{"id":"b","label":"Using innerHTML"},{"id":"c","label":"Disabling HTTPS"}]','a'),
('frontend','What is the purpose of responsive layout?', '[{"id":"a","label":"Adapt to viewport sizes"},{"id":"b","label":"Encrypt storage"},{"id":"c","label":"Avoid caching"}]','a'),
('data','Which split evaluates a model on unseen examples?', '[{"id":"a","label":"Test set"},{"id":"b","label":"Training set only"},{"id":"c","label":"Duplicate rows"}]','a'),
('data','What does data leakage cause?', '[{"id":"a","label":"Overly optimistic evaluation"},{"id":"b","label":"Better encryption"},{"id":"c","label":"Lower disk usage"}]','a'),
('data','Which aggregation counts records in SQL?', '[{"id":"a","label":"COUNT"},{"id":"b","label":"ORDER BY"},{"id":"c","label":"WHERE"}]','a'),
('product','What does a measurable product metric describe?', '[{"id":"a","label":"User or business outcome"},{"id":"b","label":"Only team size"},{"id":"c","label":"Color palette"}]','a'),
('product','Which research method observes users completing tasks?', '[{"id":"a","label":"Usability testing"},{"id":"b","label":"Static code analysis"},{"id":"c","label":"Load balancing"}]','a'),
('product','What does an A/B test compare?', '[{"id":"a","label":"Outcomes of randomized variants"},{"id":"b","label":"Two databases only"},{"id":"c","label":"Two employee resumes"}]','a'),
('design','What is the purpose of a high-contrast text color?', '[{"id":"a","label":"Improve readability"},{"id":"b","label":"Reduce API calls"},{"id":"c","label":"Encrypt images"}]','a'),
('design','What does a wireframe primarily show?', '[{"id":"a","label":"Layout and information hierarchy"},{"id":"b","label":"Production database schema"},{"id":"c","label":"Source license"}]','a'),
('design','Why test a prototype with representative users?', '[{"id":"a","label":"Find usability problems early"},{"id":"b","label":"Replace security testing"},{"id":"c","label":"Prove every user is identical"}]','a');
