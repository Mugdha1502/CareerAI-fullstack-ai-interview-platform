package com.careerai.backend.controller;

import com.careerai.backend.dto.InterviewPreparationRequest;
import com.careerai.backend.dto.JobMatchRequest;
import com.careerai.backend.dto.JobMatchResponse;
import com.careerai.backend.dto.ResumeAnalysisResponse;
import com.careerai.backend.dto.ResumeCreateRequest;
import com.careerai.backend.dto.InterviewPreparationResponse;
import com.careerai.backend.dto.ResumeImprovementRequest;
import com.careerai.backend.dto.ResumeImprovementResponse;
import com.careerai.backend.dto.ResumeResponse;
import com.careerai.backend.dto.ResumeUpdateRequest;
import com.careerai.backend.dto.ResumeVersionRequest;
import com.careerai.backend.dto.ResumeVersionResponse;
import com.careerai.backend.entity.User;
import com.careerai.backend.service.ATSAnalysisService;
import com.careerai.backend.service.CurrentUserService;
import com.careerai.backend.service.JobMatchService;
import com.careerai.backend.service.ResumeImprovementService;
import com.careerai.backend.service.ResumeInterviewPreparationService;
import com.careerai.backend.service.ResumeService;
import com.careerai.backend.service.ResumeService.ResumeFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Resume endpoints. All require a JWT; the user always comes from the token, never from
 * the request. Analysis logic lives in services (rule-based now, AI-assisted later).
 */
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final CurrentUserService currentUserService;
    private final ResumeService resumeService;
    private final ATSAnalysisService atsAnalysisService;
    private final JobMatchService jobMatchService;
    private final ResumeImprovementService improvementService;
    private final ResumeInterviewPreparationService interviewPreparationService;


    /* ---------- Resumes ---------- */

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeResponse> upload(
        Authentication authentication,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "title", required = false) String title
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resumeService.upload(user(authentication), file, title));
    }

    @PostMapping
    public ResponseEntity<ResumeResponse> create(Authentication authentication, @Valid @RequestBody ResumeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resumeService.createBuilderResume(user(authentication), request));
    }

    @GetMapping
    public ResponseEntity<List<ResumeResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(resumeService.list(user(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResumeResponse> get(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(resumeService.get(user(authentication), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResumeResponse> update(
        Authentication authentication,
        @PathVariable Long id,
        @Valid @RequestBody ResumeUpdateRequest request
    ) {
        return ResponseEntity.ok(resumeService.update(user(authentication), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        resumeService.delete(user(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/primary")
    public ResponseEntity<ResumeResponse> setPrimary(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(resumeService.setPrimary(user(authentication), id));
    }

    /** Authenticated download of the caller's own uploaded file (never a public static path). */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(Authentication authentication, @PathVariable Long id) {
        ResumeFile file = resumeService.loadFile(user(authentication), id);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(file.resource());
    }


    /* ---------- Versions ---------- */

    @PostMapping("/{id}/versions")
    public ResponseEntity<ResumeVersionResponse> createVersion(
        Authentication authentication,
        @PathVariable Long id,
        @Valid @RequestBody(required = false) ResumeVersionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resumeService.createVersion(user(authentication), id, request));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<ResumeVersionResponse>> listVersions(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(resumeService.listVersions(user(authentication), id));
    }

    @PostMapping("/{id}/versions/{versionId}/duplicate")
    public ResponseEntity<ResumeVersionResponse> duplicateVersion(
        Authentication authentication,
        @PathVariable Long id,
        @PathVariable Long versionId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resumeService.duplicateVersion(user(authentication), id, versionId));
    }

    @PutMapping("/{id}/versions/{versionId}")
    public ResponseEntity<ResumeVersionResponse> renameVersion(
        Authentication authentication,
        @PathVariable Long id,
        @PathVariable Long versionId,
        @Valid @RequestBody ResumeVersionRequest request
    ) {
        return ResponseEntity.ok(resumeService.renameVersion(user(authentication), id, versionId, request));
    }

    @DeleteMapping("/{id}/versions/{versionId}")
    public ResponseEntity<Void> deleteVersion(Authentication authentication, @PathVariable Long id, @PathVariable Long versionId) {
        resumeService.deleteVersion(user(authentication), id, versionId);
        return ResponseEntity.noContent().build();
    }


    /* ---------- ATS analysis (rule-based) ---------- */

    @PostMapping("/{id}/ats-analysis")
    public ResponseEntity<ResumeAnalysisResponse> analyzeAts(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(atsAnalysisService.analyze(user(authentication), id));
    }

    /** 204 when the resume has not been analysed yet. */
    @GetMapping("/{id}/ats-analysis/latest")
    public ResponseEntity<ResumeAnalysisResponse> latestAts(Authentication authentication, @PathVariable Long id) {
        return atsAnalysisService.latest(user(authentication), id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }


    /* ---------- Job match (rule-based) ---------- */

    @PostMapping("/{id}/job-match")
    public ResponseEntity<JobMatchResponse> jobMatch(
        Authentication authentication,
        @PathVariable Long id,
        @Valid @RequestBody JobMatchRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobMatchService.analyze(user(authentication), id, request));
    }

    @GetMapping("/job-matches")
    public ResponseEntity<List<JobMatchResponse>> jobMatches(Authentication authentication) {
        return ResponseEntity.ok(jobMatchService.list(user(authentication)));
    }

    @GetMapping("/job-matches/{matchId}")
    public ResponseEntity<JobMatchResponse> jobMatchDetail(Authentication authentication, @PathVariable Long matchId) {
        return ResponseEntity.ok(jobMatchService.get(user(authentication), matchId));
    }


    /* ---------- Improvement (AI not connected yet) ---------- */

    @PostMapping("/improve")
    public ResponseEntity<ResumeImprovementResponse> improve(Authentication authentication, @Valid @RequestBody ResumeImprovementRequest request) {
        return ResponseEntity.ok(improvementService.improve(user(authentication), request));
    }


    /* ---------- Interview preparation bridge ---------- */

    @PostMapping("/{id}/interview-preparation")
    public ResponseEntity<InterviewPreparationResponse> interviewPreparation(
        Authentication authentication,
        @PathVariable Long id,
        @Valid @RequestBody(required = false) InterviewPreparationRequest request
    ) {
        return ResponseEntity.ok(interviewPreparationService.prepare(user(authentication), id, request));
    }


    private User user(Authentication authentication) {
        return currentUserService.require(authentication.getName());
    }
}
