package com.mindbridge.controller;

import com.mindbridge.dto.AuthDtos;
import com.mindbridge.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register a company and login to get a JWT token")
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new company + HR Manager.
     *
     * Request:
     * {
     *   "companyName": "Acme Corp",
     *   "fullName": "Jane HR",
     *   "email": "jane@acme.com",
     *   "password": "securePassword123"
     * }
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register a new company",
        description = "Creates a new company and HR Manager account. Returns a JWT token immediately — no separate login needed."
    )
    public ResponseEntity<?> register(@RequestBody AuthDtos.RegisterRequest request) {
        try {
            AuthDtos.AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                AuthDtos.ErrorResponse.builder()
                    .error("REGISTRATION_FAILED")
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Login with email + password. Returns JWT token.
     *
     * Request:
     * {
     *   "email": "jane@acme.com",
     *   "password": "securePassword123"
     * }
     */
    @PostMapping("/login")
    @Operation(
        summary = "Login and get JWT token",
        description = "Authenticates with email and password. Copy the returned token and use it as: Authorization: Bearer <token> on all other endpoints."
    )
    public ResponseEntity<?> login(@RequestBody AuthDtos.LoginRequest request) {
        try {
            AuthDtos.AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(
                AuthDtos.ErrorResponse.builder()
                    .error("INVALID_CREDENTIALS")
                    .message("Invalid email or password")
                    .build()
            );
        }
    }
}