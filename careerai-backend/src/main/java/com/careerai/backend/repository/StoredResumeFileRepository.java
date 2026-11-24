package com.careerai.backend.repository;

import com.careerai.backend.entity.StoredResumeFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredResumeFileRepository extends JpaRepository<StoredResumeFile, String> {
}
