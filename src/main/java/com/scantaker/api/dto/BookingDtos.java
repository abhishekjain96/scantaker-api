package com.scantaker.api.dto;

import com.scantaker.api.entity.Booking;
import com.scantaker.api.entity.BookingStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.ZoneId;

public final class BookingDtos {
    private BookingDtos() {}

    public record Create(
        @NotBlank(message = "Scan is required") String scanSlug,
        String contrast,
        @Size(max = 1000) String notes,
        String area,
        @Pattern(regexp = "^(\\d{6})?$", message = "Pincode must be 6 digits") String pincode,
        @NotBlank(message = "Name is required") @Size(max = 80) String name,
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10 digit mobile number") String mobile,
        @Email(message = "Enter a valid email") String email,
        String age, String gender,
        @NotNull(message = "Preferred date is required") @FutureOrPresent(message = "Date cannot be in the past") LocalDate preferredDate,
        @NotBlank(message = "Preferred time is required") String preferredTime,
        String scansList) {}

    public record AdminUpdate(BookingStatus status, String stage, Long centreId, Integer quotedPrice,
                              LocalDate appointmentDate, String appointmentTime, String paymentStatus, String cancelNote) {}

    public record CentreStatusUpdate(String status, String note) {}

    public record FeedbackRequest(Integer rating, String feedback) {}

    public record View(
        String id, String scanSlug, String scanName, BookingStatus status,
        String patient, String mobile, String age, String gender,
        String area, String location, String contrast,
        LocalDate preferredDate, String preferredTime, String notes, LocalDate createdAt,
        Long centreId, String centre, Integer quotedPrice,
        LocalDate appointmentDate, String appointmentTime, String cancelNote,
        boolean hasPrescription, boolean hasReport, String reportName, String scansList, String stage,
        String centreAddress, String centrePhone, String centreArea, String paymentStatus,
        Integer rating, String feedback, java.time.Instant feedbackAt) {

        public static View from(Booking b) {
            String area = b.getArea() != null && !b.getArea().isBlank() ? b.getArea() : (b.getPincode() != null ? "Pincode " + b.getPincode() : "Jaipur");
            String contrast = "yes".equals(b.getContrast()) ? "With contrast"
                    : "no".equals(b.getContrast()) ? "Without contrast" : "Not sure";
            String stage = b.getStage() != null ? b.getStage() : b.getStatus().name();
            var c = b.getCentre();
            return new View(
                b.getCode(), b.getScanSlug(), b.getScanName(), b.getStatus(),
                b.getPatientName(), b.getMobile(), b.getAge(), b.getGender(),
                area, area + ", Jaipur", contrast,
                b.getPreferredDate(), b.getPreferredTime(), b.getNotes(),
                LocalDate.ofInstant(b.getCreatedAt(), ZoneId.of("Asia/Kolkata")),
                c == null ? null : c.getId(),
                c == null ? null : c.getName(),
                b.getQuotedPrice(), b.getAppointmentDate(), b.getAppointmentTime(), b.getCancelNote(),
                b.getPrescriptionFile() != null,
                b.getReportFile() != null,
                b.getReportName(),
                b.getScansList(),
                stage,
                c == null ? null : c.getAddress(),
                c == null ? null : c.getPhone(),
                c == null ? null : c.getArea(),
                b.getPaymentStatus() != null ? b.getPaymentStatus() : "PENDING",
                b.getRating(),
                b.getFeedback(),
                b.getFeedbackAt());
        }
    }
}