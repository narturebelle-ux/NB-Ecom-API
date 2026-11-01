package com.narturebelle.controller;

import com.narturebelle.repository.EntitlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On real Tomcat over a raw socket: MockMvc doesn't decode paths the same way, and URI clients can rewrite them.
 * No spelling of an /internal path may reach an endpoint without the private token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "internal.api-token=test-internal-token")
class InternalPathBypassTest {

    private static final String CONSENT = "v1/users/1/consent-status";
    private static final long ORDER = 9301L;
    private static final String EVENT = """
            {"eventId":"payment-succeeded:%d","schemaVersion":1,"orderId":%d,"userId":8301,"planCode":"STANDARD",
             "months":9,"price":9999.00,"paidAt":"2026-11-01T10:15:30.123456","expiresAt":"2027-11-01T10:15:30.123456",
             "sessions":[{"professionalType":"GYNECOLOGIST","sessionCount":5}]}""".formatted(ORDER, ORDER);

    @LocalServerPort
    private int port;

    @Autowired
    private EntitlementRepository entitlementRepository;

    private String send(String method, String target, String extraHeaders, String body) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            out.write((method + " " + target + " HTTP/1.1\r\nHost: localhost\r\n" + extraHeaders
                    + "Content-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(bytes);
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/" + CONSENT,
            "/%69nternal/" + CONSENT,
            "/%69%6e%74%65%72%6e%61%6c/" + CONSENT,
            "/INTERNAL/" + CONSENT,
            "//internal/" + CONSENT,
            "///internal/" + CONSENT,
            "/x/../internal/" + CONSENT,
            "/./internal/" + CONSENT,
            "/%2e/internal/" + CONSENT,
            "/x/%2e%2e/internal/" + CONSENT,
            "/internal;a=b/" + CONSENT,
            "/%69nternal;a=b/" + CONSENT,
            "/;a=b/internal/" + CONSENT,
            "/internal/./" + CONSENT,
            "/internal//" + CONSENT,
            "/internal/%76%31/users/1/consent-status",
    })
    void noSpellingReachesTheEndpointWithoutTheToken(String target) throws IOException {
        String response = send("GET", target, "", "");

        assertFalse(response.contains("\"pending\""), target + " reached the endpoint: " + response);
        assertFalse(response.startsWith("HTTP/1.1 200"), target + " returned 200: " + response);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/v1/money-events/payment-succeeded",
            "/%69nternal/v1/money-events/payment-succeeded",
            "//internal/v1/money-events/payment-succeeded",
            "/x/../internal/v1/money-events/payment-succeeded",
            "/internal;a=b/v1/money-events/payment-succeeded",
    })
    void moneyEventsCantBeForgedWithoutTheToken(String target) throws IOException {
        String response = send("POST", target, "", EVENT);

        assertFalse(response.startsWith("HTTP/1.1 200"), target + " returned 200: " + response);
        assertEquals(0, entitlementRepository.findAll().stream().filter(e -> e.getOrderId() == ORDER).count(), target + " granted sessions");
    }

    /** Proves the requests above are well formed: the same socket with the token does reach it. */
    @Test
    void theTokenStillGetsThrough() throws IOException {
        String response = send("GET", "/internal/" + CONSENT, "X-Internal-Token: test-internal-token\r\n", "");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.contains("\"pending\""), response);
    }
}
