package com.careerai.backend.repository;

import com.careerai.backend.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
