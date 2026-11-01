package com.scantaker.api.repository;

import com.scantaker.api.entity.Scan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScanRepository extends JpaRepository<Scan, Long> {
    List<Scan> findAllByOrderBySortOrderAsc();
    List<Scan> findByActiveTrueOrderBySortOrderAsc();
    List<Scan> findByActiveTrueAndPopularTrueOrderBySortOrderAsc();
    Optional<Scan> findBySlug(String slug);
    boolean existsBySlug(String slug);
}