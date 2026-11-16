package com.careerai.backend.controller;

import com.careerai.backend.dto.PracticeHistoryItem;
import com.careerai.backend.dto.PracticeHistoryResponse;
import com.careerai.backend.dto.PracticeResultRequest;
import com.careerai.backend.dto.PracticeSessionRequest;
import com.careerai.backend.dto.PracticeSessionResponse;
import com.careerai.backend.service.PracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Practice sessions and results. All endpoints require a valid JWT
 * (SecurityConfig: anyRequest().authenticated()); the user comes from the token.
 */
@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;

    @PostMapping("/sessions")
    public ResponseEntity<PracticeSessionResponse> startSession(
        Authentication authentication,
        @Valid @RequestBody PracticeSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(practiceService.startSession(authentication.getName(), request));
    }

    @PutMapping("/sessions/{id}/complete")
    public ResponseEntity<PracticeSessionResponse> completeSession(
        Authentication authentication,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(practiceService.completeSession(authentication.getName(), id));
    }

    @PostMapping("/results")
    public ResponseEntity<PracticeHistoryItem> saveResult(
        Authentication authentication,
        @Valid @RequestBody PracticeResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(practiceService.saveResult(authentication.getName(), request));
    }

    @GetMapping("/history")
    public ResponseEntity<PracticeHistoryResponse> getHistory(
        Authentication authentication,
        @RequestParam(required = false) String module,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(practiceService.getHistory(authentication.getName(), module, page, size));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<PracticeHistoryItem>> getRecent(Authentication authentication) {
        return ResponseEntity.ok(practiceService.getRecent(authentication.getName()));
    }
}
