package com.careerai.backend.ai;

/** No AI provider is configured (e.g. GEMINI_API_KEY is not set). Reported as 503. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException() {
        super("AI service is not available right now. Practice questions from the CareerAI question bank can still be used.");
    }
}
