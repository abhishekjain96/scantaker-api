package com.scantaker.api.repository;

import com.scantaker.api.entity.Booking;
import com.scantaker.api.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByCode(String code);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findAllByOrderByCreatedAtDesc();
    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);
    List<Booking> findByCentreIdOrderByCreatedAtDesc(Long centreId);
    long countByUserId(Long userId);
    long countByCentreId(Long centreId);
}