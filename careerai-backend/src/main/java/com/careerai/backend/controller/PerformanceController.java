package com.careerai.backend.controller;

import com.careerai.backend.dto.PerformanceHistoryResponse;
import com.careerai.backend.dto.PerformanceSummaryResponse;
import com.careerai.backend.dto.PracticeHistoryItem;
import com.careerai.backend.dto.SkillPerformanceResponse;
import com.careerai.backend.service.CurrentUserService;
import com.careerai.backend.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Performance of the authenticated user (JWT required; user taken from the token). */
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final CurrentUserService currentUserService;
    private final PerformanceService performanceService;

    @GetMapping("/summary")
    public ResponseEntity<PerformanceSummaryResponse> summary(Authentication authentication) {
        return ResponseEntity.ok(performanceService.getSummary(currentUserService.require(authentication.getName())));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PerformanceHistoryResponse>> history(
        Authentication authentication,
        @RequestParam(defaultValue = "" + PerformanceService.DEFAULT_HISTORY_DAYS) int days
    ) {
        return ResponseEntity.ok(performanceService.getHistory(currentUserService.require(authentication.getName()), days));
    }

    @GetMapping("/skills")
    public ResponseEntity<List<SkillPerformanceResponse>> skills(Authentication authentication) {
        return ResponseEntity.ok(performanceService.getSkillPerformance(currentUserService.require(authentication.getName())));
    }

    @GetMapping("/recent-activity")
    public ResponseEntity<List<PracticeHistoryItem>> recentActivity(Authentication authentication) {
        return ResponseEntity.ok(performanceService.getRecentActivity(currentUserService.require(authentication.getName())));
    }
}
