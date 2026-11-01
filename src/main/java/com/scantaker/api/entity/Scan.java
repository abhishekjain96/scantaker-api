package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "scans")
@Getter @Setter @NoArgsConstructor
public class Scan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanType type;

    @Column(nullable = false)
    private String bodyPart;

    private String description;

    @Column(nullable = false)
    private int price;

    private Integer mrp;
    private String category;

    private String img;
    private String emoji;
    private boolean popular;
    private boolean active = true;
    private int sortOrder;
    
}