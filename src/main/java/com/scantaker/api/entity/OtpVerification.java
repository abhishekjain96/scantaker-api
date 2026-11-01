package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verifications")
@Getter @Setter @NoArgsConstructor
public class OtpVerification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 32)
    private String purpose; // "SIGNUP", "FORGOT_PASSWORD", "CENTRE_SIGNUP"

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private boolean verified = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public OtpVerification(String email, String code, String purpose, LocalDateTime expiresAt) {
        this.email = email.trim().toLowerCase();
        this.code = code.trim();
        this.purpose = purpose != null ? purpose : "SIGNUP";
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
        this.verified = false;
    }
}
