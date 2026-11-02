package com.scantaker.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.config.path:}")
    private String configPath;

    private boolean initialized = false;

    @PostConstruct
    public void initialize() {
        if (!FirebaseApp.getApps().isEmpty()) {
            initialized = true;
            log.info("[FCM] Firebase already initialized with app name: {}", FirebaseApp.getInstance().getName());
            return;
        }

        try (InputStream stream = resolveCredentialsStream()) {
            if (stream == null) {
                log.warn("[FCM] No Firebase credentials provided (checked env FIREBASE_SERVICE_ACCOUNT_JSON, classpath:serviceAccountKey.json, ./serviceAccountKey.json). Push notifications are paused until credentials are provided.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();

            FirebaseApp.initializeApp(options);
            initialized = true;
            log.info("[FCM] Firebase Admin SDK initialized successfully! Real-time push notifications are ACTIVE.");
        } catch (Exception e) {
            log.error("[FCM] Failed to initialize Firebase Admin SDK: {}", e.getMessage(), e);
        }
    }

    public boolean isInitialized() {
        return initialized && !FirebaseApp.getApps().isEmpty();
    }

    public String getStatus() {
        if (!isInitialized()) return "NOT_INITIALIZED";
        return "ACTIVE (" + FirebaseApp.getInstance().getName() + ")";
    }

    private InputStream resolveCredentialsStream() {
        // 1. Raw JSON string from possible environment variables
        String[] possibleKeys = {
            "FIREBASE_SERVICE_ACCOUNT_JSON",
            "FIREBASE_SERVICE_ACCOUNT",
            "FIREBASE_CREDENTIALS",
            "FIREBASE_CREDENTIALS_JSON",
            "FIREBASE_CONFIG",
            "FIREBASE_KEY",
            "GOOGLE_APPLICATION_CREDENTIALS"
        };
        for (String key : possibleKeys) {
            String val = System.getenv(key);
            if (val != null && !val.isBlank()) {
                String clean = val.trim();
                if (clean.startsWith("{") && clean.endsWith("}")) {
                    log.info("[FCM] Loading credentials from {} environment variable", key);
                    return new ByteArrayInputStream(clean.getBytes(StandardCharsets.UTF_8));
                }
                File f = new File(clean);
                if (f.exists() && f.isFile()) {
                    try {
                        log.info("[FCM] Loading credentials from path in {}: {}", key, clean);
                        return new FileInputStream(f);
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Custom path from property/env
        if (configPath != null && !configPath.isBlank()) {
            File file = new File(configPath);
            if (file.exists() && file.isFile()) {
                try {
                    log.info("[FCM] Loading credentials from custom path: {}", configPath);
                    return new FileInputStream(file);
                } catch (Exception e) {
                    log.warn("[FCM] Failed to read custom path {}: {}", configPath, e.getMessage());
                }
            }
        }

        // 3. Classpath resource: serviceAccountKey.json
        try {
            ClassPathResource cpr = new ClassPathResource("serviceAccountKey.json");
            if (cpr.exists()) {
                log.info("[FCM] Loading credentials from classpath: serviceAccountKey.json");
                return cpr.getInputStream();
            }
        } catch (Exception ignored) {}

        // 4. File in current working directory: ./serviceAccountKey.json
        File localFile = new File("serviceAccountKey.json");
        if (localFile.exists() && localFile.isFile()) {
            try {
                log.info("[FCM] Loading credentials from working directory ./serviceAccountKey.json");
                return new FileInputStream(localFile);
            } catch (Exception ignored) {}
        }

        return null;
    }
}
