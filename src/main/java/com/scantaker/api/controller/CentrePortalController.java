package com.scantaker.api.controller;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.dto.BookingDtos;
import com.scantaker.api.dto.BookingDtos.CentreStatusUpdate;
import com.scantaker.api.dto.CentreDtos;
import com.scantaker.api.dto.CentreDtos.CentreStats;
import com.scantaker.api.entity.Booking;
import com.scantaker.api.entity.BookingStatus;
import com.scantaker.api.entity.Centre;
import com.scantaker.api.entity.User;
import com.scantaker.api.repository.BookingRepository;
import com.scantaker.api.repository.UserRepository;
import com.scantaker.api.service.BookingService;
import com.scantaker.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/centre")
@RequiredArgsConstructor
public class CentrePortalController {
    private final BookingService service;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final com.scantaker.api.repository.CentreRepository centres;
    private final AdminCentreController adminCentres;
    private final NotificationService notifications;

    private User getCentreUser(Long userId) {
        if (userId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please login as a diagnostic centre");
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (u.getCentre() == null) throw new ApiException(HttpStatus.FORBIDDEN, "No centre associated with this account");
        return u;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        Centre c = u.getCentre();
        List<Booking> list = bookings.findByCentreIdOrderByCreatedAtDesc(c.getId());

        LocalDate today = LocalDate.now();
        long total = list.size();
        long todayCount = list.stream().filter(b -> today.equals(b.getAppointmentDate())).count();
        long checkedIn = list.stream().filter(b -> "CHECKED_IN".equalsIgnoreCase(b.getStage())).count();
        long inProgress = list.stream().filter(b -> "IN_PROGRESS".equalsIgnoreCase(b.getStage())).count();
        long completed = list.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED || "COMPLETED".equalsIgnoreCase(b.getStage())).count();
        long reportsUploaded = list.stream().filter(b -> b.getReportFile() != null).count();
        double todayRevenue = list.stream()
                .filter(b -> today.equals(b.getAppointmentDate()) && b.getQuotedPrice() != null)
                .mapToDouble(Booking::getQuotedPrice).sum();

        CentreStats stats = new CentreStats(total, todayCount, checkedIn, inProgress, completed, reportsUploaded, todayRevenue);
        return Map.of("centre", CentreDtos.View.from(c), "stats", stats);
    }

    @GetMapping("/bookings")
    public List<BookingDtos.View> myBookings(@AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        return service.centreBookings(u.getCentre().getId());
    }

    @PutMapping("/bookings/{code}/status")
    public BookingDtos.View updateStatus(@PathVariable String code, @RequestBody CentreStatusUpdate body,
                                        @AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        return service.updateCentreBookingStatus(code, u.getCentre().getId(), body);
    }

    @PostMapping(value = "/bookings/{code}/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookingDtos.View uploadReport(@PathVariable String code, @RequestParam("file") MultipartFile file,
                                        @AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        Booking b = bookings.findByCode(code).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (b.getCentre() == null || !b.getCentre().getId().equals(u.getCentre().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Booking is not assigned to your centre");
        }
        return service.attachReport(code, file);
    }

    @GetMapping("/bookings/{code}/report")
    public ResponseEntity<Resource> report(@PathVariable String code, @AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        Booking b = bookings.findByCode(code).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (b.getCentre() == null || !b.getCentre().getId().equals(u.getCentre().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
        }
        var r = service.report(code, null, true);
        return ResponseEntity.ok().contentType(r.type())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.name() + "\"")
                .header("X-Content-Type-Options", "nosniff").body(r.file());
    }

    @GetMapping("/bookings/{code}/prescription")
    public ResponseEntity<Resource> prescription(@PathVariable String code, @AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        Booking b = bookings.findByCode(code).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (b.getCentre() == null || !b.getCentre().getId().equals(u.getCentre().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
        }
        var r = service.prescription(code, null, true);
        return ResponseEntity.ok().contentType(r.type())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.name() + "\"")
                .header("X-Content-Type-Options", "nosniff").body(r.file());
    }

    @GetMapping("/slots")
    public Map<String, String> getSlots(@AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        Centre c = u.getCentre();
        return Map.of("slotsJson", c.getAvailableSlotsJson() != null ? c.getAvailableSlotsJson() : "{}");
    }

    @PutMapping("/slots")
    public Map<String, String> updateSlots(@RequestBody Map<String, String> body, @AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        Centre c = u.getCentre();
        String json = body.get("slotsJson");
        if (json != null) {
            c.setAvailableSlotsJson(json);
            centres.save(c);
        }
        return Map.of("status", "ok", "slotsJson", c.getAvailableSlotsJson() != null ? c.getAvailableSlotsJson() : "{}");
    }

    @GetMapping("/prices")
    public List<AdminCentreController.CentreScanPriceView> getPrices(@AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        return adminCentres.getPrices(u.getCentre().getId());
    }

    @PostMapping("/bookings/{code}/notify-patient")
    public Map<String, Object> notifyPatient(@PathVariable String code, @RequestBody Map<String, String> body,
                                            @AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        Booking b = bookings.findByCode(code).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (b.getCentre() == null || !b.getCentre().getId().equals(u.getCentre().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Booking is not assigned to your centre");
        }
        if (b.getUser() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No user account is linked to this booking");
        }
        String type = body.getOrDefault("type", "CUSTOM");
        String customMsg = body.get("message");
        String title = "Update from " + u.getCentre().getName() + " 🏥";
        String message = customMsg != null && !customMsg.isBlank() ? customMsg : "You have an update regarding your scan appointment.";

        if ("REPORT_READY".equalsIgnoreCase(type)) {
            title = "Report Ready for Download 📄";
            message = "Your diagnostic scan report from " + u.getCentre().getName() + " is ready to download.";
        } else if ("SLOT_REMINDER".equalsIgnoreCase(type)) {
            title = "Scan Appointment Reminder ⏰";
            message = "Reminder: Please arrive at " + u.getCentre().getName() + " 15 minutes before your scheduled slot.";
        } else if ("PREPARATION".equalsIgnoreCase(type)) {
            title = "Scan Prep Instructions ℹ️";
            message = "Preparation note from " + u.getCentre().getName() + ": Please follow fasting or hydration instructions as advised.";
        }

        notifications.notifyUser(b.getUser().getId(), title, message, "/dashboard/bookings/" + b.getId());
        return Map.of("success", true, "title", title, "message", message);
    }

    @PutMapping("/profile")
    public CentreDtos.View updateProfile(@RequestBody CentreDtos.ProfileUpdate body, @AuthenticationPrincipal Long userId) {
        User u = getCentreUser(userId);
        Centre c = u.getCentre();
        if (body.description() != null) c.setDescription(body.description());
        if (body.timings() != null) c.setTimings(body.timings());
        if (body.facilities() != null) c.setFacilities(body.facilities());
        if (body.address() != null) c.setAddress(body.address());
        if (body.phone() != null) c.setPhone(body.phone());
        if (body.contactPerson() != null) c.setContactPerson(body.contactPerson());
        if (body.modalities() != null) c.setModalities(body.modalities());
        if (body.imageUrl() != null) c.setImageUrl(body.imageUrl());
        return CentreDtos.View.from(centres.save(c));
    }
}
