package com.careerai.backend.billing;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RazorpayPaymentGateway against a local fake Razorpay API (no real Razorpay, no money). */
class RazorpayPaymentGatewayTest {

    private static final String KEY_ID = "rzp_test_unitKey";
    private static final String KEY_SECRET = "unit_key_secret";
    private static final String WEBHOOK_SECRET = "unit_webhook_secret";

    private HttpServer server;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private volatile int forcedStatus = 0;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] raw = exchange.getRequestBody().readAllBytes();
            if (raw.length > 0) bodies.add(JsonMapper.builder().build().readTree(raw));
            String path = exchange.getRequestURI().getPath();
            String response;
            int status = 200;
            if (forcedStatus != 0) {
                status = forcedStatus;
                response = "{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"nope\"}}";
            } else if (path.equals("/v1/orders")) {
                JsonNode body = bodies.getLast();
                response = "{\"id\":\"order_unit1\",\"entity\":\"order\",\"amount\":" + body.path("amount").asLong() + ",\"currency\":\"" + body.path("currency").asString()
                    + "\",\"receipt\":\"" + body.path("receipt").asString() + "\",\"status\":\"created\"}";
            } else if (path.equals("/v1/payments/pay_unit1")) {
                response = "{\"id\":\"pay_unit1\",\"entity\":\"payment\",\"amount\":29900,\"currency\":\"INR\",\"status\":\"captured\",\"order_id\":\"order_unit1\"}";
            } else {
                status = 404;
                response = "{}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private RazorpayPaymentGateway gateway(String id, String secret, String webhook) {
        return new RazorpayPaymentGateway("http://127.0.0.1:" + server.getAddress().getPort(), id, secret, webhook, 5, JsonMapper.builder().build());
    }

    @Test
    void createsOrdersWithBasicAuthAndPaise() {
        PaymentGateway.GatewayOrder order = gateway(KEY_ID, KEY_SECRET, WEBHOOK_SECRET).createOrder(29900, "INR", "cai-1-abcdef", Map.of("plan", "PRO"));
        assertThat(order.id()).isEqualTo("order_unit1");
        assertThat(order.amount()).isEqualTo(29900);
        assertThat(order.currency()).isEqualTo("INR");
        assertThat(authHeaders.getFirst()).isEqualTo("Basic " + Base64.getEncoder().encodeToString((KEY_ID + ":" + KEY_SECRET).getBytes(StandardCharsets.UTF_8)));
        JsonNode body = bodies.getFirst();
        assertThat(body.path("amount").asLong()).isEqualTo(29900);
        assertThat(body.path("currency").asString()).isEqualTo("INR");
        assertThat(body.path("receipt").asString()).isEqualTo("cai-1-abcdef");
        assertThat(body.toString()).doesNotContain(KEY_SECRET);
    }

    @Test
    void fetchesPayments() {
        PaymentGateway.GatewayPayment payment = gateway(KEY_ID, KEY_SECRET, WEBHOOK_SECRET).fetchPayment("pay_unit1");
        assertThat(payment.orderId()).isEqualTo("order_unit1");
        assertThat(payment.status()).isEqualTo("captured");
        assertThat(payment.amount()).isEqualTo(29900);
    }

    @Test
    void gatewayErrorsBecomeUnavailable() {
        forcedStatus = 401;
        assertThatThrownBy(() -> gateway(KEY_ID, KEY_SECRET, WEBHOOK_SECRET).createOrder(29900, "INR", "r", Map.of()))
            .isInstanceOf(BillingException.class)
            .satisfies(ex -> assertThat(((BillingException) ex).code()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE"));
        forcedStatus = 500;
        assertThatThrownBy(() -> gateway(KEY_ID, KEY_SECRET, WEBHOOK_SECRET).fetchPayment("pay_unit1")).isInstanceOf(BillingException.class);
        RazorpayPaymentGateway unreachable = new RazorpayPaymentGateway("http://127.0.0.1:1", KEY_ID, KEY_SECRET, WEBHOOK_SECRET, 1, JsonMapper.builder().build());
        assertThatThrownBy(() -> unreachable.createOrder(29900, "INR", "r", Map.of())).isInstanceOf(BillingException.class);
    }

    @Test
    void notConfiguredWithoutCredentials() {
        RazorpayPaymentGateway missing = gateway("", "", "");
        assertThat(missing.isConfigured()).isFalse();
        assertThat(missing.isWebhookConfigured()).isFalse();
        assertThatThrownBy(() -> missing.createOrder(29900, "INR", "r", Map.of())).isInstanceOf(BillingException.class);
        assertThat(authHeaders).isEmpty();
        assertThat(missing.verifyWebhookSignature("{}".getBytes(), "x")).isFalse();
        assertThat(missing.verifyPaymentSignature("order_1", "pay_1", "x")).isFalse();
    }

    @Test
    void signaturesFollowRazorpayHmacScheme() throws Exception {
        RazorpayPaymentGateway gateway = gateway(KEY_ID, KEY_SECRET, WEBHOOK_SECRET);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = java.util.HexFormat.of().formatHex(mac.doFinal("order_unit1|pay_unit1".getBytes(StandardCharsets.UTF_8)));
        assertThat(gateway.verifyPaymentSignature("order_unit1", "pay_unit1", expected)).isTrue();
        assertThat(gateway.verifyPaymentSignature("order_unit1", "pay_unit1", expected.toUpperCase())).isTrue();
        assertThat(gateway.verifyPaymentSignature("order_unit2", "pay_unit1", expected)).isFalse();
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String webhook = RazorpayPaymentGateway.hmacHex(WEBHOOK_SECRET, body);
        assertThat(gateway.verifyWebhookSignature(body, webhook)).isTrue();
        assertThat(gateway.verifyWebhookSignature("{\"event\": \"payment.captured\"}".getBytes(StandardCharsets.UTF_8), webhook)).isFalse();
        // The key secret cannot sign webhooks (separate secrets).
        assertThat(gateway.verifyWebhookSignature(body, RazorpayPaymentGateway.hmacHex(KEY_SECRET, body))).isFalse();
    }
}
