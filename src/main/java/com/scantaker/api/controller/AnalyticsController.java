package com.scantaker.api.controller;

import com.scantaker.api.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analytics;

    public record TrackPayload(
            String sessionId,
            String visitorId,
            String path,
            String referrer,
            String device,
            String browser,
            String city,
            String eventType,
            Integer durationSeconds
    ) {}

    @PostMapping("/api/analytics/track")
    public Map<String, Object> track(@RequestBody TrackPayload payload, HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        String device = payload.device();
        if (device == null || device.isBlank()) {
            device = (ua != null && ua.matches("(?i).*(Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini).*"))
                    ? "Mobile" : "Desktop";
        }
        String city = payload.city();
        if (city == null || city.isBlank()) {
            String vercelCity = req.getHeader("x-vercel-ip-city");
            String vercelCountry = req.getHeader("x-vercel-ip-country");
            if (vercelCity != null && !vercelCity.isBlank()) {
                city = vercelCountry != null && !vercelCountry.isBlank()
                        ? vercelCity + ", " + vercelCountry
                        : vercelCity;
            }
        }

        boolean recorded = analytics.track(new AnalyticsService.TrackRequest(
                payload.sessionId(),
                payload.visitorId(),
                payload.path(),
                payload.referrer(),
                device,
                payload.browser(),
                city,
                payload.eventType(),
                payload.durationSeconds(),
                ua
        ));
        return Map.of("ok", recorded);
    }

    @GetMapping("/api/admin/analytics/stats")
    public AnalyticsService.AnalyticsStats stats() {
        return analytics.getStats();
    }
}
