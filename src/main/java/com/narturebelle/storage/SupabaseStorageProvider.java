package com.narturebelle.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * LEGACY. Supabase Storage held files before D48 (11 Nov 2026): all files and videos now go to AWS S3,
 * and Supabase is the database only. Kept only so a record whose file was stored here can still be
 * opened (none exist as of 11 Nov 2026). SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are optional: without
 * them the app starts normally, and any call here fails with a clear message.
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
    private final boolean configured;

    public SupabaseStorageProvider(@Value("${supabase.url:}") String supabaseUrl,
                                    @Value("${supabase.service-role-key:}") String serviceRoleKey) {
        this.supabaseUrl = supabaseUrl;
        this.configured = !supabaseUrl.isBlank() && !serviceRoleKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/storage/v1")
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .defaultHeader("apikey", serviceRoleKey)
                .build();
    }

    @Override
    public SignedUploadUrl createSignedUploadUrl(String bucket, String objectPath, Duration expiry) {
        requireConfigured();
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
        requireConfigured();
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
        requireConfigured();
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
        requireConfigured();
        restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/object/{bucket}/{path}", bucket, objectPath)
                .retrieve()
                .toBodilessEntity();
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("This file is in legacy Supabase Storage, but SUPABASE_URL and "
                    + "SUPABASE_SERVICE_ROLE_KEY are not set. Files are stored in S3 now (D48).");
        }
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
