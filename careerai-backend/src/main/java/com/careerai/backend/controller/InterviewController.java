package com.careerai.backend.controller;

import com.careerai.backend.dto.InterviewAnswerRequest;
import com.careerai.backend.dto.InterviewAnswerResponse;
import com.careerai.backend.dto.InterviewReportResponse;
import com.careerai.backend.dto.InterviewSessionResponse;
import com.careerai.backend.dto.InterviewStartRequest;
import com.careerai.backend.dto.InterviewStatsResponse;
import com.careerai.backend.dto.InterviewSummaryResponse;
import com.careerai.backend.dto.VoiceInterviewResultResponse;
import com.careerai.backend.dto.VoiceTranscriptRequest;
import com.careerai.backend.entity.User;
import com.careerai.backend.service.CurrentUserService;
import com.careerai.backend.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Text-based AI interview (JWT required). Another user's session ids give 404. */
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final CurrentUserService currentUserService;
    private final InterviewService interviewService;

    @PostMapping
    public ResponseEntity<InterviewSessionResponse> start(Authentication authentication, @Valid @RequestBody InterviewStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(interviewService.start(user(authentication), request));
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<InterviewAnswerResponse> answer(
        Authentication authentication,
        @PathVariable Long id,
        @Valid @RequestBody InterviewAnswerRequest request
    ) {
        return ResponseEntity.ok(interviewService.answer(user(authentication), id, request));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<InterviewReportResponse> complete(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(interviewService.complete(user(authentication), id));
    }

    /**
     * Final transcript of a voice (Vapi) call. Idempotent: repeated submissions return the stored
     * result. Scores are computed on the server; the client sends only roles and text.
     */
    @PostMapping("/{id}/voice-transcript")
    public ResponseEntity<VoiceInterviewResultResponse> voiceTranscript(
        Authentication authentication,
        @PathVariable Long id,
        @Valid @RequestBody VoiceTranscriptRequest request
    ) {
        return ResponseEntity.ok(interviewService.ingestVoiceTranscript(user(authentication), id, request));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<InterviewReportResponse> report(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(interviewService.report(user(authentication), id));
    }

    @GetMapping("/history")
    public ResponseEntity<List<InterviewSummaryResponse>> history(Authentication authentication) {
        return ResponseEntity.ok(interviewService.history(user(authentication)));
    }

    /** Interview performance as a separate data source (practice results are not modified). */
    @GetMapping("/stats")
    public ResponseEntity<InterviewStatsResponse> stats(Authentication authentication) {
        return ResponseEntity.ok(interviewService.stats(user(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewSessionResponse> get(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(interviewService.get(user(authentication), id));
    }

    private User user(Authentication authentication) {
        return currentUserService.require(authentication.getName());
    }
}
