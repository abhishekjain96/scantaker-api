package com.scantaker.api.service;

import com.scantaker.api.entity.VisitorPageHit;
import com.scantaker.api.entity.VisitorSession;
import com.scantaker.api.repository.VisitorPageHitRepository;
import com.scantaker.api.repository.VisitorSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.*;
import java.util.*;

@Service
public class AnalyticsService {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final VisitorSessionRepository sessions;
    private final VisitorPageHitRepository pageHits;
    private final Clock clock;

    @Autowired
    public AnalyticsService(VisitorSessionRepository sessions, VisitorPageHitRepository pageHits) {
        this(sessions, pageHits, Clock.systemUTC());
    }

    public AnalyticsService(VisitorSessionRepository sessions, VisitorPageHitRepository pageHits, Clock clock) {
        this.sessions = sessions;
        this.pageHits = pageHits;
        this.clock = clock;
    }

    public record TrackRequest(
            String sessionId,
            String visitorId,
            String path,
            String referrer,
            String device,
            String browser,
            String city,
            String eventType,
            Integer durationSeconds,
            String userAgent
    ) {}

    public record LabelCount(String label, long count) {}
    public record PageCount(String path, long count) {}
    public record SourceCount(String source, long count) {}
    public record RecentVisitor(
            String id,
            String path,
            String city,
            String referrer,
            String device,
            int duration,
            int pageViews,
            long timeAgoSec
    ) {}

    public record AnalyticsStats(
            long liveActiveVisitors,
            long todayVisitors,
            long allTimeVisitors,
            long todaySessions,
            long totalSessions,
            long todayPageViews,
            long totalPageViews,
            long avgDurationSeconds,
            int mobilePercentage,
            int desktopPercentage,
            List<PageCount> topPages,
            List<SourceCount> topReferrers,
            List<RecentVisitor> recentVisitors,
            String generatedAt
    ) {}

