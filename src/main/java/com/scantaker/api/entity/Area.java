package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "areas")
@Getter @Setter @NoArgsConstructor
public class Area {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    private boolean active = true;
    private int sortOrder;
}