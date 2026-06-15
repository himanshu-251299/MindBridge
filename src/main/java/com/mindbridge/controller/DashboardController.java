package com.mindbridge.controller;

import com.mindbridge.model.BurnoutScore;
import com.mindbridge.model.Employee;
import com.mindbridge.repository.BurnoutScoreRepository;
import com.mindbridge.repository.EmployeeRepository;
import com.mindbridge.service.BurnoutScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DashboardController — REST endpoints for the HR Manager dashboard.
 *
 * GET /api/dashboard/{companyId}/overview     → Team wellness overview
 * GET /api/dashboard/{companyId}/alerts       → High/Critical risk employees
 * POST /api/dashboard/score/{employeeId}      → Trigger on-demand scoring
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "HR Manager endpoints for team wellness overview and burnout alerts")
public class DashboardController {

    private final BurnoutScoreRepository burnoutScoreRepository;
    private final EmployeeRepository employeeRepository;
    private final BurnoutScoringService burnoutScoringService;

    /**
     * Team wellness overview for a given date (defaults to today).
     * Returns anonymized team-level risk distribution.
     *
     * NOTE: Individual employee names are NOT returned here for privacy.
     * HR sees aggregate counts and can see named alerts only for HIGH/CRITICAL.
     */
    @GetMapping("/{companyId}/overview")
    @Operation(
            summary = "Team wellness overview",
            description = "Returns anonymized team-level risk distribution, average score, and top stressors for a given date. Defaults to today."
    )
    public ResponseEntity<?> getTeamOverview(
            @PathVariable UUID companyId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<BurnoutScore> scores = burnoutScoreRepository
                .findByCompanyIdAndScoreDate(companyId, targetDate);

        // Count by risk level
        Map<String, Long> riskDistribution = scores.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getRiskLevel().name(),
                        Collectors.counting()
                ));

        // Average team risk score
        double avgScore = scores.stream()
                .mapToInt(BurnoutScore::getRiskScore)
                .average()
                .orElse(0.0);

        // Top stressors across the team
        List<String> topStressors = scores.stream()
                .flatMap(s -> s.getPrimaryStressors() != null
                        ? s.getPrimaryStressors().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        return ResponseEntity.ok(Map.of(
                "date",             targetDate,
                "companyId",        companyId,
                "totalEmployees",   scores.size(),
                "averageRiskScore", Math.round(avgScore),
                "riskDistribution", riskDistribution,
                "topStressors",     topStressors,
                "teamHealthStatus", getTeamHealthStatus(avgScore)
        ));
    }

    /**
     * Returns HIGH and CRITICAL risk employees for HR to follow up.
     * This is where HR sees named alerts (only for high-risk cases).
     */
    @GetMapping("/{companyId}/alerts")
    @Operation(
            summary = "Get HIGH and CRITICAL risk alerts",
            description = "Returns named employee alerts for HIGH and CRITICAL risk levels. HR uses this to prioritize follow-ups."
    )
    public ResponseEntity<?> getAlerts(@PathVariable UUID companyId) {
        List<BurnoutScore> highRisk = burnoutScoreRepository
                .findByCompanyIdAndScoreDate(companyId, LocalDate.now())
                .stream()
                .filter(s -> s.getRiskLevel() == BurnoutScore.RiskLevel.HIGH
                        || s.getRiskLevel() == BurnoutScore.RiskLevel.CRITICAL)
                .toList();

        List<Map<String, Object>> alerts = highRisk.stream().map(score -> {
            Optional<Employee> emp = employeeRepository.findById(score.getEmployeeId());
            return Map.<String, Object>of(
                    "employeeId",       score.getEmployeeId(),
                    "employeeName",     emp.map(Employee::getFullName).orElse("Unknown"),
                    "riskLevel",        score.getRiskLevel(),
                    "riskScore",        score.getRiskScore(),
                    "trendDirection",   score.getTrendDirection(),
                    "primaryStressors", score.getPrimaryStressors() != null
                            ? score.getPrimaryStressors() : List.of(),
                    "aiReasoning",      score.getAiReasoning()
            );
        }).toList();

        return ResponseEntity.ok(Map.of(
                "date",       LocalDate.now(),
                "alertCount", alerts.size(),
                "alerts",     alerts
        ));
    }

    /**
     * Manually trigger burnout scoring for a single employee.
     * Useful for testing and on-demand HR requests.
     */
    @PostMapping("/score/{employeeId}")
    @Operation(
            summary = "Trigger on-demand burnout scoring",
            description = "Manually runs the Pattern Agent for a single employee. Useful for testing or urgent HR requests without waiting for the nightly scheduler."
    )
    public ResponseEntity<?> triggerScoring(@PathVariable UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        BurnoutScore score = burnoutScoringService.scoreEmployee(
                employeeId, employee.getCompanyId()
        );

        return ResponseEntity.ok(Map.of(
                "employeeId",   employeeId,
                "riskLevel",    score.getRiskLevel(),
                "riskScore",    score.getRiskScore(),
                "trend",        score.getTrendDirection()
        ));
    }

    private String getTeamHealthStatus(double avgScore) {
        if (avgScore <= 30) return "HEALTHY";
        if (avgScore <= 55) return "MODERATE";
        if (avgScore <= 75) return "CONCERNING";
        return "CRITICAL";
    }
}