package com.scantaker.api.service;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.dto.AuthDtos.*;
import com.scantaker.api.entity.OtpVerification;
import com.scantaker.api.entity.Role;
import com.scantaker.api.entity.User;
import com.scantaker.api.repository.OtpVerificationRepository;
import com.scantaker.api.repository.UserRepository;
import com.scantaker.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final com.scantaker.api.repository.CentreRepository centres;
    private final OtpVerificationRepository otpRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final NotificationService notifications;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String clean(String s) {
        return (s == null || s.isBlank()) ? null : s.trim().toLowerCase();
    }

    public SimpleMessageResponse sendOtp(OtpSendRequest r) {
        String email = clean(r.email());
        if (email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Valid email address is required");
        }

        String purpose = r.purpose() != null ? r.purpose().toUpperCase() : "SIGNUP";

        if ("FORGOT_PASSWORD".equalsIgnoreCase(purpose)) {
            if (!users.existsByEmail(email)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "No account found with this email address");
            }
        } else if ("SIGNUP".equalsIgnoreCase(purpose)) {
            if (users.existsByEmail(email)) {
                throw new ApiException(HttpStatus.CONFLICT, "This email is already registered. Please login.");
            }
        }

        // Generate 6-digit OTP code
        String code = String.format("%06d", RANDOM.nextInt(900000) + 100000);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        OtpVerification otp = new OtpVerification(email, code, purpose, expiresAt);
        otpRepo.save(otp);

        emailService.sendOtpEmail(email, code, purpose);

        return new SimpleMessageResponse("Verification code sent to " + email, true, null);
    }

    public SimpleMessageResponse verifyOtp(OtpVerifyRequest r) {
        String email = clean(r.email());
        String code = r.code() != null ? r.code().trim() : "";
        String purpose = r.purpose() != null ? r.purpose().toUpperCase() : "SIGNUP";

        OtpVerification found = otpRepo.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No verification request found for this email."));

        if (found.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verification code has expired. Please request a new one.");
        }

        if (!found.getCode().equals(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid 6-digit verification code. Please check and try again.");
        }

        found.setVerified(true);
        otpRepo.save(found);

        return new SimpleMessageResponse("Email verified successfully", true, null);
    }

    @Transactional
    public SimpleMessageResponse forgotPasswordReset(ForgotPasswordReset r) {
        String email = clean(r.email());
        String code = r.code() != null ? r.code().trim() : "";

        OtpVerification found = otpRepo.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, "FORGOT_PASSWORD")
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No reset verification found for this email"));

        if (found.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verification code has expired. Please request a new OTP.");
        }

        if (!found.getCode().equals(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP code. Please enter the correct code.");
        }

        User u = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User account not found"));

        u.setPasswordHash(encoder.encode(r.newPassword()));
        users.save(u);

        found.setVerified(true);
        otpRepo.save(found);

        return new SimpleMessageResponse("Password has been reset successfully. You can now login.", true, null);
    }

    public AuthResponse googleAuth(GoogleAuthRequest r) {
        String email = clean(r.email());
        if (email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Google email is missing");
        }

        Optional<User> existing = users.findByEmail(email);
        User u;
        if (existing.isPresent()) {
            u = existing.get();
        } else {
            // Create new patient account from Google OAuth
            u = new User();
            u.setName(r.name() != null && !r.name().isBlank() ? r.name().trim() : "Google User");
            u.setEmail(email);
            // Temporary generated random mobile internally to satisfy DB not-null constraint
            u.setMobile("9" + String.format("%09d", Math.abs(RANDOM.nextLong()) % 1000000000L));
            u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            u.setRole(Role.USER);
            u.setGoogleUser(true);
            u.setHasPasswordSet(false);
            u.setMobileVerified(false);
            users.save(u);
            notifications.notifyAdmin("New Google User Registered", u.getName() + " (" + email + ") signed up via Google.", "/admin/patients");
        }

        return new AuthResponse(jwt.generate(u), UserView.from(u));
    }

    public AuthResponse signup(Signup r) {
        if (users.existsByMobile(r.mobile()))
            throw new ApiException(HttpStatus.CONFLICT, "This mobile number is already registered");
        String email = clean(r.email());
        if (email != null && users.existsByEmail(email))
            throw new ApiException(HttpStatus.CONFLICT, "This email is already registered");

        User u = new User();
        u.setName(r.name().trim());
        u.setMobile(r.mobile());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        users.save(u);
        notifications.notifyAdmin("New Patient Registered", u.getName() + " (" + u.getMobile() + ") joined ScanTaker.", "/admin/patients");
        return new AuthResponse(jwt.generate(u), UserView.from(u));
    }

    public AuthResponse centreSignup(com.scantaker.api.dto.CentreDtos.CentreSignup r) {
        if (users.existsByMobile(r.mobile()))
            throw new ApiException(HttpStatus.CONFLICT, "This mobile number is already registered");
        String email = clean(r.email());
        if (email != null && users.existsByEmail(email))
            throw new ApiException(HttpStatus.CONFLICT, "This email is already registered");

        com.scantaker.api.entity.Centre c = new com.scantaker.api.entity.Centre();
        c.setName(r.centreName().trim());
        c.setContactPerson(r.contactPerson().trim());
        c.setPhone(r.mobile());
        c.setEmail(email);
        c.setArea(r.area().trim());
        c.setAddress(r.address());
        c.setModalities(r.modalities() != null && !r.modalities().isBlank() ? r.modalities() : "MRI, CT");
        c.setVerified(false);
        c.setActive(false);
        centres.save(c);

        User u = new User();
        u.setName(r.centreName().trim());
        u.setMobile(r.mobile());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(com.scantaker.api.entity.Role.CENTRE);
        u.setCentre(c);
        u.setCity(r.area().trim() + ", Jaipur");
        users.save(u);
        notifications.notifyAdmin("New Centre Pending Approval ⏳", c.getName() + " (" + c.getArea() + ") registered. Verify in admin panel.", "/admin/centres");
        return new AuthResponse(jwt.generate(u), UserView.from(u));
    }

    public AuthResponse login(Login r) {
        String id = r.identifier().trim();
        Optional<User> found = id.contains("@") ? users.findByEmail(id.toLowerCase()) : users.findByMobile(id);
        User u = found.filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid mobile/email or password"));
        if (u.getRole() == com.scantaker.api.entity.Role.CENTRE && u.getCentre() != null) {
            if (!u.getCentre().isVerified() || !u.getCentre().isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Your centre account is pending verification by Scantaker admin. Please wait for approval.");
            }
        }
        return new AuthResponse(jwt.generate(u), UserView.from(u));
    }

    public UserView me(Long id) { return UserView.from(find(id)); }

    public UserView update(Long id, ProfileUpdate r) {
        User u = find(id);
        String email = clean(r.email());
        if (email != null && !email.equals(u.getEmail()) && users.existsByEmail(email))
            throw new ApiException(HttpStatus.CONFLICT, "This email is already registered");
        String mobile = clean(r.mobile());
        if (mobile != null && !mobile.isBlank()) {
            if (!mobile.equals(u.getMobile()) && users.existsByMobile(mobile)) {
                throw new ApiException(HttpStatus.CONFLICT, "This mobile number is already registered to another account");
            }
            u.setMobile(mobile);
            u.setMobileVerified(true);
        }
        u.setName(r.name().trim());
        u.setEmail(email);
        if (r.city() != null && !r.city().isBlank()) u.setCity(r.city().trim());
        return UserView.from(users.save(u));
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please login again"));
    }

    public void changePassword(Long id, PasswordChange r) {
        User u = find(id);
        if (u.isHasPasswordSet()) {
            if (r.current() == null || r.current().isBlank() || !encoder.matches(r.current(), u.getPasswordHash()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is wrong");
        }
        u.setPasswordHash(encoder.encode(r.next()));
        u.setHasPasswordSet(true);
        users.save(u);
    }
}