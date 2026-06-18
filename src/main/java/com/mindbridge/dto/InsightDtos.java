package com.mindbridge.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InsightDtos {

    // ── Hero card ────────────────────────────────────────
    @Getter @Builder
    public static class InsightSummary {
        private UUID   insightId;
        private String headline;
        private String narrative;
        private int    wellnessScore;
        private int    trendVsLast;          // e.g. +4 or -2
        private String trendDirection;       // IMPROVING | STABLE | DECLINING
        private String severity;
        private String generatedAt;          // "Updated 2m ago"
        private boolean isStale;             // true if no insight generated today
    }

    // ── Burnout Risk Forecast (line chart) ───────────────
    @Getter @Builder
    public static class ForecastPoint {
        private String day;                  // "Mon", "Tue" ...
        private double actualScore;          // historical avg risk
        private double projectedScore;       // AI predicted
        private boolean isProjected;         // true = dotted line
    }

    @Getter @Builder
    public static class BurnoutForecast {
        private List<ForecastPoint> dataPoints;
        private int    confidencePercent;    // e.g. 82
        private String forecastSummary;      // "Risk expected to remain stable"
        private String trend;               // IMPROVING | STABLE | WORSENING
    }

    // ── Team Segments at Risk (bar chart by dept) ────────
    @Getter @Builder
    public static class DepartmentRisk {
        private String department;
        private double avgRiskScore;
        private int    employeeCount;
        private int    highRiskCount;
        private String riskLevel;            // highest risk level in this dept
    }

    // ── Peak Stress Periods (bar chart by day) ───────────
    @Getter @Builder
    public static class DayStressLevel {
        private String dayOfWeek;            // Mon–Sun
        private double avgEnergyScore;       // from check_ins
        private double avgWorkloadStress;    // inferred from workload tags
        private int    checkInCount;
    }

    // ── AI Recommendation card ───────────────────────────
    @Getter @Builder
    public static class Recommendation {
        private UUID   id;
        private String priority;             // URGENT | HIGH | MEDIUM
        private String icon;                 // emoji
        private String title;
        private String detail;
        private String actionLabel;
        private String status;               // PENDING | ACTIONED | DISMISSED
        private String createdAt;
    }

    // ── Historical insight timeline entry ────────────────
    @Getter @Builder
    public static class HistoricalInsight {
        private UUID       id;
        private LocalDate  date;
        private String     severity;         // LOW | MEDIUM | HIGH | CRITICAL
        private String     summary;          // short version of headline
        private String     status;           // Actioned | In progress | Acknowledged | Resolved
    }

    // ── Full AI Insights page response ───────────────────
    @Getter @Builder
    public static class InsightsPageResponse {
        private InsightSummary        summary;
        private BurnoutForecast       burnoutForecast;
        private List<DepartmentRisk>  teamSegments;
        private List<DayStressLevel>  peakStressPeriods;
        private List<Recommendation>  recommendations;
        private List<HistoricalInsight> historicalInsights;
    }
}