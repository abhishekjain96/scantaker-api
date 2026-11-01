package com.scantaker.api.dto;

import com.scantaker.api.entity.Centre;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class CentreDtos {
    private CentreDtos() {}

    public record View(Long id, String name, String area, String address, String phone,
                       String email, String contactPerson, boolean active, boolean verified,
                       String modalities, Double defaultDiscount, String commissionType,
                       Double commissionValue, String availableSlotsJson, String imageUrl,
                       String description, String timings, String facilities, Instant createdAt) {
        public static View from(Centre c) {
            return new View(
                c.getId(), c.getName(), c.getArea(), c.getAddress(), c.getPhone(),
                c.getEmail(), c.getContactPerson(), c.isActive(), c.isVerified(),
                c.getModalities(), c.getDefaultDiscount(),
                c.getCommissionType() != null ? c.getCommissionType() : "PERCENT",
                c.getCommissionValue() != null ? c.getCommissionValue() : 15.0,
                c.getAvailableSlotsJson(), c.getImageUrl(), c.getDescription(),
                c.getTimings(), c.getFacilities(), c.getCreatedAt());
        }
    }

    public record Request(
        @NotBlank(message = "Centre name is required") String name,
        @NotBlank(message = "Area is required") String area,
        String address,
        @Pattern(regexp = "^\\d{10}$", message = "Phone must be 10 digits") String phone,
        String email,
        String contactPerson,
        Boolean active,
        Boolean verified,
        String modalities,
        Double defaultDiscount,
        String commissionType,
        Double commissionValue,
        String availableSlotsJson,
        String imageUrl,
        String description,
        String timings,
        String facilities) {}

    public record ProfileUpdate(
        String description,
        String timings,
        String facilities,
        String address,
        String phone,
        String contactPerson,
        String modalities,
        String imageUrl) {}

    public record CentreSignup(
        @NotBlank(message = "Centre name is required") String centreName,
        @NotBlank(message = "Contact person name is required") String contactPerson,
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number") String mobile,
        @Email(message = "Enter a valid email") String email,
        @Size(min = 6, max = 72, message = "Password must be at least 6 characters") String password,
        @NotBlank(message = "Area is required") String area,
        String address,
        String modalities) {}

    public record CentreStats(
        long totalBookings,
        long todayBookings,
        long checkedIn,
        long inProgress,
        long completed,
        long reportsUploaded,
        double todayRevenue) {}
}