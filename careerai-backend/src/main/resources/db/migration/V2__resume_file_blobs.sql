-- =====================================================================================
-- V2: storage for the "database" resume storage provider (app.storage.resume-provider=database).
-- Additive only. IF NOT EXISTS: databases where Hibernate (ddl-auto=update) already created this
-- table with the same definition keep it, and its rows, unchanged.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `resume_file_blobs` (
  `created_at` datetime(6) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `storage_path` varchar(255) NOT NULL,
  `content` longblob NOT NULL,
  PRIMARY KEY (`storage_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
