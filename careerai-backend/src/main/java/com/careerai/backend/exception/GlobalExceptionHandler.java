package com.careerai.backend.exception;

import com.careerai.backend.ai.AiProviderException;
import com.careerai.backend.ai.AiRateLimitException;
import com.careerai.backend.ai.AiResponseInvalidException;
import com.careerai.backend.ai.AiUnavailableException;
import com.careerai.backend.billing.BillingException;
import com.careerai.backend.billing.PlanLimitExceededException;
import com.careerai.backend.execution.ExecutionProviderException;
import com.careerai.backend.practice.PracticeExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts exceptions into small JSON bodies such as
 * { "message": "Email already registered" }.
 * Stack traces are logged on the server and never sent to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailExists(EmailAlreadyExistsException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<Map<String, Object>> handlePasswordMismatch(PasswordMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** Same body for every account (no enumeration); Retry-After tells clients when to try again. */
    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<Map<String, Object>> handleLoginRateLimited(LoginRateLimitedException ex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", ex.getMessage());
        response.put("code", "LOGIN_RATE_LIMITED");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
            .body(response);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PracticeSessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePracticeSessionNotFound(PracticeSessionNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PracticeResultAlreadySavedException.class)
    public ResponseEntity<Map<String, Object>> handleResultAlreadySaved(PracticeResultAlreadySavedException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PlanTaskNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePlanTaskNotFound(PlanTaskNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidStateException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResumeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResumeNotFound(ResumeNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ InvalidResumeFileException.class, InvalidResumeRequestException.class })
    public ResponseEntity<Map<String, Object>> handleInvalidResume(RuntimeException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResumeFileTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handleResumeTooLarge(ResumeFileTooLargeException ex) {
        return body(HttpStatus.CONTENT_TOO_LARGE, ex.getMessage());
    }

    /** Servlet multipart limit hit before the controller ran. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.CONTENT_TOO_LARGE, "Resume file is too large. Maximum size is 5 MB.");
    }

    @ExceptionHandler(ResumeTextExtractionException.class)
    public ResponseEntity<Map<String, Object>> handleExtraction(ResumeTextExtractionException ex) {
        return body(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    /* ---------- AI: safe messages plus a code the frontend uses to choose its fallback ---------- */

    @ExceptionHandler(InvalidAiRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAiRequest(InvalidAiRequestException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), "AI_INVALID_REQUEST");
    }

    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAiUnavailable(AiUnavailableException ex) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "AI_UNAVAILABLE");
    }

    @ExceptionHandler(AiRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleAiRateLimit(AiRateLimitException ex) {
        return body(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "AI_RATE_LIMITED");
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<Map<String, Object>> handleAiProvider(AiProviderException ex) {
        HttpStatus status = switch (ex.reason()) {
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case BUSY, CONFIGURATION -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return body(status, ex.getMessage(), "AI_" + ex.reason().name());
    }

    @ExceptionHandler(AiResponseInvalidException.class)
    public ResponseEntity<Map<String, Object>> handleAiResponseInvalid(AiResponseInvalidException ex) {
        // The validation detail is logged by AiUsageService; it is not sent to the client.
        return body(HttpStatus.BAD_GATEWAY, "The AI response could not be used. Please try again.", "AI_INVALID_RESPONSE");
    }

    /* ---------- Plans and billing ---------- */

    /** 429 PLAN_LIMIT_REACHED with what the frontend needs for an upgrade prompt. */
    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimit(PlanLimitExceededException ex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "PLAN_LIMIT_REACHED");
        response.put("message", ex.getMessage());
        response.put("feature", ex.feature().name());
        response.put("featureLabel", ex.feature().label());
        response.put("plan", ex.plan().name());
        response.put("limit", ex.limit());
        response.put("used", ex.used());
        response.put("period", ex.period().name());
        response.put("proLimit", ex.proLimit());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<Map<String, Object>> handleBilling(BillingException ex) {
        return body(ex.status(), ex.getMessage(), ex.code());
    }

    /* ---------- Coding / SQL practice: safe message + category code ---------- */

    @ExceptionHandler(PracticeExecutionException.class)
    public ResponseEntity<Map<String, Object>> handlePracticeExecution(PracticeExecutionException ex) {
        return body(ex.status(), ex.getMessage(), ex.code());
    }

    @ExceptionHandler(ExecutionProviderException.class)
    public ResponseEntity<Map<String, Object>> handleExecutionProvider(ExecutionProviderException ex) {
        return switch (ex.reason()) {
            case TIMEOUT -> body(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage(), "EXECUTION_TIMEOUT");
            case LIMIT_REACHED -> body(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "EXECUTION_PROVIDER_LIMIT_REACHED");
            default -> body(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "EXECUTION_PROVIDER_UNAVAILABLE");
        };
    }

    /** Avatar problems never affect the voice interview; the code says whether a retry can help. */
    @ExceptionHandler(AvatarUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAvatarUnavailable(AvatarUnavailableException ex) {
        return body(ex.status(), ex.getMessage(), ex.code());
    }

    /** @Validated path/query parameter failures (e.g. an invalid plan task key). */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
            .map(jakarta.validation.ConstraintViolation::getMessage)
            .findFirst()
            .orElse("Invalid request");
        return body(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(InvalidPracticeResultException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPracticeResult(InvalidPracticeResultException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Bean Validation failures: first message plus every field error. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        String message = errors.isEmpty() ? "Invalid request" : errors.values().iterator().next();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON");
    }

    /** Covers the race where two registrations with the same email pass the existence check together. */
    /** A path or query value of the wrong type (e.g. /api/interviews/abc): the client's mistake, not a server error. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
        log.warn("Data integrity violation: {}", cause);

        if (cause.contains("uk_users_email")) {
            return body(HttpStatus.CONFLICT, "Email already registered");
        }

        return body(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        // Keep Spring's own status for framework errors (404 no route, 405 wrong method, ...).
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = errorResponse.getBody().getDetail();
            return body(status, detail != null ? detail : "Request could not be processed");
        }

        log.error("Unexpected error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatusCode status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatusCode status, String message, String code) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("code", code);
        return ResponseEntity.status(status).body(response);
    }
}
