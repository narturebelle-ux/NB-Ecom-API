package com.narturebelle.mail;

import com.narturebelle.internal.InternalTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Real Tomcat and a raw socket on purpose: MockMvc and URI clients change the path before the filter would see it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalPathSecurityTest {

    @LocalServerPort private int port;

    @MockBean private MailsClient mailsClient;

    private int status(String path, String header) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            String request = "GET " + path + " HTTP/1.1\r\nHost: localhost\r\n" + header + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            String line = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(line.split(" ")[1]);
        }
    }

    @Test
    void noSpellingOfThePath_getsPastTheToken() throws IOException {
        List<String> paths = List.of(
                "/internal/v1/users/1/consent-status",
                "/%69nternal/v1/users/1/consent-status",
                "/%69%6e%74%65%72%6e%61%6c/v1/users/1/consent-status",
                "/internal%2fv1/users/1/consent-status",
                "//internal/v1/users/1/consent-status",
                "/./internal/v1/users/1/consent-status",
                "/x/../internal/v1/users/1/consent-status",
                "/internal;a=b/v1/users/1/consent-status",
                "/%2e/internal/v1/users/1/consent-status");
        for (String path : paths) {
            int status = status(path, "");
            assertFalse(status >= 200 && status < 300, path + " returned " + status);
        }
        assertEquals(401, status("/%69nternal/v1/users/1/consent-status", ""));
    }

    @Test
    void theRealPathWithTheToken_stillWorks() throws IOException {
        assertEquals(200, status("/internal/v1/users/1/consent-status", InternalTokenFilter.HEADER + ": test-internal-token\r\n"));
    }
}
