package com.documorph.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documorph.dto.AuthResponse;
import com.documorph.dto.LoginRequest;
import com.documorph.dto.SetupRequest;
import com.documorph.dto.SignUpRequest;
import com.documorph.exception.InvalidCredentialsException;
import com.documorph.service.AuthRateLimiter;
import com.documorph.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody SignUpRequest request,
            HttpServletRequest http) {
        authRateLimiter.checkSignup(http.getRemoteAddr());
        return ResponseEntity.ok(authService.signUp(request));
    }

    /** Finish setting up the signed-in account (once). Requires the token from sign-up. */
    @PostMapping("/setup")
    public ResponseEntity<AuthResponse> setupUser(@Valid @RequestBody SetupRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.setupUser(request, userDetails.getUsername()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        authRateLimiter.checkLogin(request.getEmail(), http.getRemoteAddr());
        try {
            AuthResponse response = authService.login(request);
            authRateLimiter.recordSuccess(request.getEmail());
            return ResponseEntity.ok(response);
        } catch (InvalidCredentialsException e) {
            authRateLimiter.recordFailure(request.getEmail());
            throw e;
        }
    }
}
