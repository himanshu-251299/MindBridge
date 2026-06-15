package com.mindbridge.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class EmployeeDtos {

    // ─────────────────────────────────────
    // Self-Registration Request
    // POST /api/employees/register
    // ─────────────────────────────────────
    @Getter
    public static class RegisterRequest {
        private String inviteCode;      // Company invite code from HR
        private String fullName;
        private String email;
        private String password;
    }

    // ─────────────────────────────────────
    // Login Request
    // POST /api/employees/login
    // ─────────────────────────────────────
    @Getter
    public static class LoginRequest {
        private String email;
        private String password;
    }

    // ─────────────────────────────────────
    // Auth Response (same shape as HR login)
    // ─────────────────────────────────────
    @Getter
    @Builder
    public static class AuthResponse {
        private String token;
        private UUID employeeId;
        private String email;
        private String fullName;
        private String companyId;
        private long expiresIn;
    }

    // ─────────────────────────────────────
    // Employee Profile Response
    // GET /api/employees/me
    // ─────────────────────────────────────
    @Getter
    @Builder
    public static class ProfileResponse {
        private UUID id;
        private String fullName;
        private String email;
        private String companyId;
        private String teamId;
        private boolean active;
        private LocalDateTime createdAt;
    }

    // ─────────────────────────────────────
    // Check-in Summary (for history view)
    // ─────────────────────────────────────
    @Getter
    @Builder
    public static class CheckInSummary {
        private UUID id;
        private LocalDate date;
        private Integer energyScore;
        private String workloadTag;
        private Integer teamSupportScore;
        private String rawSentiment;
    }
}