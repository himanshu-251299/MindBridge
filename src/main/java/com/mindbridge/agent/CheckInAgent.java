package com.mindbridge.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.model.CheckIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CheckInAgent — The first AI agent in MindBridge.
 *
 * Conducts a warm, conversational daily wellness check-in with employees.
 * Uses Google Gemini via Spring AI's ChatClient.
 *
 * Responsibilities:
 *  - Engage the employee in a 3-question wellness conversation
 *  - Extract structured data (scores, tags, sentiment)
 *  - Return a populated CheckIn object ready for storage
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String CHECKIN_SYSTEM_PROMPT = """
        You are Bridge, a warm and empathetic wellness companion for a workplace wellbeing app.
        Your role is to conduct a brief, friendly daily check-in with an employee.

        CONVERSATION FLOW:
        1. Greet the employee warmly by name (if provided)
        2. Ask about their energy level today on a scale of 1–10
        3. Ask them to describe their current workload in one word
           (guide them: manageable / busy / heavy / overwhelming / drowning)
        4. Ask if they feel supported by their team today (scale of 1–10)
        5. Wrap up with a kind, personalized note based on their responses

        TONE RULES:
        - Always warm, never clinical or robotic
        - Never diagnose, analyze, or alarm the employee
        - Keep the total conversation under 5 messages
        - If the employee shares something concerning, acknowledge it empathetically
          and let them know their HR team cares — but never create panic

        OUTPUT RULES:
        After the conversation, output ONLY a JSON block in this exact format:
        {
          "energyScore": <integer 1-10>,
          "workloadTag": "<single word>",
          "teamSupportScore": <integer 1-10>,
          "rawSentiment": "<2-3 sentence summary of how the employee seems to be doing>",
          "flaggedKeywords": ["<word1>", "<word2>"],
          "conversationSummary": "<what happened in this check-in>"
        }

        Flag keywords like: exhausted, burnout, overwhelmed, crying, quitting, stressed,
        deadline, pressure, conflict, unfair, ignored, anxiety, panic.
        """;

    /**
     * Conducts a single-turn check-in based on the employee's message.
     * For MVP, this is a single exchange. Future versions will support multi-turn.
     */
    public CheckInResult processCheckIn(UUID employeeId, UUID companyId,
                                        String employeeName, String employeeMessage) {
        log.info("Processing check-in for employee: {}", employeeId);

        String userPrompt = String.format(
            "Employee name: %s\nEmployee message: %s\n\n" +
            "Conduct the check-in and end with the JSON output block.",
            employeeName, employeeMessage
        );

        String aiResponse = chatClient.prompt()
            .system(CHECKIN_SYSTEM_PROMPT)
            .user(userPrompt)
            .call()
            .content();

        log.debug("Gemini check-in response: {}", aiResponse);

        return parseCheckInResult(aiResponse, employeeId, companyId, aiResponse);
    }

    /**
     * Generates the opening check-in message (for Slack bot to send to employee).
     */
    public String generateCheckInPrompt(String employeeName) {
        return chatClient.prompt()
            .system(CHECKIN_SYSTEM_PROMPT)
            .user("Start a check-in conversation for employee named: " + employeeName +
                  ". Just send the opening greeting and first question. Do NOT output JSON yet.")
            .call()
            .content();
    }

    /**
     * Parses Gemini's JSON response into a structured CheckInResult.
     * Falls back gracefully if JSON parsing fails.
     */
    @SuppressWarnings("unchecked")
    private CheckInResult parseCheckInResult(String aiResponse, UUID employeeId,
                                             UUID companyId, String fullConversation) {
        try {
            // Extract JSON block from the response
            int jsonStart = aiResponse.lastIndexOf("{");
            int jsonEnd   = aiResponse.lastIndexOf("}") + 1;

            if (jsonStart == -1 || jsonEnd == 0) {
                log.warn("No JSON block found in Gemini response, using defaults");
                return buildDefaultResult(employeeId, companyId, fullConversation);
            }

            String jsonBlock = aiResponse.substring(jsonStart, jsonEnd);
            Map<String, Object> parsed = objectMapper.readValue(jsonBlock, Map.class);

            List<String> flaggedKeywords = parsed.containsKey("flaggedKeywords")
                ? (List<String>) parsed.get("flaggedKeywords")
                : List.of();

            return CheckInResult.builder()
                .employeeId(employeeId)
                .companyId(companyId)
                .energyScore(toInt(parsed.get("energyScore"), 5))
                .workloadTag(toString(parsed.get("workloadTag"), "manageable"))
                .teamSupportScore(toInt(parsed.get("teamSupportScore"), 5))
                .rawSentiment(toString(parsed.get("rawSentiment"), "No sentiment extracted"))
                .flaggedKeywords(flaggedKeywords)
                .aiConversation(fullConversation)
                .build();

        } catch (Exception e) {
            log.error("Failed to parse Gemini check-in JSON: {}", e.getMessage());
            return buildDefaultResult(employeeId, companyId, fullConversation);
        }
    }

    private CheckInResult buildDefaultResult(UUID employeeId, UUID companyId, String conversation) {
        return CheckInResult.builder()
            .employeeId(employeeId)
            .companyId(companyId)
            .energyScore(5)
            .workloadTag("unknown")
            .teamSupportScore(5)
            .rawSentiment("Parse error — manual review needed")
            .flaggedKeywords(List.of())
            .aiConversation(conversation)
            .build();
    }

    private int toInt(Object value, int defaultVal) {
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { return defaultVal; }
    }

    private String toString(Object value, String defaultVal) {
        return value != null ? String.valueOf(value) : defaultVal;
    }

    // ─────────────────────────────────────────────────
    // Result DTO (inner record for clean returns)
    // ─────────────────────────────────────────────────
    @lombok.Builder
    @lombok.Getter
    public static class CheckInResult {
        private UUID employeeId;
        private UUID companyId;
        private int energyScore;
        private String workloadTag;
        private int teamSupportScore;
        private String rawSentiment;
        private List<String> flaggedKeywords;
        private String aiConversation;
    }
}