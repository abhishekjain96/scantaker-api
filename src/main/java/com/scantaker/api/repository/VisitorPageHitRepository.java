package com.scantaker.api.repository;

import com.scantaker.api.entity.VisitorPageHit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface VisitorPageHitRepository extends JpaRepository<VisitorPageHit, Long> {
    long countByViewedAtAfter(Instant since);

    @Query("select p.path, count(p) as c from VisitorPageHit p group by p.path order by c desc")
    List<Object[]> findTopPages(Pageable pageable);
}
