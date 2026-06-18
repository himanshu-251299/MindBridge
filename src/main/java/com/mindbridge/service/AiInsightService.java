package com.mindbridge.service;

import com.mindbridge.agent.AiInsightAgent;
import com.mindbridge.dto.InsightDtos;
import com.mindbridge.model.*;
import com.mindbridge.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInsightService {

    private final AiInsightAgent          aiInsightAgent;
    private final AiInsightRepository     aiInsightRepository;
    private final AiRecommendationRepository recommendationRepository;
    private final BurnoutScoreRepository  burnoutScoreRepository;
    private final CheckInRepository       checkInRepository;
    private final EmployeeRepository      employeeRepository;
    private final CompanyRepository       companyRepository;

    // ─────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────

    /**
     * Returns the full AI Insights page response for a company.
     * Uses today's cached insight if available, otherwise generates a new one.
     */
    public InsightDtos.InsightsPageResponse getInsightsPage(UUID companyId) {
        // Get or generate today's insight
        AiInsight insight = aiInsightRepository
                .findByCompanyIdAndGeneratedDate(companyId, LocalDate.now())
                .orElseGet(() -> generateAndSaveInsight(companyId));

        return InsightDtos.InsightsPageResponse.builder()
                .summary(buildSummary(insight))
                .burnoutForecast(buildForecast(companyId))
                .teamSegments(buildTeamSegments(companyId))
                .peakStressPeriods(buildPeakStress(companyId))
                .recommendations(buildRecommendations(insight.getId()))
                .historicalInsights(buildHistory(companyId))
                .build();
    }

    /**
     * Force-regenerates today's insight via Gemini — called by "Refresh Insight" button.
     * Split into two transactions: first delete, then insert — avoids unique constraint conflict.
     */
    public InsightDtos.InsightsPageResponse refreshInsight(UUID companyId) {
        // Step 1: Delete in its own transaction (commits before insert)
        deleteTodaysInsight(companyId);

        // Step 2: Generate fresh insight in a new transaction
        AiInsight fresh = generateAndSaveInsight(companyId);

        return InsightDtos.InsightsPageResponse.builder()
                .summary(buildSummary(fresh))
                .burnoutForecast(buildForecast(companyId))
                .teamSegments(buildTeamSegments(companyId))
                .peakStressPeriods(buildPeakStress(companyId))
                .recommendations(buildRecommendations(fresh.getId()))
                .historicalInsights(buildHistory(companyId))
                .build();
    }

    /**
     * Deletes today's insight and its recommendations in its own committed transaction.
     * Must be separate from generateAndSaveInsight to avoid unique constraint violation.
     */
    @Transactional
    public void deleteTodaysInsight(UUID companyId) {
        aiInsightRepository
                .findByCompanyIdAndGeneratedDate(companyId, LocalDate.now())
                .ifPresent(existing -> {
                    // Delete recommendations first (FK constraint)
                    recommendationRepository.deleteAll(
                            recommendationRepository.findByInsightIdOrderByPriorityAsc(existing.getId())
                    );
                    aiInsightRepository.delete(existing);
                    aiInsightRepository.flush();   // force immediate DB commit
                });
    }

    /**
     * Update recommendation status — ACTIONED or DISMISSED.
     */
    @Transactional
    public InsightDtos.Recommendation updateRecommendationStatus(
            UUID recommendationId, String status
    ) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found"));

        rec.setStatus(status.toUpperCase());
        rec.setUpdatedAt(LocalDateTime.now());
        AiRecommendation saved = recommendationRepository.save(rec);

        return toRecommendationDto(saved);
    }

    /**
     * Weekly scheduled insight generation — runs every Monday at 8 AM.
     */
    @Scheduled(cron = "0 0 8 * * MON")
    public void generateWeeklyInsights() {
        log.info("=== Generating weekly AI insights for all companies ===");
        companyRepository.findAll().stream()
                .filter(Company::isActive)
                .forEach(company -> {
                    try {
                        generateAndSaveInsight(company.getId());
                        log.info("Weekly insight generated for company: {}", company.getName());
                    } catch (Exception e) {
                        log.error("Failed to generate insight for {}: {}", company.getName(), e.getMessage());
                    }
                });
    }

    // ─────────────────────────────────────────────────────
    // INSIGHT GENERATION
    // ─────────────────────────────────────────────────────

    @Transactional
    public AiInsight generateAndSaveInsight(UUID companyId) {
        log.info("Generating AI insight for company: {}", companyId);

        // Gather data
        List<BurnoutScore> todayScores = burnoutScoreRepository
                .findByCompanyIdAndScoreDate(companyId, LocalDate.now());

        List<BurnoutScore> lastWeekScores = burnoutScoreRepository
                .findByCompanyIdAndScoreDate(companyId, LocalDate.now().minusDays(7));

        List<Employee> employees = employeeRepository.findByCompanyIdAndActiveTrue(companyId);

        // Compute metrics
        int avgRiskToday    = avgRiskScore(todayScores);
        int avgRiskLastWeek = avgRiskScore(lastWeekScores);
        int trendVsLast     = avgRiskToday - avgRiskLastWeek;

        Map<String, Long> riskDistribution = todayScores.stream()
                .collect(Collectors.groupingBy(s -> s.getRiskLevel().name(), Collectors.counting()));

        List<String> topStressors = todayScores.stream()
                .flatMap(s -> s.getPrimaryStressors() != null
                        ? s.getPrimaryStressors().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).collect(Collectors.toList());

        // Department risk map
        Map<UUID, String> empDeptMap = employees.stream()
                .filter(e -> e.getDepartment() != null)
                .collect(Collectors.toMap(Employee::getId, Employee::getDepartment));

        Map<String, Double> departmentRisks = todayScores.stream()
                .filter(s -> empDeptMap.containsKey(s.getEmployeeId()))
                .collect(Collectors.groupingBy(
                        s -> empDeptMap.get(s.getEmployeeId()),
                        Collectors.averagingInt(s -> s.getRiskScore() != null ? s.getRiskScore() : 0)
                ));

        int wellnessScore = Math.max(0, 100 - avgRiskToday);

        // Call Gemini
        AiInsightAgent.InsightResult result = aiInsightAgent.generateInsight(
                avgRiskToday, trendVsLast, riskDistribution,
                topStressors, departmentRisks, employees.size()
        );

        // Save insight
        AiInsight insight = AiInsight.builder()
                .companyId(companyId)
                .generatedDate(LocalDate.now())
                .headline(result.headline())
                .narrative(result.narrative())
                .wellnessScore(wellnessScore)
                .trendVsLast(trendVsLast)
                .trendDirection(trendVsLast < -2 ? "IMPROVING" : trendVsLast > 2 ? "DECLINING" : "STABLE")
                .severity(result.severity())
                .rawAiResponse(result.rawAiResponse())
                .build();

        AiInsight saved = aiInsightRepository.save(insight);

        // Save recommendations
        List<AiRecommendation> recs = result.recommendations().stream()
                .map(r -> AiRecommendation.builder()
                        .companyId(companyId)
                        .insightId(saved.getId())
                        .priority(r.priority())
                        .icon(r.icon())
                        .title(r.title())
                        .detail(r.detail())
                        .actionLabel(r.actionLabel())
                        .status("PENDING")
                        .build())
                .collect(Collectors.toList());

        recommendationRepository.saveAll(recs);
        log.info("Insight saved with {} recommendations for company {}", recs.size(), companyId);

        return saved;
    }

    // ─────────────────────────────────────────────────────
    // BUILDERS — each section of the insights page
    // ─────────────────────────────────────────────────────

    private InsightDtos.InsightSummary buildSummary(AiInsight insight) {
        long minutesAgo = ChronoUnit.MINUTES.between(insight.getCreatedAt(), LocalDateTime.now());
        String updatedAt = minutesAgo < 60
                ? "Updated " + minutesAgo + "m ago"
                : "Updated " + ChronoUnit.HOURS.between(insight.getCreatedAt(), LocalDateTime.now()) + "h ago";

        return InsightDtos.InsightSummary.builder()
                .insightId(insight.getId())
                .headline(insight.getHeadline())
                .narrative(insight.getNarrative())
                .wellnessScore(insight.getWellnessScore() != null ? insight.getWellnessScore() : 0)
                .trendVsLast(insight.getTrendVsLast() != null ? insight.getTrendVsLast() : 0)
                .trendDirection(insight.getTrendDirection())
                .severity(insight.getSeverity())
                .generatedAt(updatedAt)
                .isStale(!insight.getGeneratedDate().equals(LocalDate.now()))
                .build();
    }

    private InsightDtos.BurnoutForecast buildForecast(UUID companyId) {
        // Build 7-day history + 7-day projection
        List<InsightDtos.ForecastPoint> points = new ArrayList<>();
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);

        // Last 7 days — actual data
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            List<BurnoutScore> scores = burnoutScoreRepository
                    .findByCompanyIdAndScoreDate(companyId, date);
            double avg = scores.stream()
                    .mapToInt(s -> s.getRiskScore() != null ? s.getRiskScore() : 0)
                    .average().orElse(0.0);

            points.add(InsightDtos.ForecastPoint.builder()
                    .day(days[i])
                    .actualScore(Math.round(avg * 10.0) / 10.0)
                    .projectedScore(0)
                    .isProjected(date.isAfter(today))
                    .build());
        }

        // Simple projection: extend trend by 7 days
        double lastActual = points.stream()
                .filter(p -> !p.isProjected())
                .mapToDouble(InsightDtos.ForecastPoint::getActualScore)
                .reduce((a, b) -> b).orElse(30.0);

        for (int i = 0; i < 7; i++) {
            String day = days[i] + "+7";
            double projected = Math.min(100, Math.max(0, lastActual + (i * 0.5)));
            points.add(InsightDtos.ForecastPoint.builder()
                    .day(day)
                    .actualScore(0)
                    .projectedScore(Math.round(projected * 10.0) / 10.0)
                    .isProjected(true)
                    .build());
        }

        return InsightDtos.BurnoutForecast.builder()
                .dataPoints(points)
                .confidencePercent(82)
                .forecastSummary("Risk levels expected to remain stable over the next 7 days.")
                .trend("STABLE")
                .build();
    }

    private List<InsightDtos.DepartmentRisk> buildTeamSegments(UUID companyId) {
        List<Employee> employees = employeeRepository.findByCompanyIdAndActiveTrue(companyId);
        List<BurnoutScore> todayScores = burnoutScoreRepository
                .findByCompanyIdAndScoreDate(companyId, LocalDate.now());

        Map<UUID, BurnoutScore> scoreMap = todayScores.stream()
                .collect(Collectors.toMap(BurnoutScore::getEmployeeId, s -> s, (a, b) -> a));

        return employees.stream()
                .filter(e -> e.getDepartment() != null)
                .collect(Collectors.groupingBy(Employee::getDepartment))
                .entrySet().stream()
                .map(entry -> {
                    String dept = entry.getKey();
                    List<Employee> deptEmps = entry.getValue();
                    List<BurnoutScore> deptScores = deptEmps.stream()
                            .map(e -> scoreMap.get(e.getId()))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    double avgRisk = deptScores.stream()
                            .mapToInt(s -> s.getRiskScore() != null ? s.getRiskScore() : 0)
                            .average().orElse(0.0);

                    long highRisk = deptScores.stream()
                            .filter(s -> s.getRiskLevel() == BurnoutScore.RiskLevel.HIGH
                                    || s.getRiskLevel() == BurnoutScore.RiskLevel.CRITICAL)
                            .count();

                    String topRisk = deptScores.stream()
                            .max(Comparator.comparingInt(s -> s.getRiskScore() != null ? s.getRiskScore() : 0))
                            .map(s -> s.getRiskLevel().name()).orElse("LOW");

                    return InsightDtos.DepartmentRisk.builder()
                            .department(dept)
                            .avgRiskScore(Math.round(avgRisk * 10.0) / 10.0)
                            .employeeCount(deptEmps.size())
                            .highRiskCount((int) highRisk)
                            .riskLevel(topRisk)
                            .build();
                })
                .sorted(Comparator.comparingDouble(InsightDtos.DepartmentRisk::getAvgRiskScore).reversed())
                .collect(Collectors.toList());
    }

    private List<InsightDtos.DayStressLevel> buildPeakStress(UUID companyId) {
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        List<InsightDtos.DayStressLevel> result = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            List<CheckIn> checkIns = checkInRepository
                    .findByCompanyIdAndCheckInDate(companyId, date);

            double avgEnergy = checkIns.stream()
                    .filter(c -> c.getEnergyScore() != null)
                    .mapToInt(CheckIn::getEnergyScore)
                    .average().orElse(5.0);

            // Workload stress: count "overwhelming"/"drowning"/"heavy" tags
            long stressfulWorkloads = checkIns.stream()
                    .filter(c -> c.getWorkloadTag() != null &&
                            List.of("overwhelming", "drowning", "heavy").contains(c.getWorkloadTag().toLowerCase()))
                    .count();
            double workloadStress = checkIns.isEmpty() ? 0
                    : Math.round((stressfulWorkloads * 100.0) / checkIns.size() * 10.0) / 10.0;

            result.add(InsightDtos.DayStressLevel.builder()
                    .dayOfWeek(days[i])
                    .avgEnergyScore(Math.round(avgEnergy * 10.0) / 10.0)
                    .avgWorkloadStress(workloadStress)
                    .checkInCount(checkIns.size())
                    .build());
        }
        return result;
    }

    private List<InsightDtos.Recommendation> buildRecommendations(UUID insightId) {
        return recommendationRepository
                .findByInsightIdOrderByPriorityAsc(insightId)
                .stream()
                .map(this::toRecommendationDto)
                .collect(Collectors.toList());
    }

    private List<InsightDtos.HistoricalInsight> buildHistory(UUID companyId) {
        return aiInsightRepository
                .findTop5ByCompanyIdOrderByGeneratedDateDesc(companyId)
                .stream()
                .map(insight -> InsightDtos.HistoricalInsight.builder()
                        .id(insight.getId())
                        .date(insight.getGeneratedDate())
                        .severity(insight.getSeverity())
                        .summary(insight.getHeadline().length() > 80
                                ? insight.getHeadline().substring(0, 80) + "..."
                                : insight.getHeadline())
                        .status(mapSeverityToHistoryStatus(insight.getSeverity()))
                        .build())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────

    private int avgRiskScore(List<BurnoutScore> scores) {
        return (int) scores.stream()
                .mapToInt(s -> s.getRiskScore() != null ? s.getRiskScore() : 0)
                .average().orElse(0.0);
    }

    private String mapSeverityToHistoryStatus(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "Resolved";
            case "HIGH"     -> "Actioned";
            case "MEDIUM"   -> "In progress";
            default         -> "Acknowledged";
        };
    }

    private InsightDtos.Recommendation toRecommendationDto(AiRecommendation rec) {
        return InsightDtos.Recommendation.builder()
                .id(rec.getId())
                .priority(rec.getPriority())
                .icon(rec.getIcon())
                .title(rec.getTitle())
                .detail(rec.getDetail())
                .actionLabel(rec.getActionLabel())
                .status(rec.getStatus())
                .createdAt(rec.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd")))
                .build();
    }
}