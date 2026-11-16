package com.careerai.backend.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Razorpay Payment Gateway over its REST API (Orders + Payments), using HTTP Basic auth with
 * key id : key secret. Signatures follow Razorpay's documented HMAC-SHA256 schemes.
 *
 * RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET never leave this class: they are not logged,
 * not returned, and only used for Basic auth and HMAC verification. Without credentials the app
 * still starts and checkout answers 503 PAYMENT_GATEWAY_UNAVAILABLE.
 */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final String baseUrl;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RazorpayPaymentGateway(
        @Value("${app.billing.razorpay.base-url:https://api.razorpay.com}") String baseUrl,
        @Value("${app.billing.razorpay.key-id:}") String keyId,
        @Value("${app.billing.razorpay.key-secret:}") String keySecret,
        @Value("${app.billing.razorpay.webhook-secret:}") String webhookSecret,
        @Value("${app.billing.razorpay.timeout-seconds:15}") int timeoutSeconds,
        ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.razorpay.com" : baseUrl.trim().replaceAll("/+$", "");
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(Math.max(timeoutSeconds, 1), 10))).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(timeoutSeconds, 1)));
        this.restClient = RestClient.builder().requestFactory(factory).build();

        if (!isConfigured()) {
            log.warn("RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET are not set: plans are shown, checkout answers 503.");
        } else {
            log.info("Razorpay gateway configured ({} mode).", this.keyId.startsWith("rzp_live_") ? "LIVE" : "TEST");
        }
        if (!isWebhookConfigured()) {
            log.warn("RAZORPAY_WEBHOOK_SECRET is not set: Razorpay webhooks are rejected.");
        }
    }

    @Override
    public boolean isConfigured() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    @Override
    public boolean isWebhookConfigured() {
        return !webhookSecret.isEmpty();
    }

    @Override
    public String publicKeyId() {
        return keyId;
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode order = call(() -> restClient.post().uri(baseUrl + "/v1/orders")
            .header("Authorization", basicAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve().body(String.class), "create order");
        String id = order.path("id").asString("");
        if (id.isBlank()) {
            log.warn("RAZORPAY_BAD_RESPONSE: order response without id.");
            throw BillingException.gatewayUnavailable();
        }
        return new GatewayOrder(id, order.path("amount").asLong(-1), order.path("currency").asString(""), order.path("status").asString(""));
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        requireConfigured();
        JsonNode payment = call(() -> restClient.get().uri(baseUrl + "/v1/payments/{id}", paymentId)
            .header("Authorization", basicAuth())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve().body(String.class), "fetch payment");
        return new GatewayPayment(payment.path("id").asString(""), payment.path("order_id").asString(""),
            payment.path("amount").asLong(-1), payment.path("currency").asString(""), payment.path("status").asString(""));
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        if (keySecret.isEmpty() || orderId == null || paymentId == null || signature == null) return false;
        return constantTimeEquals(hmacHex(keySecret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)), signature);
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        if (webhookSecret.isEmpty() || rawBody == null || signature == null) return false;
        return constantTimeEquals(hmacHex(webhookSecret, rawBody), signature);
    }

    static String hmacHex(String secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    static boolean constantTimeEquals(String expectedHex, String actual) {
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8), actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private interface Call {
        String run();
    }

    private JsonNode call(Call call, String operation) {
        try {
            String response = call.run();
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401) log.warn("Razorpay authentication failed ({}): check RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET.", operation);
            else log.warn("RAZORPAY_HTTP_ERROR: {} failed with HTTP {}: {}", operation, status, safe(ex.getResponseBodyAsString()));
            throw BillingException.gatewayUnavailable();
        } catch (RestClientException ex) {
            log.warn("RAZORPAY_UNREACHABLE: {} failed: {}", operation, ex.getClass().getSimpleName());
            throw BillingException.gatewayUnavailable();
        } catch (BillingException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("RAZORPAY_BAD_RESPONSE: {} returned unreadable data: {}", operation, ex.getClass().getSimpleName());
            throw BillingException.gatewayUnavailable();
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) throw BillingException.gatewayUnavailable();
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
    }

    private String safe(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ");
        if (!keySecret.isEmpty()) value = value.replace(keySecret, "[redacted]");
        if (!webhookSecret.isEmpty()) value = value.replace(webhookSecret, "[redacted]");
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }
}
