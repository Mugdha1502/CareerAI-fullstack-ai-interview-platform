package com.careerai.backend.controller;

import com.careerai.backend.dto.PlanResponse;
import com.careerai.backend.service.CurrentUserService;
import com.careerai.backend.service.PlanService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Personalized plan of the authenticated user; completion is stored per user and day. */
@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
@Validated
public class PlanController {

    private static final String TASK_KEY = "^[a-z0-9-]{1,80}$";

    private final CurrentUserService currentUserService;
    private final PlanService planService;

    @GetMapping("/me")
    public ResponseEntity<PlanResponse> myPlan(Authentication authentication) {
        return ResponseEntity.ok(planService.getPlan(currentUserService.require(authentication.getName())));
    }

    @PutMapping("/tasks/{taskKey}/complete")
    public ResponseEntity<PlanResponse> complete(
        Authentication authentication,
        @PathVariable @Pattern(regexp = TASK_KEY, message = "Invalid task key") String taskKey
    ) {
        return ResponseEntity.ok(planService.setTaskCompleted(currentUserService.require(authentication.getName()), taskKey, true));
    }

    @PutMapping("/tasks/{taskKey}/incomplete")
    public ResponseEntity<PlanResponse> incomplete(
        Authentication authentication,
        @PathVariable @Pattern(regexp = TASK_KEY, message = "Invalid task key") String taskKey
    ) {
        return ResponseEntity.ok(planService.setTaskCompleted(currentUserService.require(authentication.getName()), taskKey, false));
    }
}
