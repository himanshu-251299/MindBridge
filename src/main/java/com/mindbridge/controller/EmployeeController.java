package com.mindbridge.controller;

import com.mindbridge.dto.AuthDtos;
import com.mindbridge.dto.DashboardDtos;
import com.mindbridge.dto.EmployeeDtos;
import com.mindbridge.service.EmployeeListService;
import com.mindbridge.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee registration, login, profile, list and HR management")
public class EmployeeController {

    private final EmployeeService     employeeService;
    private final EmployeeListService employeeListService;

    // ═══════════════════════════════════════════════════
    // EMPLOYEE SELF-SERVICE (no auth required)
    // ═══════════════════════════════════════════════════

    @PostMapping("/register")
    @Operation(
            summary = "Employee self-registration",
            description = "Register as an employee using your company's invite code. Returns a JWT token immediately."
    )
    public ResponseEntity<?> register(@RequestBody EmployeeDtos.RegisterRequest request) {
        try {
            return ResponseEntity.ok(employeeService.register(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    AuthDtos.ErrorResponse.builder()
                            .error("REGISTRATION_FAILED").message(e.getMessage()).build());
        }
    }

    @PostMapping("/login")
    @Operation(
            summary = "Employee login",
            description = "Login with email and password. Returns a JWT token for check-ins."
    )
    public ResponseEntity<?> login(@RequestBody EmployeeDtos.LoginRequest request) {
        try {
            return ResponseEntity.ok(employeeService.login(request));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(
                    AuthDtos.ErrorResponse.builder()
                            .error("INVALID_CREDENTIALS").message("Invalid email or password").build());
        }
    }

    // ═══════════════════════════════════════════════════
    // EMPLOYEE PROFILE (employee JWT required)
    // ═══════════════════════════════════════════════════

    @GetMapping("/me")
    @Operation(summary = "Get my profile", description = "Returns the logged-in employee's profile.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(employeeService.getProfile(userDetails.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me/checkins")
    @Operation(summary = "Get my check-in history", description = "Returns last 30 days of check-ins for the logged-in employee.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getMyCheckIns(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            var history = employeeService.getCheckInHistory(userDetails.getUsername());
            return ResponseEntity.ok(Map.of("count", history.size(), "checkins", history));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════
    // HR MANAGER — INVITE CODE MANAGEMENT
    // ═══════════════════════════════════════════════════

    @GetMapping("/invite-code/{companyId}")
    @Operation(summary = "Get company invite code", description = "Returns invite code HR shares with new employees.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getInviteCode(@PathVariable UUID companyId) {
        return employeeService.getCompanyInviteCode(companyId)
                .map(code -> ResponseEntity.ok(Map.of(
                        "inviteCode", code,
                        "companyId",  companyId,
                        "message",    "Share this code with employees so they can register"
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/invite-code/{companyId}/regenerate")
    @Operation(summary = "Regenerate invite code", description = "Creates a new invite code. Old code stops working immediately.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> regenerateInviteCode(@PathVariable UUID companyId) {
        try {
            String newCode = employeeService.regenerateInviteCode(companyId);
            return ResponseEntity.ok(Map.of(
                    "inviteCode", newCode,
                    "message",    "New invite code generated. Share this with your employees."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════
    // HR MANAGER — EMPLOYEE LIST & STATS (HR JWT required)
    // ═══════════════════════════════════════════════════

    /**
     * Full employee list for the HR dashboard table.
     * Enriched with wellness score, risk level, last check-in, and status.
     * Sorted by risk score descending (highest risk first).
     *
     * Query params:
     *   search     — filter by name, email, or department
     *   risk       — LOW / MEDIUM / HIGH / CRITICAL / ALL
     *   department — filter by department name
     */
    @GetMapping("/{companyId}/list")
    @Operation(
            summary = "Get employee list with wellness data",
            description = "Returns all active employees with burnout score, wellness score, " +
                    "last check-in date and status. Supports search, risk and department filters."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getEmployeeList(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false) String department
    ) {
        List<DashboardDtos.EmployeeListItem> list =
                employeeListService.getEmployeeList(companyId, search, risk, department);
        return ResponseEntity.ok(Map.of(
                "companyId", companyId,
                "total",     list.size(),
                "employees", list
        ));
    }

    /**
     * Employee wellness stats — 4 top cards for the Employees page.
     */
    @GetMapping("/{companyId}/stats")
    @Operation(
            summary = "Get employee wellness stats",
            description = "Returns totalEmployees, healthyCount (LOW), atRiskCount (MEDIUM+HIGH), criticalCount (CRITICAL)."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getEmployeeStats(@PathVariable UUID companyId) {
        return ResponseEntity.ok(employeeListService.getStats(companyId));
    }

    /**
     * Unique department names for the filter dropdown.
     */
    @GetMapping("/{companyId}/departments")
    @Operation(
            summary = "Get department list",
            description = "Returns all unique department names for the filter dropdown."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getDepartments(@PathVariable UUID companyId) {
        return ResponseEntity.ok(Map.of(
                "departments", employeeListService.getDepartments(companyId)
        ));
    }
}