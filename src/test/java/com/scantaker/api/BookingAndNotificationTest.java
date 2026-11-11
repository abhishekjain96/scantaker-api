package com.scantaker.api;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.dto.AuthDtos.Signup;
import com.scantaker.api.dto.BookingDtos.*;
import com.scantaker.api.entity.BookingStatus;
import com.scantaker.api.entity.Centre;
import com.scantaker.api.entity.Role;
import com.scantaker.api.entity.User;
import com.scantaker.api.repository.*;
import com.scantaker.api.service.AuthService;
import com.scantaker.api.service.BookingService;
import com.scantaker.api.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BookingAndNotificationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CentreRepository centreRepo;

    @Autowired
    private UserRepository userRepo;

    @Test
    void fullBookingLifecycleCreatesNotificationsPreservesReportAndEnforcesOwnership() throws Exception {
        var auth = authService.signup(new Signup("Booking Tester", "7111112222", "booking.tester@scantaker.com", "Test@1234"));
        User patient = userRepo.findById(auth.user().id()).orElseThrow();

        User admin = userRepo.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst()
                .orElseThrow();
        long unreadAdminBefore = notificationService.getUnreadCount(admin);

        // Multi-scan booking
        View created = bookingService.create(new Create(
                "mri-brain-plain", "no", "Follow-up headache", "Vaishali Nagar", "302021",
                "Booking Tester", "7111112222", "booking.tester@scantaker.com", "32", "Male",
                LocalDate.now().plusDays(1), "10:00 AM", "mri-brain-plain,ct-head-plain"
        ), patient.getId());

        assertNotNull(created.id());
        assertTrue(created.id().startsWith("SCN-"));
        assertEquals(BookingStatus.PENDING, created.status());
        assertTrue(created.scanName().contains("+"), "Combined multi-scan name should join selected scans");
        assertTrue(notificationService.getUnreadCount(admin) > unreadAdminBefore, "Admin must receive real notification on new booking");
        assertTrue(notificationService.getUnreadCount(patient) >= 1, "Patient must receive booking received notification");

        // Admin assigns centre
        Centre centre = centreRepo.findAll().stream().findFirst().orElseThrow();
        View assigned = bookingService.adminUpdate(created.id(), new AdminUpdate(
                BookingStatus.CONFIRMED, "CONFIRMED", centre.getId(), 3800,
                LocalDate.now().plusDays(1), "11:00 AM", "PENDING", null
        ));
        assertEquals(BookingStatus.CONFIRMED, assigned.status());
        assertEquals(centre.getId(), assigned.centreId());

        // Attach prescription & report
        byte[] pdfBytes = "%PDF-1.4\n%test report".getBytes();
        bookingService.attachPrescription(created.id(), new MockMultipartFile("file", "rx.pdf", "application/pdf", pdfBytes), null, patient.getId());
        View completed = bookingService.attachReport(created.id(), new MockMultipartFile("file", "final-report.pdf", "application/pdf", pdfBytes));

        assertEquals(BookingStatus.COMPLETED, completed.status());
        assertTrue(completed.hasReport());

        var reportRes = bookingService.report(created.id(), patient.getId(), false);
        assertArrayEquals(pdfBytes, reportRes.file().getContentAsByteArray());

        // Another user cannot steal/cancel or leave feedback on this patient's booking
        var otherAuth = authService.signup(new Signup("Other User", "7111113333", "other.user@scantaker.com", "Test@1234"));
        assertThrows(ApiException.class, () -> bookingService.get(created.id(), otherAuth.user().id()));
        assertThrows(ApiException.class, () -> bookingService.submitFeedback(created.id(), 1, "Hijack", otherAuth.user().id()));

        // Owner feedback succeeds
        View rated = bookingService.submitFeedback(created.id(), 5, "Quick MRI experience", patient.getId());
        assertEquals(5, rated.rating());

        // Notification mark-all read updates unread count to 0
        notificationService.markAllAsRead(patient);
        assertEquals(0, notificationService.getUnreadCount(patient));
    }

    @Test
    void wrongCentreCannotUpdateUnassignedBookingAndInvalidCancelFails() {
        var auth = authService.signup(new Signup("Cancel Tester", "7111114444", "cancel.tester@scantaker.com", "Test@1234"));
        View b = bookingService.create(new Create(
                "mri-brain-plain", "unsure", null, "Mansarovar", "302020",
                "Cancel Tester", "7111114444", "cancel.tester@scantaker.com", "28", "Female",
                LocalDate.now().plusDays(1), "9:00 AM", null
        ), auth.user().id());

        assertThrows(ApiException.class, () -> bookingService.cancel(b.id(), "   ", auth.user().id()));
        assertThrows(ApiException.class, () -> bookingService.updateCentreBookingStatus(b.id(), 999999L, new CentreStatusUpdate("CHECKED_IN", null)));

        View cancelled = bookingService.cancel(b.id(), "Rescheduling next week", auth.user().id());
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
    }
}
