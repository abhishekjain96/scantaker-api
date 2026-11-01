package com.scantaker.api.repository;

import com.scantaker.api.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, String purpose);
    void deleteByEmailIgnoreCase(String email);
}
