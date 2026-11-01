package com.narturebelle.mail;

import com.narturebelle.internal.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Private calls to NB-Ecom-Mails-API. */
@Component
public class MailsClient {

    private record SendResult(String status, boolean delivered) {}

    private final RestClient sends;
    private final RestClient reads;

    public MailsClient(@Value("${mails.base-url}") String baseUrl, @Value("${internal.api-token:}") String token) {
        // A sleeping container takes 20-40s to wake. Sends give up quickly, because the row stays for the
        // scheduler. Reads wait, because the caller needs the answer.
        this.sends = client(baseUrl, token, 3000);
        this.reads = client(baseUrl, token, 45000);
    }

    private static RestClient client(String baseUrl, String token, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(3000);
        timeouts.setReadTimeout(readTimeoutMillis);
        return RestClient.builder()
                .requestFactory(timeouts)
                .baseUrl(baseUrl)
                .defaultHeader(InternalTokenFilter.HEADER, token)
                .build();
    }

    /** True only if the email really went out. Throws if it wasn't handled, so the row is retried. */
    public boolean send(OutgoingMail mail) {
        SendResult result = sends.post().uri("/internal/v1/emails")
                .contentType(MediaType.APPLICATION_JSON).body(mail)
                .retrieve().body(SendResult.class);
        return result != null && result.delivered();
    }

    public void sendTransactional(Long userId, String title, String body) {
        sends.post().uri("/internal/v1/notifications/transactional")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("userId", userId, "title", title, "body", body))
                .retrieve().toBodilessEntity();
    }

    /** Repeats are safe: Mails ignores a reminder it already has. */
    public void sendReminders(List<DueReminder> reminders) {
        reads.post().uri("/internal/v1/reminders")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("reminders", reminders))
                .retrieve().toBodilessEntity();
    }

    /** The user's reminders and notification preferences, for their data export. */
    public Map<String, Object> exportFor(Long userId) {
        try {
            return reads.get().uri("/internal/v1/users/{id}/export", userId)
                    .retrieve().body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException e) {
            throw new MailsUnavailableException(e);
        }
    }
}
