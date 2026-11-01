package com.scantaker.api.controller;

import com.scantaker.api.entity.Booking;
import com.scantaker.api.entity.BookingStatus;
import com.scantaker.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
@RequiredArgsConstructor
public class AdminStatsController {
    private final BookingRepository bookingRepo;
    private final ScanRepository scanRepo;
    private final CentreRepository centreRepo;
    private final UserRepository userRepo;

    public record Day(LocalDate date, long count) {}
    public record Stats(long pending, long confirmed, long completed, long cancelled, long totalBookings,
                        long activeScans, long activeCentres, long users, List<Day> last7) {}

    @GetMapping("/api/admin/stats")
    public Stats stats() {
        List<Booking> all = bookingRepo.findAll();
        ZoneId z = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(z);
        Map<LocalDate, Long> byDay = all.stream().collect(
            Collectors.groupingBy(b -> LocalDate.ofInstant(b.getCreatedAt(), z), Collectors.counting()));
        List<Day> last7 = IntStream.range(0, 7).mapToObj(i -> today.minusDays(6 - i))
            .map(d -> new Day(d, byDay.getOrDefault(d, 0L))).toList();
        return new Stats(
            count(all, BookingStatus.PENDING), count(all, BookingStatus.CONFIRMED),
            count(all, BookingStatus.COMPLETED), count(all, BookingStatus.CANCELLED), all.size(),
            scanRepo.findByActiveTrueOrderBySortOrderAsc().size(),
            centreRepo.findAll().stream().filter(c -> c.isActive()).count(),
            userRepo.count(), last7);
    }

    private long count(List<Booking> l, BookingStatus s) { return l.stream().filter(b -> b.getStatus() == s).count(); }
}