package com.scantaker.api.controller;

import com.scantaker.api.repository.BookingRepository;
import com.scantaker.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminUserController {
    private final UserRepository userRepo;
    private final BookingRepository bookingRepo;

    public record Row(Long id, String name, String mobile, String email, String role, String city,
                      LocalDate joined, long bookings) {}

    @GetMapping("/api/admin/users")
    public List<Row> list() {
        return userRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .map(u -> new Row(u.getId(), u.getName(), u.getMobile(), u.getEmail(), u.getRole().name(), u.getCity(),
                LocalDate.ofInstant(u.getCreatedAt(), ZoneId.of("Asia/Kolkata")),
                bookingRepo.countByUserId(u.getId())))
            .toList();
    }
}