    @Transactional
    public boolean track(TrackRequest req) {
        if (req == null || req.sessionId() == null || req.sessionId().isBlank()) return false;
        String cleanPath = normalizePath(req.path());
        if (shouldIgnore(cleanPath, req.userAgent())) return false;

        Instant now = clock.instant();
        String sid = truncate(req.sessionId().trim(), 120);
        String vid = req.visitorId() != null && !req.visitorId().isBlank()
                ? truncate(req.visitorId().trim(), 120)
                : sid;
        String source = normalizeSource(req.referrer());
        String device = "Mobile".equalsIgnoreCase(req.device()) ? "Mobile" : "Desktop";
        String browser = truncate(req.browser() != null && !req.browser().isBlank() ? req.browser() : "Browser", 60);
        String city = truncate(req.city() != null && !req.city().isBlank() ? req.city() : "Jaipur, IN", 120);
        int duration = req.durationSeconds() != null ? Math.max(0, Math.min(req.durationSeconds(), 86400)) : 0;

        Optional<VisitorSession> existingOpt = sessions.findBySessionId(sid);
        boolean isNewSession = existingOpt.isEmpty();
        boolean isPageView = isNewSession || "pageview".equalsIgnoreCase(req.eventType());

        VisitorSession s = existingOpt.orElseGet(VisitorSession::new);
        if (isNewSession) {
            s.setSessionId(sid);
            s.setVisitorId(vid);
            s.setLandingPath(cleanPath);
            s.setFirstSeenAt(now);
            s.setReferrer(truncate(req.referrer(), 500));
            s.setSourceLabel(source);
            s.setDevice(device);
            s.setBrowser(browser);
            s.setCity(city);
            s.setPageViews(1);
            s.setDurationSeconds(duration);
        } else {
            if (s.getVisitorId() == null || s.getVisitorId().isBlank()) s.setVisitorId(vid);
            if ("pageview".equalsIgnoreCase(req.eventType())) {
                s.setPageViews(s.getPageViews() + 1);
            }
            s.setDurationSeconds(Math.max(s.getDurationSeconds(), duration));
            if ((s.getSourceLabel() == null || "Direct".equals(s.getSourceLabel())) && !"Direct".equals(source)) {
                s.setSourceLabel(source);
                s.setReferrer(truncate(req.referrer(), 500));
            }
            if (req.city() != null && !req.city().isBlank()) s.setCity(city);
        }
        s.setCurrentPath(cleanPath);
        s.setLastSeenAt(now);
        sessions.save(s);

        if (isPageView) {
            VisitorPageHit hit = new VisitorPageHit();
            hit.setSessionId(sid);
            hit.setVisitorId(vid);
            hit.setPath(cleanPath);
            hit.setViewedAt(now);
            pageHits.save(hit);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public AnalyticsStats getStats() {
        Instant now = clock.instant();
        Instant fiveMinAgo = now.minus(Duration.ofMinutes(5));
        Instant startOfTodayIst = LocalDate.now(IST).atStartOfDay(IST).toInstant();

        long liveActive = sessions.countDistinctVisitorsSince(fiveMinAgo);
        long todayVisitors = sessions.countDistinctVisitorsSince(startOfTodayIst);
        long allTimeVisitors = sessions.countDistinctVisitorsAllTime();
        long todaySessions = sessions.countByFirstSeenAtAfter(startOfTodayIst);
        long totalSessions = sessions.count();
        long todayPageViews = pageHits.countByViewedAtAfter(startOfTodayIst);
        long totalPageViews = pageHits.count();
        long avgDuration = Math.round(sessions.averagePositiveDurationSeconds());

        long mobileCount = 0;
        long desktopCount = 0;
        for (Object[] row : sessions.countByDevice()) {
            String dev = String.valueOf(row[0]);
            long cnt = ((Number) row[1]).longValue();
            if ("Mobile".equalsIgnoreCase(dev)) mobileCount += cnt;
            else desktopCount += cnt;
        }
        long deviceTotal = mobileCount + desktopCount;
        int mobilePct = deviceTotal > 0 ? (int) Math.round(mobileCount * 100.0 / deviceTotal) : 0;
        int desktopPct = deviceTotal > 0 ? Math.max(0, 100 - mobilePct) : 0;

        List<PageCount> topPages = new ArrayList<>();
        for (Object[] row : pageHits.findTopPages(PageRequest.of(0, 10))) {
            topPages.add(new PageCount(String.valueOf(row[0]), ((Number) row[1]).longValue()));
        }

        List<SourceCount> topReferrers = new ArrayList<>();
        List<Object[]> sources = sessions.countBySourceLabel();
        for (int i = 0; i < Math.min(sources.size(), 8); i++) {
            Object[] row = sources.get(i);
            String label = row[0] != null ? String.valueOf(row[0]) : "Direct";
            topReferrers.add(new SourceCount(label, ((Number) row[1]).longValue()));
        }

        List<RecentVisitor> recent = new ArrayList<>();
        for (VisitorSession v : sessions.findTop20ByOrderByLastSeenAtDesc()) {
            long ago = Math.max(0, Duration.between(v.getLastSeenAt(), now).getSeconds());
            String shortId = v.getSessionId().length() > 10
                    ? v.getSessionId().substring(0, 10)
                    : v.getSessionId();
            recent.add(new RecentVisitor(
                    shortId,
                    v.getCurrentPath() != null ? v.getCurrentPath() : "/",
                    v.getCity() != null ? v.getCity() : "Jaipur, IN",
                    v.getSourceLabel() != null ? v.getSourceLabel() : "Direct",
                    v.getDevice() != null ? v.getDevice() : "Desktop",
                    v.getDurationSeconds(),
                    v.getPageViews(),
                    ago
            ));
        }

        return new AnalyticsStats(
                liveActive,
                todayVisitors,
                allTimeVisitors,
                todaySessions,
                totalSessions,
                todayPageViews,
                totalPageViews,
                avgDuration,
                mobilePct,
                desktopPct,
                topPages,
                topReferrers,
                recent,
                now.toString()
        );
    }

    static boolean shouldIgnore(String path, String userAgent) {
        if (path == null || path.startsWith("/admin") || path.startsWith("/centre")
                || path.startsWith("/api") || path.startsWith("/_next")) {
            return true;
        }
        if (userAgent != null) {
            String ua = userAgent.toLowerCase(Locale.ROOT);
            return ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")
                    || ua.contains("lighthouse") || ua.contains("vercel-screenshot");
        }
        return false;
    }

    static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "/";
        int q = rawPath.indexOf('?');
        String clean = q >= 0 ? rawPath.substring(0, q) : rawPath;
        if (!clean.startsWith("/")) clean = "/" + clean;
        return truncate(clean, 500);
    }

    static String normalizeSource(String ref) {
        if (ref == null || ref.isBlank() || "Direct".equalsIgnoreCase(ref.trim())) {
            return "Direct";
        }
        String lower = ref.toLowerCase(Locale.ROOT);
        if (lower.contains("scantaker.com") || lower.contains("localhost")) return "Direct";
        if (lower.contains("instagram")) return "Instagram";
        if (lower.contains("facebook") || lower.contains("fb.com") || lower.contains("l.messenger")) return "Facebook";
        if (lower.contains("google.")) return "Google Search";
        if (lower.contains("bing.")) return "Bing Search";
        if (lower.contains("whatsapp") || lower.contains("wa.me")) return "WhatsApp";
        if (lower.contains("youtube") || lower.contains("youtu.be")) return "YouTube";
        try {
            URI uri = URI.create(ref.startsWith("http") ? ref : "https://" + ref);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return truncate(uri.getHost().replaceFirst("^www\\.", ""), 120);
            }
        } catch (Exception ignored) {}
        return truncate(ref.trim(), 120);
    }

    private static String truncate(String val, int max) {
        if (val == null) return "";
        return val.length() <= max ? val : val.substring(0, max);
    }
}
