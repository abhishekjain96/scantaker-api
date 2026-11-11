package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "visitor_sessions")
@Getter @Setter @NoArgsConstructor
public class VisitorSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String sessionId;

    @Column(length = 120)
    private String visitorId;

    @Column(name = "active_path", length = 500)
    private String currentPath;

    @Column(length = 500)
    private String landingPath;

    @Column(length = 500)
    private String referrer;

    @Column(length = 120)
    private String sourceLabel;

    @Column(length = 40)
    private String device;

    @Column(length = 60)
    private String browser;

    @Column(length = 120)
    private String city;

    private int durationSeconds;

    private int pageViews;

    @Column(nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();
}
