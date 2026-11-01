package com.scantaker.api.controller;

import com.scantaker.api.dto.AuthDtos.*;
import com.scantaker.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/auth/signup")
    public AuthResponse signup(@Valid @RequestBody Signup r) {
        return auth.signup(r);
    }

    @PostMapping("/auth/centre/signup")
    public AuthResponse centreSignup(@Valid @RequestBody com.scantaker.api.dto.CentreDtos.CentreSignup r) {
        return auth.centreSignup(r);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody Login r) {
        return auth.login(r);
    }

    @PostMapping("/auth/otp/send")
    public SimpleMessageResponse sendOtp(@Valid @RequestBody OtpSendRequest r) {
        return auth.sendOtp(r);
    }

    @PostMapping("/auth/otp/verify")
    public SimpleMessageResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest r) {
        return auth.verifyOtp(r);
    }

    @PostMapping("/auth/forgot-password/reset")
    public SimpleMessageResponse resetPassword(@Valid @RequestBody ForgotPasswordReset r) {
        return auth.forgotPasswordReset(r);
    }

    @PostMapping("/auth/google")
    public AuthResponse googleAuth(@Valid @RequestBody GoogleAuthRequest r) {
        return auth.googleAuth(r);
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Long userId) {
        return auth.me(userId);
    }

    @PutMapping("/me")
    public UserView update(@AuthenticationPrincipal Long userId, @Valid @RequestBody ProfileUpdate r) {
        return auth.update(userId, r);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal Long userId, @Valid @RequestBody PasswordChange r) {
        auth.changePassword(userId, r);
    }
}