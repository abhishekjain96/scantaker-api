package com.scantaker.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
@Getter @Setter @NoArgsConstructor
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String code; // SCN-1001

    @ManyToOne(fetch = FetchType.LAZY)
    private User user; // guest booking ho to null

    private String scanSlug;
    private String scanName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    private String patientName;
    private String mobile;
    private String email;
    private String age;
    private String gender;
    private String area;
    private String pincode;
    private String contrast; // yes | unsure | no

    private LocalDate preferredDate;
    private String preferredTime;

    @Column(length = 1000)
    private String notes;

    @Column(length = 2000)
    private String scansList;

    private String stage;

    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.EAGER)
    private Centre centre;

    private Integer quotedPrice;
    private LocalDate appointmentDate;
    private String appointmentTime;
    private String cancelNote;
    private String prescriptionFile; // server pe stored naam (random)
    private String prescriptionName; // patient ki original file ka naam
    private String reportFile;
    private String reportName;
    private Instant reportUploadedAt;

    private String paymentStatus = "PENDING"; // PENDING | PAID_AT_CENTRE | PAYMENT_DONE
    private Integer rating;
    @Column(length = 2000)
    private String feedback;
    private Instant feedbackAt;
}