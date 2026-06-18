package com.mindbridge.controller;

import com.mindbridge.dto.InsightDtos;
import com.mindbridge.service.AiInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Tag(name = "AI Insights", description = "AI-powered team wellness insights, recommendations and historical timeline")
public class AiInsightController {

    private final AiInsightService aiInsightService;

    /**
     * Full AI Insights page — single endpoint returns everything the screen needs.
     *
     * Returns:
     * - summary      (hero card: headline, narrative, wellness score, trend)
     * - burnoutForecast (7-day line chart + 7-day projection)
     * - teamSegments    (bar chart by department)
     * - peakStressPeriods (bar chart by day of week)
     * - recommendations   (4 action cards with priority)
     * - historicalInsights (last 5 entries for timeline)
     */
    @GetMapping("/{companyId}")
    @Operation(
        summary = "Get full AI insights page",
        description = "Returns all data needed for the AI Insights screen in one call. " +
                      "Uses cached insight if already generated today, otherwise calls Gemini to generate a new one."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getInsightsPage(@PathVariable UUID companyId) {
        try {
            InsightDtos.InsightsPageResponse response = aiInsightService.getInsightsPage(companyId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error",   "INSIGHT_GENERATION_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Force-regenerate today's insight by calling Gemini again.
     * Triggered by the "Refresh Insight" button on the screen.
     */
    @PostMapping("/{companyId}/refresh")
    @Operation(
        summary = "Refresh AI insight",
        description = "Deletes today's cached insight and regenerates via Gemini. " +
                      "Use when the HR manager clicks 'Refresh Insight' button."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> refreshInsight(@PathVariable UUID companyId) {
        try {
            InsightDtos.InsightsPageResponse response = aiInsightService.refreshInsight(companyId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error",   "REFRESH_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Update a recommendation status — ACTIONED or DISMISSED.
     * Triggered by "Take action" or "Dismiss" buttons on recommendation cards.
     */
    @PatchMapping("/recommendations/{recommendationId}/status")
    @Operation(
        summary = "Update recommendation status",
        description = "Mark a recommendation as ACTIONED or DISMISSED. " +
                      "Body: { \"status\": \"ACTIONED\" | \"DISMISSED\" }"
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> updateRecommendationStatus(
        @PathVariable UUID recommendationId,
        @RequestBody Map<String, String> body
    ) {
        try {
            String status = body.get("status");
            if (status == null || (!status.equalsIgnoreCase("ACTIONED")
                                && !status.equalsIgnoreCase("DISMISSED"))) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error",   "INVALID_STATUS",
                    "message", "Status must be ACTIONED or DISMISSED"
                ));
            }
            InsightDtos.Recommendation updated =
                aiInsightService.updateRecommendationStatus(recommendationId, status);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Manually trigger insight generation for a company.
     * Useful for testing without waiting for the Monday scheduler.
     */
    @PostMapping("/{companyId}/generate")
    @Operation(
        summary = "Manually generate AI insight",
        description = "Forces insight generation via Gemini even if one already exists today. " +
                      "Use in Swagger for testing."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> generateInsight(@PathVariable UUID companyId) {
        try {
            aiInsightService.generateAndSaveInsight(companyId);
            InsightDtos.InsightsPageResponse response = aiInsightService.getInsightsPage(companyId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error",   "GENERATION_FAILED",
                "message", e.getMessage()
            ));
        }
    }
}