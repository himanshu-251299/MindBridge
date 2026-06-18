package com.mindbridge.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.model.BurnoutScore;
import com.mindbridge.model.CheckIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AiInsightAgent — Third AI agent in MindBridge.
 *
 * Analyzes company-wide burnout scores and check-in data
 * to generate a narrative insight + prioritized recommendations
 * for the HR manager.
 *
 * Used by: AiInsightService (on-demand + weekly scheduled)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiInsightAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String INSIGHT_SYSTEM_PROMPT = """
        You are an organizational wellness analyst for MindBridge, an AI-powered burnout prevention platform.
        You analyze team-level burnout data and generate actionable insights for HR managers.

        You will receive:
        - Summary stats (avg risk score, distribution, trend vs last week)
        - Top stressors across the team
        - Department-level risk breakdown

        Your job is to generate:
        1. A compelling one-line HEADLINE that captures the most important finding
           (e.g. "Your team is trending healthy, but Engineering shows early-warning signs.")
        2. A 2-3 sentence NARRATIVE explaining the key patterns in plain language
        3. 4 RECOMMENDATIONS prioritized as URGENT/HIGH/MEDIUM

        RULES:
        - Be specific — mention departments or patterns when relevant
        - Never alarm unnecessarily — be constructive and empowering
        - Recommendations must be actionable by an HR manager today
        - Never mention individual employee names — always speak in aggregate

        OUTPUT FORMAT — respond ONLY with this JSON, no markdown, no preamble:
        {
          "headline": "<one compelling sentence>",
          "narrative": "<2-3 sentences of analysis>",
          "severity": "LOW|MEDIUM|HIGH|CRITICAL",
          "recommendations": [
            {
              "priority": "URGENT|HIGH|MEDIUM",
              "icon": "<single emoji>",
              "title": "<short action title>",
              "detail": "<2 sentences explaining why and what to do>",
              "actionLabel": "<button text e.g. Schedule 1:1s>"
            }
          ]
        }
        """;

    /**
     * Generates a team-level wellness insight from aggregated data.
     *
     * @param avgRiskScore      today's average burnout risk score
     * @param trendVsLast       change vs last week (positive = worse)
     * @param riskDistribution  map of LOW/MEDIUM/HIGH/CRITICAL counts
     * @param topStressors      list of top stress keywords
     * @param departmentRisks   map of department → avg risk score
     * @param totalEmployees    total active employees
     */
    public InsightResult generateInsight(
        int avgRiskScore,
        int trendVsLast,
        Map<String, Long> riskDistribution,
        List<String> topStressors,
        Map<String, Double> departmentRisks,
        int totalEmployees
    ) {
        log.info("Generating AI insight for {} employees", totalEmployees);

        String userPrompt = buildPrompt(
            avgRiskScore, trendVsLast, riskDistribution,
            topStressors, departmentRisks, totalEmployees
        );

        String aiResponse = chatClient.prompt()
            .system(INSIGHT_SYSTEM_PROMPT)
            .user(userPrompt)
            .call()
            .content();

        log.debug("Insight Agent response: {}", aiResponse);
        return parseInsightResult(aiResponse);
    }

    private String buildPrompt(
        int avgRiskScore, int trendVsLast,
        Map<String, Long> riskDist, List<String> stressors,
        Map<String, Double> deptRisks, int total
    ) {
        return String.format("""
            Analyze this team wellness data and generate an insight:

            TEAM OVERVIEW:
            - Total employees: %d
            - Average burnout risk score: %d/100
            - Change vs last week: %+d points
            - Risk breakdown: LOW=%d, MEDIUM=%d, HIGH=%d, CRITICAL=%d

            TOP STRESSORS THIS WEEK:
            %s

            DEPARTMENT RISK SCORES (avg 0-100):
            %s

            Generate the insight JSON now.
            """,
            total, avgRiskScore, trendVsLast,
            riskDist.getOrDefault("LOW", 0L),
            riskDist.getOrDefault("MEDIUM", 0L),
            riskDist.getOrDefault("HIGH", 0L),
            riskDist.getOrDefault("CRITICAL", 0L),
            stressors.isEmpty() ? "None detected" : String.join(", ", stressors),
            deptRisks.isEmpty() ? "No department data" :
                deptRisks.entrySet().stream()
                    .map(e -> "- " + e.getKey() + ": " + Math.round(e.getValue()))
                    .reduce("", (a, b) -> a + "\n" + b)
        );
    }

    @SuppressWarnings("unchecked")
    private InsightResult parseInsightResult(String aiResponse) {
        try {
            String json = aiResponse.trim();
            if (json.contains("```")) {
                json = json.replaceAll("```json|```", "").trim();
            }

            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            List<Map<String, Object>> rawRecs =
                (List<Map<String, Object>>) parsed.getOrDefault("recommendations", List.of());

            List<RecommendationResult> recs = rawRecs.stream().map(r ->
                new RecommendationResult(
                    String.valueOf(r.getOrDefault("priority", "MEDIUM")),
                    String.valueOf(r.getOrDefault("icon", "💡")),
                    String.valueOf(r.getOrDefault("title", "")),
                    String.valueOf(r.getOrDefault("detail", "")),
                    String.valueOf(r.getOrDefault("actionLabel", "Take action"))
                )
            ).toList();

            return new InsightResult(
                String.valueOf(parsed.getOrDefault("headline", "Team wellness analysis complete.")),
                String.valueOf(parsed.getOrDefault("narrative", "Analysis is available.")),
                String.valueOf(parsed.getOrDefault("severity", "LOW")),
                recs,
                aiResponse
            );

        } catch (Exception e) {
            log.error("Failed to parse insight response: {}", e.getMessage());
            return buildFallbackInsight();
        }
    }

    private InsightResult buildFallbackInsight() {
        return new InsightResult(
            "Team wellness analysis is ready.",
            "Your team's wellness data has been analyzed. Check the risk distribution for details.",
            "LOW",
            List.of(
                new RecommendationResult("MEDIUM", "📊",
                    "Review team check-in completion",
                    "Ensure all employees complete their daily check-ins for accurate risk detection.",
                    "View check-ins")
            ),
            "Fallback — AI parsing failed"
        );
    }

    // ── Result records ──────────────────────────────────
    public record InsightResult(
        String headline,
        String narrative,
        String severity,
        List<RecommendationResult> recommendations,
        String rawAiResponse
    ) {}

    public record RecommendationResult(
        String priority,
        String icon,
        String title,
        String detail,
        String actionLabel
    ) {}
}