-- =====================================================================================
-- V1: CareerAI baseline schema (19 tables), in foreign-key dependency order.
-- Generated from the JPA entities on MySQL 8.0 and checked against the existing development
-- database: identical tables, columns, indexes, unique keys, foreign keys and checks.
-- Existing databases do NOT run this script: they are baselined at version 1 (see
-- docs/deployment-backend.md). A clean database runs it once.
-- =====================================================================================

CREATE TABLE `payment_webhook_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `received_at` datetime(6) NOT NULL,
  `outcome` varchar(40) DEFAULT NULL,
  `event_type` varchar(60) NOT NULL,
  `razorpay_order_id` varchar(64) DEFAULT NULL,
  `razorpay_payment_id` varchar(64) DEFAULT NULL,
  `event_id` varchar(80) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_webhook_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `onboarding_completed` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(100) NOT NULL,
  `password` varchar(100) NOT NULL,
  `email` varchar(255) NOT NULL,
  `plan` enum('FREE','PREMIUM','PRO') NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_usage_logs` (
  `success` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `latency_ms` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `provider` varchar(30) NOT NULL,
  `error_code` varchar(40) DEFAULT NULL,
  `feature` varchar(50) NOT NULL,
  `model` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_usage_user_created` (`user_id`,`created_at`),
  CONSTRAINT `fk_ai_usage_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coach_conversations` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `title` varchar(120) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_coach_conversations_user_updated` (`user_id`,`updated_at`),
  CONSTRAINT `fk_coach_conversations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interview_sessions` (
  `overall_score` int DEFAULT NULL,
  `question_count` int NOT NULL,
  `usage_period_start` date DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resume_id` bigint DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `mode` varchar(10) DEFAULT NULL,
  `difficulty` varchar(20) NOT NULL,
  `provider` varchar(20) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `type` varchar(20) NOT NULL,
  `provider_call_id` varchar(120) DEFAULT NULL,
  `resume_title` varchar(120) DEFAULT NULL,
  `target_role` varchar(120) NOT NULL,
  `focus_topics_json` text,
  `job_description` text,
  `report_json` longtext,
  `voice_transcript_json` longtext,
  PRIMARY KEY (`id`),
  KEY `idx_interview_sessions_user_started` (`user_id`,`started_at`),
  CONSTRAINT `fk_interview_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plan_task_completions` (
  `completed` bit(1) NOT NULL,
  `task_date` date NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `task_key` varchar(80) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_task_completion` (`user_id`,`task_key`,`task_date`),
  CONSTRAINT `fk_plan_task_completions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `practice_sessions` (
  `completed_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `started_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `difficulty` varchar(20) DEFAULT NULL,
  `module` varchar(40) NOT NULL,
  `question_type` varchar(60) DEFAULT NULL,
  `topic` varchar(100) DEFAULT NULL,
  `status` enum('COMPLETED','IN_PROGRESS') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_practice_sessions_user_started` (`user_id`,`started_at`),
  CONSTRAINT `fk_practice_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `profiles` (
  `daily_preparation_minutes` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `preferred_difficulty` varchar(20) DEFAULT NULL,
  `current_status` varchar(50) DEFAULT NULL,
  `experience_level` varchar(50) DEFAULT NULL,
  `career_goal` varchar(100) DEFAULT NULL,
  `target_role` varchar(100) DEFAULT NULL,
  `education` varchar(150) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4ixsj6aqve5pxrbw2u0oyk8bb` (`user_id`),
  CONSTRAINT `fk_profiles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `resumes` (
  `is_primary` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `file_size` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `file_type` varchar(10) DEFAULT NULL,
  `stored_file_name` varchar(80) DEFAULT NULL,
  `title` varchar(120) NOT NULL,
  `content` longtext,
  `extracted_text` longtext,
  `original_file_name` varchar(255) DEFAULT NULL,
  `storage_path` varchar(255) DEFAULT NULL,
  `source_type` enum('BUILDER','UPLOADED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_resumes_user_updated` (`user_id`,`updated_at`),
  CONSTRAINT `fk_resumes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_subscriptions` (
  `currency` varchar(3) NOT NULL,
  `amount_paise` bigint NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paid_at` datetime(6) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  `activated_via` varchar(20) DEFAULT NULL,
  `receipt` varchar(40) NOT NULL,
  `razorpay_order_id` varchar(64) DEFAULT NULL,
  `razorpay_payment_id` varchar(64) DEFAULT NULL,
  `failure_reason` varchar(200) DEFAULT NULL,
  `billing_cycle` enum('ANNUAL','MONTHLY') NOT NULL,
  `plan` enum('FREE','PREMIUM','PRO') NOT NULL,
  `status` enum('ACTIVE','CANCELLED','EXPIRED','FAILED','PENDING') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_subscriptions_receipt` (`receipt`),
  UNIQUE KEY `uk_user_subscriptions_order` (`razorpay_order_id`),
  UNIQUE KEY `uk_user_subscriptions_payment` (`razorpay_payment_id`),
  KEY `idx_user_subscriptions_user_status` (`user_id`,`status`),
  KEY `idx_user_subscriptions_status_expires` (`status`,`expires_at`),
  CONSTRAINT `fk_user_subscriptions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_usage` (
  `period_start` date NOT NULL,
  `usage_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `feature_key` enum('AI_COACH_MESSAGE','AI_TEXT_INTERVIEW','AI_VOICE_INTERVIEW','CODING_RUN','CODING_SUBMIT','MY_PLAN','PERFORMANCE_ANALYTICS','PRACTICE_MODULES','RESUME_AI_IMPROVEMENT','RESUME_ATS_ANALYSIS','RESUME_INTERVIEW_PREP','RESUME_JOB_MATCH','SKILL_GAP','SQL_RUN','SQL_SUBMIT') NOT NULL,
  `period_type` enum('DAILY','MONTHLY') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_usage_period` (`user_id`,`feature_key`,`period_type`,`period_start`),
  KEY `idx_user_usage_user_period` (`user_id`,`period_start`),
  CONSTRAINT `fk_user_usage_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coach_messages` (
  `conversation_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source` varchar(20) DEFAULT NULL,
  `actions_json` text,
  `content` text NOT NULL,
  `role` enum('ASSISTANT','USER') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_coach_messages_conversation` (`conversation_id`,`id`),
  CONSTRAINT `fk_coach_messages_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `coach_conversations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interview_questions` (
  `sequence_number` int NOT NULL,
  `generated_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `difficulty` varchar(20) DEFAULT NULL,
  `question_type` varchar(20) NOT NULL,
  `source` varchar(20) NOT NULL,
  `topic` varchar(80) DEFAULT NULL,
  `intent` varchar(300) DEFAULT NULL,
  `key_points_json` text,
  `question` text NOT NULL,
  `reference_answer` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_question_sequence` (`session_id`,`sequence_number`),
  CONSTRAINT `fk_interview_questions_session` FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `job_match_analyses` (
  `keyword_coverage` int NOT NULL,
  `overall_match_score` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resume_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `education_alignment` varchar(20) NOT NULL,
  `experience_alignment` varchar(20) NOT NULL,
  `engine` varchar(30) NOT NULL,
  `company` varchar(120) DEFAULT NULL,
  `job_title` varchar(120) DEFAULT NULL,
  `details_json` longtext,
  `job_description` longtext NOT NULL,
  `matched_skills_json` text NOT NULL,
  `missing_skills_json` text NOT NULL,
  `recommendations_json` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_job_match_user_created` (`user_id`,`created_at`),
  KEY `fk_job_match_resume` (`resume_id`),
  CONSTRAINT `fk_job_match_resume` FOREIGN KEY (`resume_id`) REFERENCES `resumes` (`id`),
  CONSTRAINT `fk_job_match_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `practice_results` (
  `accuracy` int NOT NULL,
  `correct_answers` int NOT NULL,
  `incorrect_answers` int NOT NULL,
  `score` int NOT NULL,
  `total_questions` int NOT NULL,
  `xp_earned` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `duration_seconds` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `language` varchar(20) DEFAULT NULL,
  `execution_status` varchar(30) DEFAULT NULL,
  `question_id` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKntdmyqls2hnnfa94p65afsb10` (`session_id`),
  CONSTRAINT `fk_practice_results_session` FOREIGN KEY (`session_id`) REFERENCES `practice_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `profile_skills` (
  `profile_id` bigint NOT NULL,
  `skill` varchar(60) NOT NULL,
  `position` int NOT NULL,
  PRIMARY KEY (`profile_id`,`position`),
  CONSTRAINT `fk_profile_skills_profile` FOREIGN KEY (`profile_id`) REFERENCES `profiles` (`id`),
  CONSTRAINT `profile_skills_chk_1` CHECK ((`position` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `resume_analyses` (
  `content_score` int DEFAULT NULL,
  `experience_score` int DEFAULT NULL,
  `formatting_score` int DEFAULT NULL,
  `keyword_score` int DEFAULT NULL,
  `overall_score` int NOT NULL,
  `analyzed_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resume_id` bigint NOT NULL,
  `engine` varchar(30) NOT NULL,
  `result_json` longtext NOT NULL,
  `analysis_type` enum('AI_IMPROVEMENT','ATS','JOB_MATCH') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_resume_analyses_resume_type` (`resume_id`,`analysis_type`,`analyzed_at`),
  CONSTRAINT `fk_resume_analyses_resume` FOREIGN KEY (`resume_id`) REFERENCES `resumes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `resume_versions` (
  `version_number` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resume_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version_name` varchar(120) NOT NULL,
  `content` longtext,
  `extracted_text` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_version_number` (`resume_id`,`version_number`),
  CONSTRAINT `fk_resume_versions_resume` FOREIGN KEY (`resume_id`) REFERENCES `resumes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interview_answers` (
  `score` int NOT NULL,
  `answered_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `source` varchar(20) NOT NULL,
  `answer_text` text NOT NULL,
  `feedback_json` text NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2ydiuxvxwb5ddueavb1mm3my8` (`question_id`),
  CONSTRAINT `fk_interview_answers_question` FOREIGN KEY (`question_id`) REFERENCES `interview_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
