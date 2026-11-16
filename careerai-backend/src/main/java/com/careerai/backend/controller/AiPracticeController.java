package com.careerai.backend.controller;

import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.dto.AiEvaluationRequest;
import com.careerai.backend.dto.AiEvaluationResponse;
import com.careerai.backend.dto.AiQuestionRequest;
import com.careerai.backend.dto.AiQuestionsResponse;
import com.careerai.backend.dto.SystemDesignEvaluationRequest;
import com.careerai.backend.dto.SystemDesignEvaluationResponse;
import com.careerai.backend.service.AiPracticeEvaluationService;
import com.careerai.backend.service.AiPracticeQuestionService;
import com.careerai.backend.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI practice endpoints (JWT required). Thin: validation and AI calls happen in services;
 * this controller never talks to a provider directly.
 */
@RestController
@RequestMapping("/api/ai/practice")
@RequiredArgsConstructor
public class AiPracticeController {

    private final CurrentUserService currentUserService;
    private final AiPracticeQuestionService questionService;
    private final AiPracticeEvaluationService evaluationService;
    private final AiProvider aiProvider;

    /** Lets the frontend skip AI calls (and show the fallback label) when no provider is configured. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("available", aiProvider.isConfigured(), "provider", aiProvider.name()));
    }

    @PostMapping("/questions")
    public ResponseEntity<AiQuestionsResponse> questions(Authentication authentication, @Valid @RequestBody AiQuestionRequest request) {
        return ResponseEntity.ok(questionService.generate(currentUserService.require(authentication.getName()), request));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<AiEvaluationResponse> evaluate(Authentication authentication, @Valid @RequestBody AiEvaluationRequest request) {
        return ResponseEntity.ok(evaluationService.evaluateAnswer(currentUserService.require(authentication.getName()), request));
    }

    @PostMapping("/system-design/evaluate")
    public ResponseEntity<SystemDesignEvaluationResponse> evaluateSystemDesign(
        Authentication authentication,
        @Valid @RequestBody SystemDesignEvaluationRequest request
    ) {
        return ResponseEntity.ok(evaluationService.evaluateSystemDesign(currentUserService.require(authentication.getName()), request));
    }
}
