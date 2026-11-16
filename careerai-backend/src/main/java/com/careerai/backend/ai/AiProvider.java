package com.careerai.backend.ai;

/**
 * A text-generation provider that returns strict JSON. GeminiAiProvider is the current
 * implementation; another provider can implement this interface without changes to
 * the services that use it.
 *
 * Only backend services call providers. Controllers never do, and the browser never
 * talks to a provider or sees its key.
 */
public interface AiProvider {

    /** Provider id, e.g. "gemini". */
    String name();

    /** Model identifier used for requests (shown to the user as metadata only). */
    String model();

    /** False when the provider has no API key; callers then answer 503 without calling it. */
    boolean isConfigured();

    /**
     * Sends the request and returns the model's JSON text (not yet parsed or validated).
     *
     * @throws AiUnavailableException when the provider is not configured
     * @throws AiProviderException    on timeouts, HTTP errors, blocked or empty responses
     */
    String generateJson(AiRequest request);
}
