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

        try {
            Resource resource = resourceLoader.getResource(credentialsPath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(is))
                            .build();
                    FirebaseApp.initializeApp(options);
                    log.info(">>> Firebase Admin SDK initialized successfully with credentials: {}", credentialsPath);
                }
            } else {
                log.warn(">>> [FIREBASE NOTICE]: Firebase credentials file '{}' was not found.", credentialsPath);
                log.warn(">>> To enable real backend token verification:");
                log.warn(">>> 1. Open Firebase Console -> Project Settings -> Service Accounts");
                log.warn(">>> 2. Click 'Generate new private key' and save as 'firebase-service-account.json'");
                log.warn(">>> 3. Place it in 'NB-Ecomm-API/src/main/resources/' or specify 'firebase.credentials.path'");
                log.warn(">>> The application will run in dev/fallback mode until then.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage());
        }
    }
}
