package com.narturebelle.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Real HTTP calls to Supabase's Storage REST API, reusing the same Supabase project that already
 * backs Postgres (same us-east-1 region) — no new infra account. Needs SUPABASE_URL and
 * SUPABASE_SERVICE_ROLE_KEY to actually work; both fail fast with no default, same treatment as
 * DB_URL/DB_PASSWORD (there's no safe degraded mode for storage, unlike Firebase's ADC fallback).
 *
 * IMPORTANT: Supabase's Storage API response field names (e.g. "url"/"signedURL"/"token") have
 * shifted across API versions. This implementation parses defensively (checks a few known field
 * name variants) but was written without access to real credentials to test against — verify the
 * exact wire format against current Supabase docs before relying on this in production, and add
 * the SupabaseStorageProviderTest (MockRestServiceServer-based) called for in the implementation
 * plan if one doesn't exist yet.
 */
@Component
public class SupabaseStorageProvider implements StorageProvider {

    private final RestClient restClient;
    private final String supabaseUrl;

    public SupabaseStorageProvider(@Value("${supabase.url}") String supabaseUrl,
                                    @Value("${supabase.service-role-key}") String serviceRoleKey) {
        this.supabaseUrl = supabaseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/storage/v1")
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .defaultHeader("apikey", serviceRoleKey)
                .build();
    }

    @Override
    public SignedUploadUrl createSignedUploadUrl(String bucket, String objectPath, Duration expiry) {
        Map<?, ?> response = restClient.post()
                .uri("/object/upload/sign/{bucket}/{path}", bucket, objectPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(Map.class);

        String url = firstNonNull(response, "url", "signedURL", "signedUrl");
        String token = firstNonNull(response, "token");
        String fullUrl = url != null && url.startsWith("/") ? supabaseBase() + "/storage/v1" + url : url;

        return new SignedUploadUrl(fullUrl, token, LocalDateTime.now().plus(expiry));
    }

    @Override
    public SignedDownloadUrl createSignedDownloadUrl(String bucket, String objectPath, Duration expiry) {
        Map<?, ?> response = restClient.post()
                .uri("/object/sign/{bucket}/{path}", bucket, objectPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", expiry.toSeconds()))
                .retrieve()
                .body(Map.class);

        String signedPath = firstNonNull(response, "signedURL", "signedUrl", "url");
        String fullUrl = signedPath != null && signedPath.startsWith("/") ? supabaseBase() + "/storage/v1" + signedPath : signedPath;

        return new SignedDownloadUrl(fullUrl, LocalDateTime.now().plus(expiry));
    }

    @Override
    public boolean objectExists(String bucket, String objectPath) {
        try {
            restClient.get()
                    .uri("/object/info/{bucket}/{path}", bucket, objectPath)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception notFoundOrError) {
            return false;
        }
    }

    @Override
    public void deleteObject(String bucket, String objectPath) {
        restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/object/{bucket}/{path}", bucket, objectPath)
                .retrieve()
                .toBodilessEntity();
    }

    private String supabaseBase() {
        return supabaseUrl;
    }

    private String firstNonNull(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }
}
