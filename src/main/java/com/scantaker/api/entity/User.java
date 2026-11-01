package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 10)
    private String mobile;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @ManyToOne(fetch = FetchType.EAGER)
    private Centre centre;

    private String city = "Jaipur, Rajasthan";

    @Column(columnDefinition = "boolean default false")
    private boolean googleUser = false;

    @Column(columnDefinition = "boolean default true")
    private boolean hasPasswordSet = true;

    @Column(columnDefinition = "boolean default true")
    private boolean mobileVerified = true;

    private Instant createdAt = Instant.now();
}