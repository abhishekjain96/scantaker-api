package com.scantaker.api.repository;

import com.scantaker.api.entity.CentrePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CentrePriceRepository extends JpaRepository<CentrePrice, Long> {
    List<CentrePrice> findByCentreId(Long centreId);
    Optional<CentrePrice> findByCentreIdAndScanId(Long centreId, Long scanId);
    void deleteByCentreId(Long centreId);
}
