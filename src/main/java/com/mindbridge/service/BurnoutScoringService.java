package com.mindbridge.service;

import com.mindbridge.agent.PatternAgent;
import com.mindbridge.model.BurnoutScore;
import com.mindbridge.model.CheckIn;
import com.mindbridge.model.Employee;
import com.mindbridge.repository.BurnoutScoreRepository;
import com.mindbridge.repository.CheckInRepository;
import com.mindbridge.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BurnoutScoringService {

    private final PatternAgent patternAgent;
    private final EmployeeRepository employeeRepository;
    private final CheckInRepository checkInRepository;
    private final BurnoutScoreRepository burnoutScoreRepository;
    private final NotificationService notificationService;   // ← wired in

    /**
     * Runs every night at 11:30 PM to score all active employees.
     */
    @Scheduled(cron = "0 30 23 * * *")
    @Transactional
    public void runNightlyScoring() {
        log.info("=== MindBridge Nightly Burnout Scoring Started ===");

        List<Employee> allActiveEmployees = employeeRepository.findAll()
                .stream()
                .filter(Employee::isActive)
                .toList();

        int processed = 0, errors = 0, alertsSent = 0;

        for (Employee employee : allActiveEmployees) {
            try {
                BurnoutScore score = scoreEmployee(employee.getId(), employee.getCompanyId());

                // Send alert for HIGH or CRITICAL — async, won't block the loop
                if (score.getRiskLevel() == BurnoutScore.RiskLevel.HIGH ||
                        score.getRiskLevel() == BurnoutScore.RiskLevel.CRITICAL) {
                    notificationService.sendBurnoutAlert(score, employee);
                    alertsSent++;
                }

                processed++;
            } catch (Exception e) {
                log.error("Failed to score employee {}: {}", employee.getId(), e.getMessage());
                errors++;
            }
        }

        log.info("=== Nightly Scoring Complete: {} processed, {} alerts sent, {} errors ===",
                processed, alertsSent, errors);
    }

    /**
     * Scores a single employee — also callable on-demand via the dashboard.
     */
    @Transactional
    public BurnoutScore scoreEmployee(UUID employeeId, UUID companyId) {
        List<CheckIn> history = checkInRepository
                .findByEmployeeIdAndCheckInDateAfterOrderByCheckInDateAsc(
                        employeeId,
                        LocalDate.now().minusDays(14)
                );

        PatternAgent.BurnoutRiskResult result = patternAgent.analyzePattern(
                employeeId, companyId, history
        );

        // Upsert today's score
        BurnoutScore score = burnoutScoreRepository
                .findByEmployeeIdAndScoreDate(employeeId, LocalDate.now())
                .orElse(new BurnoutScore());

        score.setEmployeeId(result.getEmployeeId());
        score.setCompanyId(result.getCompanyId());
        score.setScoreDate(LocalDate.now());
        score.setRiskLevel(result.getRiskLevel());
        score.setRiskScore(result.getRiskScore());
        score.setTrendDirection(result.getTrendDirection());
        score.setPrimaryStressors(result.getPrimaryStressors());
        score.setAiReasoning(result.getAiReasoning());

        BurnoutScore saved = burnoutScoreRepository.save(score);

        log.info("Employee {} scored: {} ({})", employeeId,
                result.getRiskLevel(), result.getRiskScore());

        return saved;
    }
}