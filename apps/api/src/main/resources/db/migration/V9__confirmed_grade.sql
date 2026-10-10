-- Failed adaptive attempts cannot confirm Junior. Keep history and genuine earlier confirmations.
ALTER TABLE assessment_attempts DROP CONSTRAINT assessment_attempts_check;
ALTER TABLE assessment_attempts ADD CHECK (
 (submitted_at IS NULL AND score IS NULL AND grade IS NULL) OR
 (submitted_at IS NOT NULL AND score IS NOT NULL)
);

WITH confirmations AS (
 SELECT id, max(CASE
   WHEN engine_version = 'cat-v1' AND passed THEN
     CASE target_grade WHEN 'junior' THEN 0 WHEN 'middle' THEN 1 WHEN 'senior' THEN 2 END
   WHEN engine_version <> 'cat-v1' THEN
     CASE grade WHEN 'junior' THEN 0 WHEN 'middle' THEN 1 WHEN 'senior' THEN 2 END
 END) OVER (PARTITION BY candidate_id, specialization ORDER BY submitted_at, started_at, id
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS confirmed
 FROM assessment_attempts WHERE submitted_at IS NOT NULL
)
UPDATE assessment_attempts a SET grade = CASE c.confirmed WHEN 0 THEN 'junior' WHEN 1 THEN 'middle' WHEN 2 THEN 'senior' END
FROM confirmations c WHERE a.id = c.id AND a.engine_version = 'cat-v1';
