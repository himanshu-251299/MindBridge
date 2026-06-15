package com.mindbridge.dto;

import lombok.Builder;
import lombok.Getter;

// ─────────────────────────────────────
// Register Request
// POST /api/auth/register
// ─────────────────────────────────────
public class AuthDtos {

    @Getter
    public static class RegisterRequest {
        private String companyName;   // Creates a new company
        private String fullName;
        private String email;
        private String password;
    }

    @Getter
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Getter
    @Builder
    public static class AuthResponse {
        private String token;
        private String email;
        private String fullName;
        private String role;
        private String companyId;
        private String inviteCode;     // Returned on company registration — share with employees
        private long expiresIn;       // ms until expiry
    }

    @Getter
    @Builder
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}