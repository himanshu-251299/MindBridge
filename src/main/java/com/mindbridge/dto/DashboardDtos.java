package com.mindbridge.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

public class DashboardDtos {

    // ── Wellness Pulse response ──────────────────────────
    @Getter
    @Builder
    public static class WellnessPulse {
        private int    energyLevel;       // avg energy_score today (0-100 scaled)
        private String energyStatus;      // Good / Moderate / Low
        private int    supportIndex;      // avg team_support_score today (0-100 scaled)
        private String supportStatus;
        private int    burnoutRisk;       // avg risk_score today
        private String burnoutStatus;
        private int    checkInsToday;
        private int    totalEmployees;
        private double checkInRate;       // % of employees who checked in
    }

    // ── Employee list item ───────────────────────────────
    @Getter
    @Builder
    public static class EmployeeListItem {
        private UUID   id;
        private String fullName;
        private String email;
        private String department;
        private String role;
        private int    wellnessScore;     // inverted risk score (100 - riskScore)
        private String riskLevel;         // LOW / MEDIUM / HIGH / CRITICAL
        private int    riskScore;
        private String lastCheckIn;       // "Today" / "Yesterday" / "X days ago"
        private String status;            // Active / Alert / Needs Attention / No Data
        private String initials;          // for avatar
    }

    // ── Employee stats (4 top cards) ─────────────────────
    @Getter
    @Builder
    public static class EmployeeStats {
        private int totalEmployees;
        private int healthyCount;         // LOW risk
        private int atRiskCount;          // MEDIUM + HIGH
        private int criticalCount;        // CRITICAL
        private int noDataCount;          // no burnout score today
        private double healthyPercent;
        private double atRiskPercent;
        private double criticalPercent;
    }
}