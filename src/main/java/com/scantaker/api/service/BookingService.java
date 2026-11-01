package com.scantaker.api.service;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.dto.BookingDtos.*;
import com.scantaker.api.entity.*;
import com.scantaker.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookings;
    private final ScanRepository scans;
    private final CentreRepository centres;
    private final UserRepository users;
    private final FileStorage storage;
    private final NotificationService notifications;

    public record Rx(Resource file, String name, MediaType type) {}

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Booking not found"); }

    @Transactional
    public View create(Create r, Long userId) {
        if (blank(r.area()) && blank(r.pincode()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter your area or pincode");

        // Handle multiple scans if scansList or comma-separated scanSlug provided
        String primarySlug = r.scanSlug();
        String combinedName = "";
        String allScans = r.scansList();

        int totalEstimated = 0;
        if (allScans != null && !allScans.isBlank()) {
            // E.g. "mri-brain,ct-chest" or comma-separated
            String[] parts = allScans.split(",");
            List<String> names = new ArrayList<>();
            for (String p : parts) {
                String sSlug = p.trim();
                if (!sSlug.isEmpty()) {
                    var opt = scans.findBySlug(sSlug).filter(Scan::isActive);
                    if (opt.isPresent()) {
                        names.add(opt.get().getName());
                        totalEstimated += opt.get().getPrice();
                    }
                }
            }
            if (!names.isEmpty()) {
                combinedName = String.join(" + ", names);
            }
        }

        if (combinedName.isBlank()) {
            Scan scan = scans.findBySlug(primarySlug).filter(Scan::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Selected scan is not available"));
            combinedName = scan.getName();
            totalEstimated = scan.getPrice();
            if (allScans == null || allScans.isBlank()) allScans = scan.getName();
        }

        Booking b = new Booking();
        if (userId != null) b.setUser(users.getReferenceById(userId));
        b.setScanSlug(primarySlug);
        b.setScanName(combinedName);
        b.setScansList(allScans);
        b.setQuotedPrice(totalEstimated > 0 ? totalEstimated : null);
        b.setStage("PENDING");
        b.setContrast(r.contrast());
        b.setNotes(r.notes());
        b.setArea(r.area());
        b.setPincode(r.pincode());
        b.setPatientName(r.name().trim());
        b.setMobile(r.mobile());
        b.setEmail(r.email());
        b.setAge(r.age());
        b.setGender(r.gender());
        b.setPreferredDate(r.preferredDate());
        b.setPreferredTime(r.preferredTime());
        bookings.save(b);
        b.setCode("SCN-" + (1000 + b.getId()));
        notifications.notifyAdmin("New Booking Request 🩻", b.getPatientName() + " (" + b.getMobile() + ") requested " + b.getScanName() + " (" + b.getCode() + ")", "/admin/bookings");
        if (userId != null) {
            notifications.notifyUser(userId, "Scan Request Received ✓", "Your request #" + b.getCode() + " for " + b.getScanName() + " is received. Centre details will be shared shortly.", "/dashboard/bookings/" + b.getCode());
        }
        return View.from(b);
    }

    public Booking findBooking(String codeOrId) {
        if (codeOrId == null || codeOrId.isBlank()) throw notFound();
        return bookings.findByCode(codeOrId)
                .or(() -> {
                    try {
                        Long id = Long.parseLong(codeOrId);
                        return bookings.findById(id);
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(BookingService::notFound);
    }

    @Transactional(readOnly = true)
    public List<View> mine(Long userId) {
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).stream().map(View::from).toList();
    }

    @Transactional(readOnly = true)
    public View get(String code, Long userId) { return View.from(owned(code, userId)); }

    @Transactional
    public View cancel(String code, Long userId) {
        Booking b = owned(code, userId);
        if (b.getStatus() != BookingStatus.PENDING)
            throw new ApiException(HttpStatus.CONFLICT, "Only pending requests can be cancelled here. Please contact us on WhatsApp.");
        b.setStatus(BookingStatus.CANCELLED);
        b.setStage("CANCELLED");
        b.setCancelNote("Cancelled by user");
        return View.from(b);
    }

    private Booking owned(String codeOrId, Long userId) {
        Booking b = findBooking(codeOrId);
        if (userId != null) {
            User u = users.findById(userId).orElse(null);
            if (u != null) {
                if (u.getRole() == Role.ADMIN) return b;
                if (u.getRole() == Role.CENTRE && u.getCentre() != null && b.getCentre() != null
                        && b.getCentre().getId().equals(u.getCentre().getId())) {
                    return b;
                }
                if (b.getUser() != null && b.getUser().getId().equals(userId)) {
                    return b;
                }
            }
        }
        if (b.getUser() == null || (userId != null && b.getUser().getId().equals(userId))) {
            return b;
        }
        throw notFound();
    }

    /* ---------- prescription ---------- */

    @Transactional
    public View attachPrescription(String code, MultipartFile file, String mobile, Long userId) {
        Booking b = findBooking(code);
        boolean owner = userId != null && b.getUser() != null && b.getUser().getId().equals(userId);
        if (!owner) {
            if (mobile == null || !mobile.equals(b.getMobile())) throw notFound();
            if (b.getPrescriptionFile() != null) throw new ApiException(HttpStatus.CONFLICT, "Prescription already uploaded");
        }
        b.setPrescriptionFile(storage.save(file));
        String n = file.getOriginalFilename() == null ? "prescription" : file.getOriginalFilename();
        b.setPrescriptionName(n.replaceAll("[^A-Za-z0-9._-]", "_"));
        return View.from(b);
    }

    @Transactional(readOnly = true)
    public Rx prescription(String code, Long userId, boolean privileged) {
        Booking b = privileged ? findBooking(code) : owned(code, userId);
        if (b.getPrescriptionFile() == null) throw new ApiException(HttpStatus.NOT_FOUND, "No prescription uploaded");
        return new Rx(storage.load(b.getPrescriptionFile()), b.getPrescriptionName(), FileStorage.type(b.getPrescriptionFile()));
    }

    /* ---------- report ---------- */

    @Transactional
    public View attachReport(String code, MultipartFile file) {
        Booking b = findBooking(code);
        b.setReportFile(storage.save(file));
        String n = file.getOriginalFilename() == null ? "scan-report" : file.getOriginalFilename();
        b.setReportName(n.replaceAll("[^A-Za-z0-9._-]", "_"));
        b.setReportUploadedAt(Instant.now());
        b.setStatus(BookingStatus.COMPLETED);
        b.setStage("COMPLETED");
        if (b.getUser() != null) {
            notifications.notifyUser(b.getUser().getId(), "Scan Report Ready! 📄", "Your verified report for " + b.getScanName() + " (" + b.getCode() + ") is ready to download.", "/dashboard/reports");
        }
        notifications.notifyAdmin("Scan Report Uploaded ✓", (b.getCentre() != null ? b.getCentre().getName() : "Diagnostic Centre") + " uploaded report for " + b.getPatientName() + " (" + b.getCode() + ")", "/admin/bookings");
        return View.from(b);
    }

    @Transactional(readOnly = true)
    public Rx report(String code, Long userId, boolean privileged) {
        Booking b = privileged ? findBooking(code) : owned(code, userId);
        if (b.getReportFile() == null) throw new ApiException(HttpStatus.NOT_FOUND, "No report uploaded yet");
        return new Rx(storage.load(b.getReportFile()), b.getReportName(), FileStorage.type(b.getReportFile()));
    }

    /* ---------- admin ---------- */

    @Transactional(readOnly = true)
    public List<View> adminList(String status) {
        List<Booking> list = blank(status) || status.equalsIgnoreCase("all")
                ? bookings.findAllByOrderByCreatedAtDesc()
                : bookings.findByStatusOrderByCreatedAtDesc(parse(status));
        return list.stream().map(View::from).toList();
    }

    @Transactional
    public View adminUpdate(String code, AdminUpdate u) {
        Booking b = findBooking(code);
        if (u.status() != null) {
            b.setStatus(u.status());
            if (u.status() == BookingStatus.CANCELLED) {
                if (b.getCancelNote() == null) b.setCancelNote(u.cancelNote() != null ? u.cancelNote() : "Cancelled by Scantaker team");
            } else {
                b.setCancelNote(null);
            }
        }
        if (u.cancelNote() != null && !u.cancelNote().isBlank()) {
            b.setCancelNote(u.cancelNote());
        }
        if (u.paymentStatus() != null && !u.paymentStatus().isBlank()) {
            b.setPaymentStatus(u.paymentStatus());
        }
        if (u.stage() != null && !u.stage().isBlank()) {
            String st = u.stage().toUpperCase();
            b.setStage(st);
            if ("PAYMENT_DONE".equals(st)) {
                b.setPaymentStatus("PAYMENT_DONE");
            } else if ("PATIENT_NOT_COME".equals(st) || "NO_SHOW".equals(st)) {
                b.setStatus(BookingStatus.CANCELLED);
                b.setCancelNote("Patient did not arrive at diagnostic centre (No-show)");
            } else if ("CANCELLED_BY_CENTRE".equals(st)) {
                b.setStatus(BookingStatus.CANCELLED);
                b.setCancelNote(u.cancelNote() != null ? u.cancelNote() : "Cancelled by diagnostic centre");
            } else if ("COMPLETED".equals(st)) {
                b.setStatus(BookingStatus.COMPLETED);
            } else if ("PATIENT_ARRIVED".equals(st) || "CHECKED_IN".equals(st) || "IN_PROGRESS".equals(st)) {
                b.setStatus(BookingStatus.CONFIRMED);
            }
        }
        boolean centreAssigned = u.centreId() != null && (b.getCentre() == null || !b.getCentre().getId().equals(u.centreId()));
        if (u.centreId() != null) {
            b.setCentre(centres.findById(u.centreId()).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Centre not found")));
            if (b.getStatus() == BookingStatus.PENDING) {
                b.setStatus(BookingStatus.CONFIRMED);
                if (b.getStage() == null || b.getStage().equals("PENDING")) b.setStage("CONFIRMED");
            }
            if (centreAssigned) {
                notifications.notifyCentre(u.centreId(), "New Patient Assigned 👤", b.getPatientName() + " assigned for " + b.getScanName() + " (" + b.getCode() + ")", "/centre/dashboard");
                if (b.getUser() != null) {
                    notifications.notifyUser(b.getUser().getId(), "Centre Assigned 🏥", "Your scan is assigned to " + b.getCentre().getName() + " in " + b.getCentre().getArea(), "/dashboard/bookings/" + (b.getCode() != null ? b.getCode() : b.getId()));
                }
            }
        } else if (u.centreId() == null && u.status() == BookingStatus.PENDING) {
            b.setCentre(null);
        }
        boolean slotChanged = (u.appointmentDate() != null && !u.appointmentDate().equals(b.getAppointmentDate())) ||
                              (u.appointmentTime() != null && !u.appointmentTime().equals(b.getAppointmentTime()));
        if (u.quotedPrice() != null) b.setQuotedPrice(u.quotedPrice());
        if (u.appointmentDate() != null) b.setAppointmentDate(u.appointmentDate());
        if (u.appointmentTime() != null) b.setAppointmentTime(u.appointmentTime());
        if (slotChanged && !centreAssigned && b.getUser() != null) {
            notifications.notifyUser(b.getUser().getId(), "Appointment Slot Confirmed ⏰",
                    "Your scan slot is scheduled for " + b.getAppointmentDate() + " at " + (b.getAppointmentTime() != null ? b.getAppointmentTime() : "") + " with " + (b.getCentre() != null ? b.getCentre().getName() : "diagnostic centre"),
                    "/dashboard/bookings/" + (b.getCode() != null ? b.getCode() : b.getId()));
        }
        return View.from(b);
    }

    @Transactional
    public View submitFeedback(String code, Integer rating, String feedbackText, Long userId) {
        Booking b = findBooking(code);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }
        b.setRating(rating != null ? rating : 5);
        b.setFeedback(feedbackText != null ? feedbackText.trim() : "");
        b.setFeedbackAt(Instant.now());

        String centreName = b.getCentre() != null ? b.getCentre().getName() : "Diagnostic Centre";
        String commentSnippet = b.getFeedback().length() > 60 ? b.getFeedback().substring(0, 60) + "..." : b.getFeedback();
        notifications.notifyAdmin(
            "New Patient Feedback ⭐ (" + b.getRating() + "/5)",
            b.getPatientName() + " rated " + centreName + " " + b.getRating() + " stars: " + commentSnippet,
            "/admin/bookings"
        );
        return View.from(b);
    }

    @Transactional
    public void deleteBooking(String code) {
        Booking b = findBooking(code);
        bookings.delete(b);
    }

    /* ---------- centre portal ---------- */

    @Transactional(readOnly = true)
    public List<View> centreBookings(Long centreId) {
        if (centreId == null) return List.of();
        return bookings.findByCentreIdOrderByCreatedAtDesc(centreId).stream().map(View::from).toList();
    }

    @Transactional
    public View updateCentreBookingStatus(String code, Long centreId, CentreStatusUpdate u) {
        Booking b = findBooking(code);
        if (b.getCentre() == null || !b.getCentre().getId().equals(centreId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Booking is not assigned to your centre");
        }
        String s = u.status() != null ? u.status().toUpperCase() : "CONFIRMED";
        b.setStage(s);
        if ("COMPLETED".equals(s)) {
            b.setStatus(BookingStatus.COMPLETED);
            if (b.getUser() != null) {
                notifications.notifyUser(b.getUser().getId(), "Scan Completed ✓",
                        "Your " + b.getScanName() + " scan at " + b.getCentre().getName() + " is completed. Report will be uploaded shortly.",
                        "/dashboard/bookings/" + (b.getCode() != null ? b.getCode() : b.getId()));
            }
            notifications.notifyAdmin("Scan Completed ✓", b.getPatientName() + "'s scan completed at " + b.getCentre().getName(), "/admin/bookings");
        } else if ("IN_PROGRESS".equals(s)) {
            b.setStatus(BookingStatus.CONFIRMED);
            if (b.getUser() != null) {
                notifications.notifyUser(b.getUser().getId(), "Scan In Progress 🩻",
                        "Your " + b.getScanName() + " scan has started inside the scanner at " + b.getCentre().getName() + ".",
                        "/dashboard/bookings/" + (b.getCode() != null ? b.getCode() : b.getId()));
            }
        } else if ("CHECKED_IN".equals(s)) {
            b.setStatus(BookingStatus.CONFIRMED);
            if (b.getUser() != null) {
                notifications.notifyUser(b.getUser().getId(), "Checked In at Centre 🏥",
                        "Welcome to " + b.getCentre().getName() + "! You have been checked in at reception.",
                        "/dashboard/bookings/" + (b.getCode() != null ? b.getCode() : b.getId()));
            }
            notifications.notifyAdmin("Patient Checked In 🏥", b.getPatientName() + " checked in at " + b.getCentre().getName(), "/admin/bookings");
        } else if ("CANCELLED".equals(s)) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setCancelNote(u.note() != null ? u.note() : "Cancelled by diagnostic centre");
            if (b.getUser() != null) {
                notifications.notifyUser(b.getUser().getId(), "Booking Cancelled ⚠️",
                        "Your booking at " + b.getCentre().getName() + " was cancelled: " + b.getCancelNote(),
                        "/dashboard/bookings/" + (b.getCode() != null ? b.getCode() : b.getId()));
            }
            notifications.notifyAdmin("Booking Cancelled ⚠️", b.getPatientName() + " at " + b.getCentre().getName() + " was cancelled.", "/admin/bookings");
        } else {
            b.setStatus(BookingStatus.CONFIRMED);
        }
        return View.from(b);
    }

    private BookingStatus parse(String s) {
        try { return BookingStatus.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid status"); }
    }
}