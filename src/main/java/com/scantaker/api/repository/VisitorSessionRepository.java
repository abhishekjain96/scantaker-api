package com.scantaker.api.repository;

import com.scantaker.api.entity.VisitorSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VisitorSessionRepository extends JpaRepository<VisitorSession, Long> {
    Optional<VisitorSession> findBySessionId(String sessionId);

    @Query("select count(distinct coalesce(v.visitorId, v.sessionId)) from VisitorSession v where v.lastSeenAt >= :since")
    long countDistinctVisitorsSince(@Param("since") Instant since);

    @Query("select count(distinct coalesce(v.visitorId, v.sessionId)) from VisitorSession v")
    long countDistinctVisitorsAllTime();

    long countByLastSeenAtAfter(Instant cutoff);

    long countByFirstSeenAtAfter(Instant cutoff);

    @Query("select coalesce(avg(v.durationSeconds), 0) from VisitorSession v where v.durationSeconds > 0")
    double averagePositiveDurationSeconds();

    @Query("select v.device, count(v) from VisitorSession v group by v.device")
    List<Object[]> countByDevice();

    @Query("select v.sourceLabel, count(v) as c from VisitorSession v group by v.sourceLabel order by c desc")
    List<Object[]> countBySourceLabel();

    List<VisitorSession> findTop20ByOrderByLastSeenAtDesc();
}
