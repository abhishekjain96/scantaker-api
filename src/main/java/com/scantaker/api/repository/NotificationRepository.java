package com.scantaker.api.repository;

import com.scantaker.api.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.recipientType = 'ADMIN' ORDER BY n.createdAt DESC")
    List<Notification> findAdminNotifications();

    @Query("SELECT n FROM Notification n WHERE n.recipientType = 'CENTRE' AND (n.recipientId = :centreId OR n.recipientId IS NULL) ORDER BY n.createdAt DESC")
    List<Notification> findCentreNotifications(@Param("centreId") Long centreId);

    @Query("SELECT n FROM Notification n WHERE n.recipientType = 'USER' AND (n.recipientId = :userId OR n.recipientId IS NULL) ORDER BY n.createdAt DESC")
    List<Notification> findUserNotifications(@Param("userId") Long userId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientType = 'ADMIN' AND n.read = false")
    long countUnreadAdmin();

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientType = 'CENTRE' AND (n.recipientId = :centreId OR n.recipientId IS NULL) AND n.read = false")
    long countUnreadCentre(@Param("centreId") Long centreId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientType = 'USER' AND (n.recipientId = :userId OR n.recipientId IS NULL) AND n.read = false")
    long countUnreadUser(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientType = 'ADMIN' AND n.read = false")
    void markAllAdminAsRead();

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientType = 'CENTRE' AND (n.recipientId = :centreId OR n.recipientId IS NULL) AND n.read = false")
    void markAllCentreAsRead(@Param("centreId") Long centreId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientType = 'USER' AND (n.recipientId = :userId OR n.recipientId IS NULL) AND n.read = false")
    void markAllUserAsRead(@Param("userId") Long userId);
}
