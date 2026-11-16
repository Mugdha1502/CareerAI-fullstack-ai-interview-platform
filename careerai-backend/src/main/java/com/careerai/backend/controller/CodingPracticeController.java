package com.careerai.backend.controller;

import com.careerai.backend.dto.CodeExecuteRequest;
import com.careerai.backend.dto.CodeExecutionResponse;
import com.careerai.backend.dto.CodeSubmitRequest;
import com.careerai.backend.dto.CodeSubmitResponse;
import com.careerai.backend.dto.CodingQuestionResponse;
import com.careerai.backend.service.CodingPracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Coding practice (JWT required; the user always comes from the token).
 * Run Code is never saved; only Submit Solution creates a PracticeResult.
 */
@RestController
@RequestMapping("/api/practice/coding")
@RequiredArgsConstructor
public class CodingPracticeController {

    private final CodingPracticeService codingPracticeService;

    @GetMapping("/question")
    public ResponseEntity<CodingQuestionResponse> question(
        @RequestParam(required = false) String id,
        @RequestParam(required = false) String topic
    ) {
        return ResponseEntity.ok(codingPracticeService.question(id, topic));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("executionAvailable", codingPracticeService.isExecutionAvailable()));
    }

    @PostMapping("/execute")
    public ResponseEntity<CodeExecutionResponse> execute(Authentication authentication, @Valid @RequestBody CodeExecuteRequest request) {
        return ResponseEntity.ok(codingPracticeService.run(authentication.getName(), request));
    }

    @PostMapping("/submit")
    public ResponseEntity<CodeSubmitResponse> submit(Authentication authentication, @Valid @RequestBody CodeSubmitRequest request) {
        return ResponseEntity.ok(codingPracticeService.submit(authentication.getName(), request));
    }
}
