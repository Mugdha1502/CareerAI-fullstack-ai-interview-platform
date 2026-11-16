package com.careerai.backend.controller;

import com.careerai.backend.dto.SqlQueryRequest;
import com.careerai.backend.dto.SqlQueryResponse;
import com.careerai.backend.dto.SqlQuestionResponse;
import com.careerai.backend.dto.SqlSubmitRequest;
import com.careerai.backend.dto.SqlSubmitResponse;
import com.careerai.backend.service.SqlPracticeService;
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

/**
 * SQL practice in a disposable sandbox (JWT required; the user always comes from the token).
 * Run Query is never saved; only Submit Query creates a PracticeResult.
 */
@RestController
@RequestMapping("/api/practice/sql")
@RequiredArgsConstructor
public class SqlPracticeController {

    private final SqlPracticeService sqlPracticeService;

    @GetMapping("/question")
    public ResponseEntity<SqlQuestionResponse> question(
        @RequestParam(required = false) String id,
        @RequestParam(required = false) String dataset,
        @RequestParam(required = false) String topic
    ) {
        return ResponseEntity.ok(sqlPracticeService.question(id, dataset, topic));
    }

    @PostMapping("/execute")
    public ResponseEntity<SqlQueryResponse> execute(Authentication authentication, @Valid @RequestBody SqlQueryRequest request) {
        return ResponseEntity.ok(sqlPracticeService.run(authentication.getName(), request));
    }

    @PostMapping("/submit")
    public ResponseEntity<SqlSubmitResponse> submit(Authentication authentication, @Valid @RequestBody SqlSubmitRequest request) {
        return ResponseEntity.ok(sqlPracticeService.submit(authentication.getName(), request));
    }
}
