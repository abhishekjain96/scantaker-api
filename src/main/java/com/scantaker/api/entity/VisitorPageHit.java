package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "visitor_page_hits")
@Getter @Setter @NoArgsConstructor
public class VisitorPageHit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String sessionId;

    @Column(length = 120)
    private String visitorId;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private Instant viewedAt = Instant.now();
}
