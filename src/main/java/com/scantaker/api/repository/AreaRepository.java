package com.scantaker.api.repository;

import com.scantaker.api.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AreaRepository extends JpaRepository<Area, Long> {
    List<Area> findAllByOrderBySortOrderAsc();
    List<Area> findByActiveTrueOrderBySortOrderAsc();
    boolean existsByNameIgnoreCase(String name);
}