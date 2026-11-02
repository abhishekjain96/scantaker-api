package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "device_tokens", indexes = {
    @Index(name = "idx_device_tokens_user", columnList = "userId"),
    @Index(name = "idx_device_tokens_role", columnList = "role"),
    @Index(name = "idx_device_tokens_centre", columnList = "centreId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    private Long userId;

    @Column(length = 32)
    private String role; // USER, ADMIN, CENTRE

    private Long centreId;

    @Column(length = 255)
    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public DeviceToken(String token, Long userId, String role, Long centreId, String userAgent) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.centreId = centreId;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
