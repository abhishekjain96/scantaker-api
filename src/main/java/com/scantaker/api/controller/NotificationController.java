package com.scantaker.api.controller;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.entity.Notification;
import com.scantaker.api.entity.Role;
import com.scantaker.api.entity.User;
import com.scantaker.api.repository.UserRepository;
import com.scantaker.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final UserRepository userRepo;

    public record NotificationDto(Long id, String recipientType, String title, String message, String link, boolean read, Instant createdAt) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getRecipientType(), n.getTitle(), n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt());
        }
    }

    @GetMapping
    public Map<String, Object> getMyNotifications(@AuthenticationPrincipal Long userId) {
        if (userId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in");
        User user = userRepo.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        List<Notification> list = notificationService.getNotifications(user);
        long unread = notificationService.getUnreadCount(user);
        return Map.of(
            "unreadCount", unread,
            "notifications", list.stream().map(NotificationDto::from).toList()
        );
    }

    @PutMapping("/{id}/read")
    public Map<String, Object> markAsRead(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        if (userId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in");
        notificationService.markAsRead(id);
        return Map.of("success", true);
    }

    @PutMapping("/read-all")
    public Map<String, Object> markAllAsRead(@AuthenticationPrincipal Long userId) {
        if (userId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in");
        User user = userRepo.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        notificationService.markAllAsRead(user);
        return Map.of("success", true);
    }

    public record BroadcastRequest(
        String targetType,
        Long targetId,
        String title,
        String message,
        String link
    ) {}

    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@AuthenticationPrincipal Long adminId, @RequestBody BroadcastRequest req) {
        if (adminId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in");
        User admin = userRepo.findById(adminId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (admin.getRole() != Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "Admin privileges required");

        int count = notificationService.broadcast(req.targetType(), req.targetId(), req.title(), req.message(), req.link());
        return Map.of("success", true, "count", count);
    }
}
