package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "faqs")
@Getter @Setter @NoArgsConstructor
public class Faq {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 500)
    private String question;
    @Column(nullable = false, length = 2000)
    private String answer;
    private boolean active = true;
    private int sortOrder;
}