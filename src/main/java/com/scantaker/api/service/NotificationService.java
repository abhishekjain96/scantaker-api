package com.scantaker.api.service;

import com.scantaker.api.entity.Notification;
import com.scantaker.api.entity.Role;
import com.scantaker.api.entity.User;
import com.scantaker.api.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;

    @Transactional
    public void notifyAdmin(String title, String message, String link) {
        repo.save(new Notification("ADMIN", null, title, message, link));
        log.info("[NOTIFICATION] Admin: {} - {}", title, message);
    }

    @Transactional
    public void notifyCentre(Long centreId, String title, String message, String link) {
        repo.save(new Notification("CENTRE", centreId, title, message, link));
        log.info("[NOTIFICATION] Centre {}: {} - {}", centreId, title, message);
    }

    @Transactional
    public void notifyUser(Long userId, String title, String message, String link) {
        if (userId == null) return;
        repo.save(new Notification("USER", userId, title, message, link));
        log.info("[NOTIFICATION] User {}: {} - {}", userId, title, message);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(User user) {
        if (user.getRole() == Role.ADMIN) {
            return repo.findAdminNotifications();
        } else if (user.getRole() == Role.CENTRE && user.getCentre() != null) {
            return repo.findCentreNotifications(user.getCentre().getId());
        } else {
            return repo.findUserNotifications(user.getId());
        }
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        if (user.getRole() == Role.ADMIN) {
            return repo.countUnreadAdmin();
        } else if (user.getRole() == Role.CENTRE && user.getCentre() != null) {
            return repo.countUnreadCentre(user.getCentre().getId());
        } else {
            return repo.countUnreadUser(user.getId());
        }
    }

    @Transactional
    public void markAsRead(Long id) {
        repo.findById(id).ifPresent(n -> {
            n.setRead(true);
            repo.save(n);
        });
    }

    @Transactional
    public void markAllAsRead(User user) {
        if (user.getRole() == Role.ADMIN) {
            repo.markAllAdminAsRead();
        } else if (user.getRole() == Role.CENTRE && user.getCentre() != null) {
            repo.markAllCentreAsRead(user.getCentre().getId());
        } else {
            repo.markAllUserAsRead(user.getId());
        }
    }

    @Transactional
    public int broadcast(String targetType, Long targetId, String title, String message, String link) {
        String safeTitle = title != null && !title.isBlank() ? title : "ScanTaker Alert 🔔";
        String safeMsg = message != null && !message.isBlank() ? message : "Important update from ScanTaker team.";
        String type = targetType != null ? targetType.toUpperCase() : "ALL";

        int count = 0;
        if ("ALL_PATIENTS".equals(type) || "PATIENTS".equals(type)) {
            repo.save(new Notification("USER", null, safeTitle, safeMsg, link != null && !link.isBlank() ? link : "/dashboard/bookings"));
            count = 1;
        } else if ("ALL_CENTRES".equals(type) || "CENTRES".equals(type)) {
            repo.save(new Notification("CENTRE", null, safeTitle, safeMsg, link != null && !link.isBlank() ? link : "/centre/dashboard"));
            count = 1;
        } else if ("CENTRE".equals(type) && targetId != null) {
            repo.save(new Notification("CENTRE", targetId, safeTitle, safeMsg, link != null && !link.isBlank() ? link : "/centre/dashboard"));
            count = 1;
        } else if ("USER".equals(type) && targetId != null) {
            repo.save(new Notification("USER", targetId, safeTitle, safeMsg, link != null && !link.isBlank() ? link : "/dashboard/bookings"));
            count = 1;
        } else {
            // ALL: notify both centres, users, and admin
            repo.save(new Notification("USER", null, safeTitle, safeMsg, link != null && !link.isBlank() ? link : "/dashboard/bookings"));
            repo.save(new Notification("CENTRE", null, safeTitle, safeMsg, link != null && !link.isBlank() ? link : "/centre/dashboard"));
            repo.save(new Notification("ADMIN", null, safeTitle, safeMsg, link != null && !link.isBlank() ? link : "/admin/bookings"));
            count = 3;
        }
        log.info("[BROADCAST] Target: {}, Title: {}", type, safeTitle);
        return count;
    }
}
