package com.mindbridge.controller;

import com.mindbridge.model.BurnoutScore;
import com.mindbridge.model.Employee;
import com.mindbridge.repository.BurnoutScoreRepository;
import com.mindbridge.repository.CheckInRepository;
import com.mindbridge.repository.EmployeeRepository;
import com.mindbridge.service.BurnoutScoringService;
import com.mindbridge.service.WellnessPulseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "HR Manager endpoints for team wellness overview and burnout alerts")
public class DashboardController {

    private final BurnoutScoreRepository burnoutScoreRepository;
    private final EmployeeRepository     employeeRepository;
    private final CheckInRepository      checkInRepository;
    private final BurnoutScoringService  burnoutScoringService;
    private final WellnessPulseService   wellnessPulseService;

    /**
     * Team wellness overview — returns all fields the React dashboard needs.
     *
     * Response shape:
     * {
     *   totalEmployees, averageRiskScore, highRiskCount, checkInsToday,
     *   riskDistribution: { LOW, MEDIUM, HIGH, CRITICAL },
     *   trendSummary:     { IMPROVING, STABLE, DECLINING },
     *   topStressors:     ["Workload", ...]
     *   teamHealthStatus: "HEALTHY | MODERATE | CONCERNING | CRITICAL"
     * }
     */
    @GetMapping("/{companyId}/overview")
    @Operation(
            summary = "Team wellness overview",
            description = "Returns full dashboard overview — risk distribution, stressors, trend summary, and KPI counts."
    )
    public ResponseEntity<?> getTeamOverview(
            @PathVariable UUID companyId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        // ── Active employees for this company ──────────────────────────
        List<Employee> employees = employeeRepository
                .findByCompanyIdAndActiveTrue(companyId);
        int totalEmployees = employees.size();

        // ── Today's burnout scores ─────────────────────────────────────
        List<BurnoutScore> scores = burnoutScoreRepository
                .findByCompanyIdAndScoreDate(companyId, targetDate);

        // ── Risk distribution ──────────────────────────────────────────
        Map<String, Long> riskDistribution = new LinkedHashMap<>();
        riskDistribution.put("LOW",      0L);
        riskDistribution.put("MEDIUM",   0L);
        riskDistribution.put("HIGH",     0L);
        riskDistribution.put("CRITICAL", 0L);

        scores.stream()
                .collect(Collectors.groupingBy(s -> s.getRiskLevel().name(), Collectors.counting()))
                .forEach(riskDistribution::put);

        // ── High risk count ────────────────────────────────────────────
        long highRiskCount = (riskDistribution.get("HIGH") + riskDistribution.get("CRITICAL"));

        // ── Average risk score ─────────────────────────────────────────
        int averageRiskScore = (int) scores.stream()
                .mapToInt(s -> s.getRiskScore() != null ? s.getRiskScore() : 0)
                .average()
                .orElse(0.0);

        // ── Trend summary ──────────────────────────────────────────────
        Map<String, Long> trendSummary = new LinkedHashMap<>();
        trendSummary.put("IMPROVING", 0L);
        trendSummary.put("STABLE",    0L);
        trendSummary.put("DECLINING", 0L);

        scores.stream()
                .filter(s -> s.getTrendDirection() != null)
                .collect(Collectors.groupingBy(s -> s.getTrendDirection().name(), Collectors.counting()))
                .forEach(trendSummary::put);

        // ── Check-ins today ────────────────────────────────────────────
        long checkInsToday = employees.stream()
                .filter(e -> checkInRepository.existsByEmployeeIdAndCheckInDate(e.getId(), targetDate))
                .count();

        // ── Top stressors ──────────────────────────────────────────────
        List<String> topStressors = scores.stream()
                .flatMap(s -> s.getPrimaryStressors() != null
                        ? s.getPrimaryStressors().stream()
                        : java.util.stream.Stream.empty())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // ── Team health status ─────────────────────────────────────────
        String teamHealthStatus = averageRiskScore <= 30 ? "HEALTHY"
                : averageRiskScore <= 55 ? "MODERATE"
                : averageRiskScore <= 75 ? "CONCERNING"
                : "CRITICAL";

        // ── Build response ─────────────────────────────────────────────
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date",              targetDate);
        response.put("companyId",         companyId);
        response.put("totalEmployees",    totalEmployees);
        response.put("averageRiskScore",  averageRiskScore);
        response.put("highRiskCount",     highRiskCount);
        response.put("checkInsToday",     checkInsToday);
        response.put("riskDistribution",  riskDistribution);
        response.put("trendSummary",      trendSummary);
        response.put("topStressors",      topStressors);
        response.put("teamHealthStatus",  teamHealthStatus);
        response.put("totalScored",       scores.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns HIGH and CRITICAL risk employees for HR to follow up.
     */
    @GetMapping("/{companyId}/alerts")
    @Operation(
            summary = "Get HIGH and CRITICAL risk alerts",
            description = "Returns named employee alerts for HIGH and CRITICAL risk levels."
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
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("employeeId",       score.getEmployeeId());
            alert.put("employeeName",     emp.map(Employee::getFullName).orElse("Team Member"));
            alert.put("riskLevel",        score.getRiskLevel());
            alert.put("riskScore",        score.getRiskScore());
            alert.put("trendDirection",   score.getTrendDirection());
            alert.put("primaryStressors", score.getPrimaryStressors() != null
                    ? score.getPrimaryStressors() : List.of());
            alert.put("aiReasoning",      score.getAiReasoning());
            return alert;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "date",       LocalDate.now(),
                "alertCount", alerts.size(),
                "alerts",     alerts
        ));
    }

    /**
     * Trigger on-demand burnout scoring for a single employee.
     */
    @PostMapping("/score/{employeeId}")
    @Operation(
            summary = "Trigger on-demand burnout scoring",
            description = "Manually runs the Pattern Agent for a single employee."
    )
    public ResponseEntity<?> triggerScoring(@PathVariable UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        BurnoutScore score = burnoutScoringService.scoreEmployee(
                employeeId, employee.getCompanyId()
        );

        return ResponseEntity.ok(Map.of(
                "employeeId", employeeId,
                "riskLevel",  score.getRiskLevel(),
                "riskScore",  score.getRiskScore(),
                "trend",      score.getTrendDirection()
        ));
    }



    /**
     * Wellness pulse — energy, support index, burnout risk from today's check-ins.
     */
    @GetMapping("/{companyId}/wellness-pulse")
    @Operation(
            summary = "Wellness pulse metrics",
            description = "Returns avg energy level, team support index, and burnout risk from today's check-ins. Scaled 0-100."
    )
    public ResponseEntity<?> getWellnessPulse(@PathVariable UUID companyId) {
        return ResponseEntity.ok(wellnessPulseService.getPulse(companyId));
    }

}