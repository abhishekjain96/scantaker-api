package com.scantaker.api;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.dto.AuthDtos.*;
import com.scantaker.api.dto.CentreDtos.CentreSignup;
import com.scantaker.api.entity.Centre;
import com.scantaker.api.entity.Role;
import com.scantaker.api.entity.User;
import com.scantaker.api.repository.CentreRepository;
import com.scantaker.api.repository.OtpVerificationRepository;
import com.scantaker.api.repository.UserRepository;
import com.scantaker.api.security.JwtService;
import com.scantaker.api.service.AuthService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtAndAuthServiceTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CentreRepository centreRepo;

    @Autowired
    private OtpVerificationRepository otpRepo;

    @Test
    void jwtTokenHasThirtyDayExpiryAndValidClaims() {
        User u = new User();
        u.setId(42L);
        u.setRole(Role.ADMIN);

        String token = jwtService.generate(u);
        Claims claims = jwtService.parse(token);

        assertEquals("42", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));

        long daysValid = ChronoUnit.DAYS.between(Instant.now(), claims.getExpiration().toInstant());
        assertTrue(daysValid >= 29 && daysValid <= 30, "JWT expiry must be ~30 days (got " + daysValid + "d)");
    }

    @Test
    void signupLoginOtpResetAndGoogleAuthWorkEndToEnd() {
        String mobile = "7000001111";
        String email = "patient.test@scantaker.com";
        userRepo.findByMobile(mobile).ifPresent(userRepo::delete);

        // Signup & duplicate guard
        AuthResponse signupRes = authService.signup(new Signup("Test Patient", mobile, email, "Pass@1234"));
        assertNotNull(signupRes.token());
        assertEquals("USER", signupRes.user().role());

        assertThrows(ApiException.class, () ->
                authService.signup(new Signup("Duplicate", mobile, "other@scantaker.com", "Pass@1234")));

        // Login with mobile and email
        AuthResponse loginRes = authService.login(new Login(email, "Pass@1234"));
        assertEquals(signupRes.user().id(), loginRes.user().id());

        // Bad password rejected
        assertThrows(ApiException.class, () -> authService.login(new Login(mobile, "WrongPass")));

        // Forgot password OTP flow
        authService.sendOtp(new OtpSendRequest(email, "FORGOT_PASSWORD"));
        var otp = otpRepo.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, "FORGOT_PASSWORD").orElseThrow();
        authService.forgotPasswordReset(new ForgotPasswordReset(email, otp.getCode(), "NewPass@999"));

        assertThrows(ApiException.class, () -> authService.login(new Login(mobile, "Pass@1234")));
        AuthResponse afterReset = authService.login(new Login(mobile, "NewPass@999"));
        assertNotNull(afterReset.token());

        // Google auth creates account cleanly when new
        AuthResponse googleRes = authService.googleAuth(new GoogleAuthRequest("google.new@scantaker.com", "Google User", "sub-1", null));
        assertTrue(googleRes.user().googleUser());
        assertNotNull(googleRes.token());
    }

    @Test
    void centreSignupRequiresAdminVerificationBeforeLogin() {
        String mobile = "7000002222";
        String email = "newcentre@scantaker.com";

        authService.centreSignup(new CentreSignup(
                "Jaipur Imaging Test", "Dr. Rao", mobile, email, "Centre@123", "Vaishali Nagar", "Plot 10", "MRI"
        ));

        // Login before admin verifies centre -> forbidden
        assertThrows(ApiException.class, () -> authService.login(new Login(mobile, "Centre@123")));

        Centre created = centreRepo.findAll().stream()
                .filter(c -> email.equalsIgnoreCase(c.getEmail()))
                .findFirst()
                .orElseThrow();
        created.setVerified(true);
        created.setActive(true);
        centreRepo.save(created);

        AuthResponse res = authService.login(new Login(mobile, "Centre@123"));
        assertEquals("CENTRE", res.user().role());
        assertEquals(created.getId(), res.user().centreId());
    }
}
