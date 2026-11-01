package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Setting {
    @Id @Column(name = "k")
    private String key;

    @Column(name = "v", length = 2000)
    private String value;
}