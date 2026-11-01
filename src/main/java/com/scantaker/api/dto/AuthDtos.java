package com.scantaker.api.dto;

import com.scantaker.api.entity.User;
import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {}

    public record Signup(
        @NotBlank(message = "Name is required") @Size(max = 80) String name,
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10 digit mobile number") String mobile,
        @Email(message = "Enter a valid email") String email,
        @Size(min = 6, max = 72, message = "Password must be 6 to 72 characters") String password) {}

    public record Login(
        @NotBlank(message = "Mobile or email is required") String identifier,
        @NotBlank(message = "Password is required") String password) {}

    public record ProfileUpdate(
        @NotBlank(message = "Name is required") @Size(max = 80) String name,
        @Email(message = "Enter a valid email") String email,
        @Pattern(regexp = "^([6-9]\\d{9})?$", message = "Enter a valid 10 digit mobile number") String mobile,
        String city) {}

    public record UserView(Long id, String name, String mobile, String email, String city, String role,
                           Long centreId, String centreName, boolean hasPasswordSet, boolean googleUser) {
        public static UserView from(User u) {
            Long cId = u.getCentre() != null ? u.getCentre().getId() : null;
            String cName = u.getCentre() != null ? u.getCentre().getName() : null;
            String displayMobile = (u.isGoogleUser() && !u.isMobileVerified()) ? null : u.getMobile();
            return new UserView(u.getId(), u.getName(), displayMobile, u.getEmail(), u.getCity(), u.getRole().name(), cId, cName, u.isHasPasswordSet(), u.isGoogleUser());
        }
    }

    public record PasswordChange(
        String current,
        @NotBlank(message = "New password is required") @Size(min = 6, max = 72, message = "New password must be 6 to 72 characters") String next) {}

    public record OtpSendRequest(
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
        String purpose) {}

    public record OtpVerifyRequest(
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
        @NotBlank(message = "OTP code is required") String code,
        String purpose) {}

    public record ForgotPasswordReset(
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
        @NotBlank(message = "OTP code is required") String code,
        @NotBlank(message = "New password is required") @Size(min = 6, max = 72, message = "Password must be 6 to 72 characters") String newPassword) {}

    public record GoogleAuthRequest(
        @NotBlank(message = "Email is required") @Email String email,
        @NotBlank(message = "Name is required") String name,
        String googleId,
        String avatar) {}

    public record AuthResponse(String token, UserView user) {}

    public record SimpleMessageResponse(String message, boolean success, String devOtp) {}
}