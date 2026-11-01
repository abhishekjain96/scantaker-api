package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipientType; // ADMIN, CENTRE, USER

    private Long recipientId; // userId or centreId

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Notification(String recipientType, Long recipientId, String title, String message, String link) {
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.title = title;
        this.message = message;
        this.link = link;
        this.read = false;
        this.createdAt = Instant.now();
    }
}
