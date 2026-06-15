package com.mindbridge.controller;

import com.mindbridge.model.CheckIn;
import com.mindbridge.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * CheckInController — REST endpoints for employee check-ins.
 *
 * POST /api/checkin          → Submit a check-in message
 * GET  /api/checkin/prompt   → Get opening check-in message for Slack bot
 */
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
@Tag(name = "Check-in", description = "Employee daily wellness check-in via AI agent")
public class CheckInController {

    private final CheckInService checkInService;

    /**
     * Submit a check-in.
     *
     * Request body:
     * {
     *   "employeeId": "uuid",
     *   "message": "I'm feeling quite tired today, lots of deadlines"
     * }
     */
    @PostMapping
    @Operation(
            summary = "Submit a daily check-in",
            description = "Sends the employee's message through the Gemini-powered Check-in Agent. " +
                    "Extracts energy score, workload tag, sentiment, and flagged keywords. " +
                    "One check-in allowed per employee per day."
    )
    public ResponseEntity<?> submitCheckIn(@RequestBody CheckInRequest request) {
        try {
            CheckIn result = checkInService.processAndSaveCheckIn(
                    UUID.fromString(request.employeeId()),
                    request.message()
            );
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "checkInId", result.getId(),
                    "date", result.getCheckInDate(),
                    "message", "Check-in recorded. Thank you for checking in today! 🌱"
            ));
        } catch (IllegalStateException e) {
            // Already checked in today
            return ResponseEntity.ok(Map.of(
                    "status", "already_done",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Failed to process check-in: " + e.getMessage()
            ));
        }
    }

    /**
     * Get the AI-generated opening message for a check-in.
     * Used by the Slack bot to greet the employee.
     */
    @GetMapping("/prompt/{employeeId}")
    @Operation(
            summary = "Get AI opening check-in message",
            description = "Returns a personalized Gemini-generated greeting to kick off the check-in conversation. Used by the Slack bot."
    )
    public ResponseEntity<?> getCheckInPrompt(@PathVariable String employeeId) {
        String prompt = checkInService.getCheckInOpeningMessage(UUID.fromString(employeeId));
        return ResponseEntity.ok(Map.of("prompt", prompt));
    }

    record CheckInRequest(String employeeId, String message) {}
}