package com.narturebelle.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    private final ResourceLoader resourceLoader;

    @Value("${firebase.credentials.path:classpath:firebase-service-account.json}")
    private String credentialsPath;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialized.");
            return;
        }

        Resource resource = resourceLoader.getResource(credentialsPath);
        if (resource.exists()) {
            // Local dev: explicit service account JSON on disk (gitignored).
            try (InputStream is = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();
                FirebaseApp.initializeApp(options);
                log.info(">>> Firebase Admin SDK initialized with credentials file: {}", credentialsPath);
                return;
            } catch (Exception e) {
                log.error("Failed to initialize Firebase Admin SDK from '{}': {}", credentialsPath, e.getMessage());
                return;
            }
        }

        try {
            // Cloud Run / GCP: use the service's attached IAM identity, no JSON file needed.
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            FirebaseApp.initializeApp(options);
            log.info(">>> Firebase Admin SDK initialized via Application Default Credentials.");
        } catch (Exception e) {
            log.warn(">>> [FIREBASE NOTICE]: No credentials file at '{}' and Application Default Credentials unavailable.", credentialsPath);
            log.warn(">>> For local dev: generate a service account JSON (Firebase Console -> Project Settings -> Service Accounts)");
            log.warn(">>> and place it at 'NB-Ecomm-API/src/main/resources/firebase-service-account.json' (gitignored).");
            log.warn(">>> On Cloud Run: attach a service account with Firebase Admin permissions to the service.");
            log.warn(">>> The application will run in dev/fallback mode until then.");
        }
    }
}
