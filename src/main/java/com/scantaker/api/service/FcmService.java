package com.scantaker.api.service;

import com.google.firebase.messaging.*;
import com.scantaker.api.config.FirebaseConfig;
import com.scantaker.api.entity.DeviceToken;
import com.scantaker.api.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FirebaseConfig firebaseConfig;
    private final DeviceTokenRepository tokenRepo;

    public boolean isConfigured() {
        return firebaseConfig.isInitialized();
    }

    public String getConfigStatus() {
        return firebaseConfig.getStatus();
    }

    public long getTokenCount() {
        return tokenRepo.count();
    }

    @Transactional
    public void registerToken(Long userId, String token, String role, Long centreId, String userAgent) {
        if (token == null || token.isBlank()) return;
        String cleanToken = token.trim();
        Optional<DeviceToken> existing = tokenRepo.findByToken(cleanToken);

        if (existing.isPresent()) {
            DeviceToken dt = existing.get();
            if (userId != null) dt.setUserId(userId);
            if (role != null) dt.setRole(role);
            if (centreId != null) dt.setCentreId(centreId);
            if (userAgent != null) dt.setUserAgent(userAgent);
            dt.setUpdatedAt(Instant.now());
            tokenRepo.save(dt);
            log.info("[FCM] Updated token registration for user {} (role {})", userId, role);
        } else {
            DeviceToken dt = new DeviceToken(cleanToken, userId, role, centreId, userAgent);
            tokenRepo.save(dt);
            log.info("[FCM] Registered new device token for user {} (role {})", userId, role);
        }
    }

    @Transactional
    public void unregisterToken(String token) {
        if (token == null || token.isBlank()) return;
        tokenRepo.deleteByToken(token.trim());
        log.info("[FCM] Unregistered device token");
    }

    @Transactional
    public void unregisterUser(Long userId) {
        if (userId == null) return;
        tokenRepo.deleteByUserId(userId);
        log.info("[FCM] Unregistered all device tokens for user {}", userId);
    }

    @Async
    public void sendToUser(Long userId, String title, String body, String link) {
        if (userId == null) return;
        List<DeviceToken> tokens = tokenRepo.findByUserId(userId);
        sendToTokens(tokens, title, body, link);
    }

    @Async
    public void sendToRole(String role, String title, String body, String link) {
        if (role == null) return;
        List<DeviceToken> tokens;
        try {
            com.scantaker.api.entity.Role userRole = com.scantaker.api.entity.Role.valueOf(role.toUpperCase());
            tokens = tokenRepo.findByRoleOrUserRole(role.toUpperCase(), userRole);
        } catch (Exception e) {
            tokens = tokenRepo.findByRole(role.toUpperCase());
        }
        log.info("[FCM] Dispatching push to role '{}' (found {} recipient devices): {}", role, tokens.size(), title);
        sendToTokens(tokens, title, body, link);
    }

    @Async
    public void sendToCentre(Long centreId, String title, String body, String link) {
        if (centreId == null) return;
        List<DeviceToken> tokens = tokenRepo.findByCentreIdOrUserCentre(centreId);
        log.info("[FCM] Dispatching push to centreId {} (found {} recipient devices): {}", centreId, tokens.size(), title);
        sendToTokens(tokens, title, body, link);
    }

    @Async
    public void broadcast(String title, String body, String link) {
        List<DeviceToken> tokens = tokenRepo.findAll();
        sendToTokens(tokens, title, body, link);
    }

    private void sendToTokens(List<DeviceToken> deviceTokens, String title, String body, String link) {
        if (deviceTokens == null || deviceTokens.isEmpty()) {
            log.debug("[FCM] No recipient tokens found for push notification: {}", title);
            return;
        }

        if (!firebaseConfig.isInitialized()) {
            log.warn("[FCM] Push skipped: Firebase is not initialized. Notification '{}' saved to in-app inbox only.", title);
            return;
        }

        String safeLink = link != null && !link.isBlank() ? link : "/";

        for (DeviceToken dt : deviceTokens) {
            String token = dt.getToken();
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setTitle(title)
                                        .setBody(body)
                                        .setSound("default")
                                        .setDefaultVibrateTimings(true)
                                        .setPriority(AndroidNotification.Priority.HIGH)
                                        .setColor("#0b9eb5")
                                        .setClickAction(safeLink)
                                        .build())
                                .build())
                        .setWebpushConfig(WebpushConfig.builder()
                                .putHeader("Urgency", "high")
                                .setNotification(WebpushNotification.builder()
                                        .setTitle(title)
                                        .setBody(body)
                                        .setIcon("/icon-192.png")
                                        .setBadge("/icon-192.png")
                                        .setData(Map.of("link", safeLink))
                                        .build())
                                .setFcmOptions(WebpushFcmOptions.builder()
                                        .setLink(safeLink)
                                        .build())
                                .build())
                        .putData("title", title)
                        .putData("body", body)
                        .putData("link", safeLink)
                        .build();

                String res = FirebaseMessaging.getInstance().send(message);
                log.info("[FCM] Successfully delivered push to token [..{}]: responseId={}",
                        token.substring(Math.max(0, token.length() - 8)), res);
            } catch (FirebaseMessagingException e) {
                log.warn("[FCM] Delivery failed to token [..{}]: code={}, message={}",
                        token.substring(Math.max(0, token.length() - 8)), e.getMessagingErrorCode(), e.getMessage());
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    try {
                        tokenRepo.deleteByToken(token);
                        log.info("[FCM] Removed invalid/unregistered token from database");
                    } catch (Exception ex) {
                        log.error("[FCM] Failed to delete invalid token: {}", ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("[FCM] Unexpected error sending push notification: {}", e.getMessage());
            }
        }
    }
}
