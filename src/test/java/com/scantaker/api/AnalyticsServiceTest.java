package com.scantaker.api;

import com.scantaker.api.repository.VisitorPageHitRepository;
import com.scantaker.api.repository.VisitorSessionRepository;
import com.scantaker.api.service.AnalyticsService;
import com.scantaker.api.service.AnalyticsService.TrackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AnalyticsServiceTest {

    @Autowired
    private VisitorSessionRepository sessionRepo;

    @Autowired
    private VisitorPageHitRepository hitRepo;

    private AtomicReference<Instant> nowRef;
    private AnalyticsService analytics;

    @BeforeEach
    void setUp() {
        hitRepo.deleteAll();
        sessionRepo.deleteAll();
        nowRef = new AtomicReference<>(ZonedDateTime.of(2026, 11, 10, 23, 55, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant());
        Clock mutableClock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return nowRef.get(); }
        };
        analytics = new AnalyticsService(sessionRepo, hitRepo, mutableClock);
    }

    @Test
    void returnsZeroesWhenNoTrafficExists() {
        var stats = analytics.getStats();
        assertEquals(0, stats.liveActiveVisitors());
        assertEquals(0, stats.todayVisitors());
        assertEquals(0, stats.allTimeVisitors());
        assertEquals(0, stats.avgDurationSeconds());
        assertEquals(0, stats.mobilePercentage());
        assertTrue(stats.topPages().isEmpty());
        assertTrue(stats.topReferrers().isEmpty());
        assertTrue(stats.recentVisitors().isEmpty());
    }

    @Test
    void tracksIstTodayVsAllTimeAndReturningVisitorCorrectly() {
        // Day 1 at 23:55 IST: Visitor 1 visits from Instagram on Mobile
        assertTrue(analytics.track(new TrackRequest(
                "sess_day1", "vis_1", "/", "https://l.instagram.com/", "Mobile", "Chrome", "Jaipur, IN", "pageview", 12, "Mozilla/5.0"
        )));
        // Same session views second page + sends heartbeat
        assertTrue(analytics.track(new TrackRequest(
                "sess_day1", "vis_1", "/scans/mri-brain", "https://l.instagram.com/", "Mobile", "Chrome", "Jaipur, IN", "pageview", 45, "Mozilla/5.0"
        )));
        assertTrue(analytics.track(new TrackRequest(
                "sess_day1", "vis_1", "/scans/mri-brain", "https://l.instagram.com/", "Mobile", "Chrome", "Jaipur, IN", "heartbeat", 80, "Mozilla/5.0"
        )));

        // Advance past IST midnight -> Day 2 00:10 IST (15 minutes later)
        nowRef.set(ZonedDateTime.of(2026, 11, 11, 0, 10, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant());

        // Returning Visitor 1 starts new session from Google Search
        assertTrue(analytics.track(new TrackRequest(
                "sess_day2_v1", "vis_1", "/book", "https://www.google.co.in/", "Mobile", "Chrome", "Jaipur, IN", "pageview", 60, "Mozilla/5.0"
        )));
        // New Visitor 2 starts session on Desktop Direct
        assertTrue(analytics.track(new TrackRequest(
                "sess_day2_v2", "vis_2", "/", "Direct", "Desktop", "Chrome", "Jaipur, IN", "pageview", 20, "Mozilla/5.0"
        )));

        // Internal admin/centre and bots must be ignored completely
        assertFalse(analytics.track(new TrackRequest(
                "sess_admin", "vis_admin", "/admin/analytics", "Direct", "Desktop", "Chrome", "Jaipur, IN", "pageview", 10, "Mozilla/5.0"
        )));
        assertFalse(analytics.track(new TrackRequest(
                "sess_bot", "vis_bot", "/", "Direct", "Desktop", "Bot", "US", "pageview", 5, "Googlebot/2.1"
        )));

        var stats = analytics.getStats();
        assertEquals(2, stats.liveActiveVisitors(), "Both Day 2 visitors active within 5 mins");
        assertEquals(2, stats.todayVisitors(), "2 distinct visitors today (IST)");
        assertEquals(2, stats.allTimeVisitors(), "2 distinct unique visitors overall");
        assertEquals(2, stats.todaySessions(), "2 new sessions started today");
        assertEquals(3, stats.totalSessions(), "3 sessions across Day 1 and Day 2");
        assertEquals(2, stats.todayPageViews(), "2 page views today (/book and /)");
        assertEquals(4, stats.totalPageViews(), "4 total page views across both days");
        assertEquals(53, stats.avgDurationSeconds(), "Average of (80, 60, 20) = 53.33 -> 53s");
        assertEquals(67, stats.mobilePercentage(), "2 of 3 sessions are Mobile");
        assertEquals(33, stats.desktopPercentage());
    }
}
