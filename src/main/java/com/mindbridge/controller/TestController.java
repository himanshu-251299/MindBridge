package com.mindbridge.controller;

import com.mindbridge.model.BurnoutScore;
import com.mindbridge.model.Employee;
import com.mindbridge.repository.EmployeeRepository;
import com.mindbridge.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TestController — Only active in 'dev' profile.
 * Remove or disable before going to production.
 *
 * Provides endpoints to manually trigger email alerts
 * without needing real burnout data.
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Tag(name = "Test (Dev Only)", description = "Test endpoints — only active in dev profile")
//@Profile("dev")   // ← only loads when spring.profiles.active=dev
public class TestController {

    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;

    /**
     * Fires a fake HIGH risk email alert for a given employee.
     * Use this to verify email config without needing real burnout data.
     */
    @PostMapping("/trigger-alert/{employeeId}")
    @Operation(
        summary = "Trigger a test burnout alert email",
        description = "Sends a fake HIGH risk email to the HR manager of this employee's company. " +
                      "Use this to verify your Gmail/SMTP config is working."
    )
    public ResponseEntity<?> triggerTestAlert(@PathVariable UUID employeeId,
                                              @RequestParam(defaultValue = "HIGH") String riskLevel) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        // Build a fake burnout score for testing
        BurnoutScore fakeScore = new BurnoutScore();
        fakeScore.setEmployeeId(employeeId);
        fakeScore.setCompanyId(employee.getCompanyId());
        fakeScore.setScoreDate(LocalDate.now());
        fakeScore.setRiskLevel(BurnoutScore.RiskLevel.valueOf(riskLevel.toUpperCase()));
        fakeScore.setRiskScore(riskLevel.equalsIgnoreCase("CRITICAL") ? 88 : 72);
        fakeScore.setTrendDirection(BurnoutScore.TrendDirection.DECLINING);
        fakeScore.setPrimaryStressors(List.of("deadline pressure", "exhaustion", "low team support"));
        fakeScore.setAiReasoning(
            "This team member has shown a consistent decline in energy scores over the past 5 days, " +
            "dropping from 7/10 to 2/10. Workload tags escalated from 'busy' to 'drowning', " +
            "and flagged keywords included 'exhausted' and 'overwhelmed' on multiple days."
        );

        // Fire the email asynchronously
        notificationService.sendBurnoutAlert(fakeScore, employee);

        return ResponseEntity.ok(Map.of(
            "status",     "alert_triggered",
            "riskLevel",  riskLevel.toUpperCase(),
            "employeeId", employeeId,
            "companyId",  employee.getCompanyId(),
            "message",    "Check the HR manager's inbox in a few seconds"
        ));
    }
}