-- =====================================================================================
-- CareerAI — remove users created by automated integration tests (local development DB).
--
-- SAFE BY DEFAULT: this script is a DRY RUN unless @apply is set to 1 before it runs.
--
--   Dry run (lists candidates and the rows that would be deleted; deletes nothing):
--     mysql -u root -p careerai < scripts/cleanup-test-data.sql
--
--   Apply (same checks, then deletes inside ONE transaction):
--     mysql -u root -p careerai -e "SET @apply = 1; SOURCE scripts/cleanup-test-data.sql;"
--
-- WHICH USERS: only emails that match an explicit allow-list of generated-fixture patterns:
--     <known test prefix>-<digits>@example.com
-- The digits are the Date.now()/timestamp suffix every test fixture appends. Anything else is
-- preserved: real or manual accounts (gmail.com, outlook.com, ...), example.com addresses without
-- a generated numeric suffix (e.g. mugdha@example.com), and any prefix not listed below.
--
-- WHAT IS DELETED (for those users only), children first because no foreign key cascades:
--   interview_answers -> interview_questions -> interview_sessions
--   coach_messages -> coach_conversations
--   practice_results -> practice_sessions
--   job_match_analyses (by user, and by the users' resumes)
--   resume_analyses, resume_versions -> resumes
--   profile_skills -> profiles
--   plan_task_completions, ai_usage_logs
--   users (last)
-- Uploaded resume FILES (uploads/resumes/<userId>/) are not touched by SQL; remove those
-- folders separately for the deleted ids only.
-- =====================================================================================

SET @apply = COALESCE(@apply, 0);

DROP TABLE IF EXISTS cleanup_users;
-- A short-lived helper table (MySQL cannot reference a TEMPORARY table twice in one query).
CREATE TABLE cleanup_users (id BIGINT PRIMARY KEY, email VARCHAR(255) NOT NULL);

INSERT INTO cleanup_users (id, email)
SELECT id, email
FROM users
WHERE email REGEXP CONCAT(
    '^(',
      'jwt-test|flow-test|profile-test|onboard-test|react-flow|',
      'practice-test|practice-other|',
      'phase-a|phase-b|phase-c|',
      'resume-a|resume-b|resume-fe|resume-ai-a|resume-ai-b|resume-ai-live|',
      'ai-configured|ai-unconfigured|ai-live|ai-fe-configured|ai-fe-unconfigured|',
      'probe|other|ct|',
      'ci-a|ci-b|ci-fe|',
      'voice-a|voice-b|voice-fe|',
      'avatar-a|avatar-b|nokey',
    ')-[0-9]+@example\\.com$'
);

-- ---------- DRY RUN: what would be deleted ----------
SELECT CONCAT('mode: ', IF(@apply = 1, 'APPLY (deleting)', 'DRY RUN (nothing deleted)')) AS info;

SELECT REGEXP_REPLACE(email, '[0-9]+@', '<digits>@') AS candidate_pattern, COUNT(*) AS users
FROM cleanup_users GROUP BY candidate_pattern ORDER BY candidate_pattern;

SELECT COUNT(*) AS candidate_users FROM cleanup_users;

SELECT u.email AS preserved_user FROM users u
WHERE u.id NOT IN (SELECT id FROM cleanup_users) ORDER BY u.email;

SELECT 'interview_answers' AS dependent_table, COUNT(*) AS rows_to_delete FROM interview_answers a
  JOIN interview_questions q ON q.id = a.question_id JOIN interview_sessions s ON s.id = q.session_id
  WHERE s.user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'interview_questions', COUNT(*) FROM interview_questions q JOIN interview_sessions s ON s.id = q.session_id
  WHERE s.user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'interview_sessions', COUNT(*) FROM interview_sessions WHERE user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'coach_messages', COUNT(*) FROM coach_messages m JOIN coach_conversations c ON c.id = m.conversation_id
  WHERE c.user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'coach_conversations', COUNT(*) FROM coach_conversations WHERE user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'practice_results', COUNT(*) FROM practice_results r JOIN practice_sessions s ON s.id = r.session_id
  WHERE s.user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'practice_sessions', COUNT(*) FROM practice_sessions WHERE user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'job_match_analyses', COUNT(*) FROM job_match_analyses WHERE user_id IN (SELECT id FROM cleanup_users)
  OR resume_id IN (SELECT id FROM resumes WHERE user_id IN (SELECT id FROM cleanup_users))
UNION ALL SELECT 'resume_analyses', COUNT(*) FROM resume_analyses WHERE resume_id IN (SELECT id FROM resumes WHERE user_id IN (SELECT id FROM cleanup_users))
UNION ALL SELECT 'resume_versions', COUNT(*) FROM resume_versions WHERE resume_id IN (SELECT id FROM resumes WHERE user_id IN (SELECT id FROM cleanup_users))
UNION ALL SELECT 'resumes', COUNT(*) FROM resumes WHERE user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'profile_skills', COUNT(*) FROM profile_skills WHERE profile_id IN (SELECT id FROM profiles WHERE user_id IN (SELECT id FROM cleanup_users))
UNION ALL SELECT 'profiles', COUNT(*) FROM profiles WHERE user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'plan_task_completions', COUNT(*) FROM plan_task_completions WHERE user_id IN (SELECT id FROM cleanup_users)
UNION ALL SELECT 'ai_usage_logs', COUNT(*) FROM ai_usage_logs WHERE user_id IN (SELECT id FROM cleanup_users);

-- ---------- APPLY: every DELETE is guarded by @apply = 1 ----------
START TRANSACTION;

DELETE a FROM interview_answers a
  JOIN interview_questions q ON q.id = a.question_id JOIN interview_sessions s ON s.id = q.session_id
  WHERE @apply = 1 AND s.user_id IN (SELECT id FROM cleanup_users);
DELETE q FROM interview_questions q JOIN interview_sessions s ON s.id = q.session_id
  WHERE @apply = 1 AND s.user_id IN (SELECT id FROM cleanup_users);
DELETE FROM interview_sessions WHERE @apply = 1 AND user_id IN (SELECT id FROM cleanup_users);

DELETE m FROM coach_messages m JOIN coach_conversations c ON c.id = m.conversation_id
  WHERE @apply = 1 AND c.user_id IN (SELECT id FROM cleanup_users);
DELETE FROM coach_conversations WHERE @apply = 1 AND user_id IN (SELECT id FROM cleanup_users);

DELETE r FROM practice_results r JOIN practice_sessions s ON s.id = r.session_id
  WHERE @apply = 1 AND s.user_id IN (SELECT id FROM cleanup_users);
DELETE FROM practice_sessions WHERE @apply = 1 AND user_id IN (SELECT id FROM cleanup_users);

DELETE j FROM job_match_analyses j LEFT JOIN resumes r ON r.id = j.resume_id
  WHERE @apply = 1 AND (j.user_id IN (SELECT id FROM cleanup_users) OR r.user_id IN (SELECT id FROM cleanup_users));
DELETE ra FROM resume_analyses ra JOIN resumes r ON r.id = ra.resume_id
  WHERE @apply = 1 AND r.user_id IN (SELECT id FROM cleanup_users);
DELETE rv FROM resume_versions rv JOIN resumes r ON r.id = rv.resume_id
  WHERE @apply = 1 AND r.user_id IN (SELECT id FROM cleanup_users);
DELETE FROM resumes WHERE @apply = 1 AND user_id IN (SELECT id FROM cleanup_users);

DELETE ps FROM profile_skills ps JOIN profiles p ON p.id = ps.profile_id
  WHERE @apply = 1 AND p.user_id IN (SELECT id FROM cleanup_users);
DELETE FROM profiles WHERE @apply = 1 AND user_id IN (SELECT id FROM cleanup_users);

DELETE FROM plan_task_completions WHERE @apply = 1 AND user_id IN (SELECT id FROM cleanup_users);
DELETE FROM ai_usage_logs WHERE @apply = 1 AND user_id IN (SELECT id FROM cleanup_users);

DELETE FROM users WHERE @apply = 1 AND id IN (SELECT id FROM cleanup_users);

COMMIT;

SELECT COUNT(*) AS users_remaining FROM users;
SELECT email AS remaining_user FROM users ORDER BY email;
-- Ids whose uploads/resumes/<id>/ folders can be removed after an applied run:
SELECT GROUP_CONCAT(id ORDER BY id) AS deleted_user_ids FROM cleanup_users WHERE @apply = 1;

DROP TABLE IF EXISTS cleanup_users;
