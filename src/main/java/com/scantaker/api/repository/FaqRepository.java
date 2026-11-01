package com.scantaker.api.repository;

import com.scantaker.api.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findAllByOrderBySortOrderAsc();
    List<Faq> findByActiveTrueOrderBySortOrderAsc();
}