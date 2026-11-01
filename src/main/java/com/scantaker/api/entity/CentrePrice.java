package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "centre_prices", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"centre_id", "scan_id"})
})
@Getter @Setter @NoArgsConstructor
public class CentrePrice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centre_id", nullable = false)
    private Centre centre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @Column(nullable = false)
    private int price;

    private Integer discountedPrice;

    private boolean available = true;

    private String commissionType; // "PERCENT" | "FIXED"
    private Double commissionValue; // custom scan commission override

    private Instant updatedAt = Instant.now();
}
