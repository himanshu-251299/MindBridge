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
import java.util.UUID;

/**
 * PatternAgent — The second AI agent in MindBridge.
 *
 * Analyzes 14 days of check-in history for an employee and
 * produces a burnout risk assessment using Gemini.
 *
 * Responsibilities:
 *  - Identify trend patterns (declining energy, increasing stress keywords)
 *  - Compute a risk score (0–100) and risk level (LOW/MEDIUM/HIGH/CRITICAL)
 *  - Explain the reasoning in plain language for HR
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PatternAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String PATTERN_SYSTEM_PROMPT = """
        You are a workplace wellness analyst specializing in early burnout detection.
        You will receive 14 days of employee check-in data and must analyze it for burnout risk.

        ANALYSIS CRITERIA — weight each factor:
        1. Energy score trend (30%): Declining over 3+ consecutive days is a strong signal
        2. Workload tags (25%): "overwhelming" and "drowning" are high-risk indicators
        3. Team support trend (20%): Scores below 5, especially if declining, indicate isolation
        4. Flagged keywords (15%): Frequency of stress-related keywords
        5. Missing check-ins (10%): Skipping check-ins can be an avoidance/disengagement signal

        RISK LEVELS:
        - LOW (0–30):      Minor fluctuations, employee appears well
        - MEDIUM (31–55):  Some stress signals, worth monitoring, send supportive nudge
        - HIGH (56–75):    Clear stress patterns, HR should be informed anonymously
        - CRITICAL (76–100): Acute risk, urgent HR follow-up recommended

        OUTPUT RULES:
        Respond ONLY with a JSON object in this exact format — no preamble, no markdown:
        {
          "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
          "riskScore": <integer 0-100>,
          "trendDirection": "IMPROVING|STABLE|DECLINING",
          "primaryStressors": ["<stressor1>", "<stressor2>"],
          "aiReasoning": "<2-3 sentence plain English explanation for HR, referring to the employee anonymously as 'this team member'>",
          "recommendedAction": "<one specific action HR or the system should take>"
        }
        """;

    /**
     * Analyzes an employee's check-in history and returns a burnout risk assessment.
     */
    public BurnoutRiskResult analyzePattern(UUID employeeId, UUID companyId,
                                            List<CheckIn> checkInHistory) {
        log.info("Running pattern analysis for employee: {} ({} check-ins)",
                 employeeId, checkInHistory.size());

        if (checkInHistory.isEmpty()) {
            log.warn("No check-in history for employee: {}", employeeId);
            return buildNoDataResult(employeeId, companyId);
        }

        String historyJson = serializeHistory(checkInHistory);

        String aiResponse = chatClient.prompt()
            .system(PATTERN_SYSTEM_PROMPT)
            .user("Analyze this check-in history for burnout risk:\n\n" + historyJson)
            .call()
            .content();

        log.debug("Pattern Agent response: {}", aiResponse);

        return parseRiskResult(aiResponse, employeeId, companyId);
    }

    private String serializeHistory(List<CheckIn> history) {
        try {
            // Build a clean summary array for the AI
            var summaries = history.stream().map(c -> Map.of(
                "date",              c.getCheckInDate().toString(),
                "energyScore",       c.getEnergyScore() != null ? c.getEnergyScore() : "missing",
                "workloadTag",       c.getWorkloadTag() != null ? c.getWorkloadTag() : "missing",
                "teamSupportScore",  c.getTeamSupportScore() != null ? c.getTeamSupportScore() : "missing",
                "flaggedKeywords",   c.getFlaggedKeywords() != null ? c.getFlaggedKeywords() : List.of()
            )).toList();

            return objectMapper.writeValueAsString(summaries);
        } catch (Exception e) {
            log.error("Failed to serialize check-in history: {}", e.getMessage());
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private BurnoutRiskResult parseRiskResult(String aiResponse, UUID employeeId, UUID companyId) {
        try {
            String jsonBlock = aiResponse.trim();
            // Strip markdown code blocks if Gemini adds them
            if (jsonBlock.contains("```")) {
                jsonBlock = jsonBlock.replaceAll("```json|```", "").trim();
            }

            Map<String, Object> parsed = objectMapper.readValue(jsonBlock, Map.class);

            BurnoutScore.RiskLevel riskLevel = BurnoutScore.RiskLevel.valueOf(
                String.valueOf(parsed.getOrDefault("riskLevel", "LOW")).toUpperCase()
            );

            BurnoutScore.TrendDirection trend = BurnoutScore.TrendDirection.valueOf(
                String.valueOf(parsed.getOrDefault("trendDirection", "STABLE")).toUpperCase()
            );

            List<String> stressors = parsed.containsKey("primaryStressors")
                ? (List<String>) parsed.get("primaryStressors")
                : List.of();

            return BurnoutRiskResult.builder()
                .employeeId(employeeId)
                .companyId(companyId)
                .riskLevel(riskLevel)
                .riskScore(toInt(parsed.get("riskScore"), 10))
                .trendDirection(trend)
                .primaryStressors(stressors)
                .aiReasoning(String.valueOf(parsed.getOrDefault("aiReasoning", "")))
                .recommendedAction(String.valueOf(parsed.getOrDefault("recommendedAction", "")))
                .build();

        } catch (Exception e) {
            log.error("Failed to parse Pattern Agent response: {}", e.getMessage());
            return buildDefaultResult(employeeId, companyId);
        }
    }

    private BurnoutRiskResult buildNoDataResult(UUID employeeId, UUID companyId) {
        return BurnoutRiskResult.builder()
            .employeeId(employeeId).companyId(companyId)
            .riskLevel(BurnoutScore.RiskLevel.LOW).riskScore(0)
            .trendDirection(BurnoutScore.TrendDirection.STABLE)
            .primaryStressors(List.of())
            .aiReasoning("No check-in data available for analysis.")
            .recommendedAction("Encourage employee to complete their first check-in.")
            .build();
    }

    private BurnoutRiskResult buildDefaultResult(UUID employeeId, UUID companyId) {
        return BurnoutRiskResult.builder()
            .employeeId(employeeId).companyId(companyId)
            .riskLevel(BurnoutScore.RiskLevel.MEDIUM).riskScore(50)
            .trendDirection(BurnoutScore.TrendDirection.STABLE)
            .primaryStressors(List.of("parse-error"))
            .aiReasoning("Analysis parsing failed — manual review recommended.")
            .recommendedAction("Review raw check-in data manually.")
            .build();
    }

    private int toInt(Object value, int defaultVal) {
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { return defaultVal; }
    }

    // ─────────────────────────────────────────────────
    // Result DTO
    // ─────────────────────────────────────────────────
    @lombok.Builder
    @lombok.Getter
    public static class BurnoutRiskResult {
        private UUID employeeId;
        private UUID companyId;
        private BurnoutScore.RiskLevel riskLevel;
        private int riskScore;
        private BurnoutScore.TrendDirection trendDirection;
        private List<String> primaryStressors;
        private String aiReasoning;
        private String recommendedAction;
    }
}