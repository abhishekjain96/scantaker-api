package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "centres")
@Getter @Setter @NoArgsConstructor
public class Centre {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String area;

    private String address;
    private String phone;
    private String email;
    private String contactPerson;
    private boolean active = true;
    private boolean verified = true;
    private String modalities;
    private Double defaultDiscount;
    private String commissionType = "PERCENT"; // "PERCENT" | "FIXED"
    private Double commissionValue = 15.0; // percentage or fixed rupee amount
    @Column(columnDefinition = "TEXT")
    private String availableSlotsJson;
    private String imageUrl;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String timings;
    private String facilities;
    private java.time.Instant createdAt = java.time.Instant.now();
}