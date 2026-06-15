package com.mindbridge.controller;

import com.mindbridge.dto.AuthDtos;
import com.mindbridge.dto.EmployeeDtos;
import com.mindbridge.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee self-registration, login, profile and check-in history")
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * Employee self-registration using HR-provided invite code.
     *
     * Request:
     * {
     *   "inviteCode": "MB-X7K2P9",
     *   "fullName": "Alice Johnson",
     *   "email": "alice@company.com",
     *   "password": "mypassword123"
     * }
     */
    @PostMapping("/register")
    @Operation(
        summary = "Employee self-registration",
        description = "Register as an employee using your company's invite code. " +
                      "Get the invite code from your HR manager. Returns a JWT token immediately."
    )
    public ResponseEntity<?> register(@RequestBody EmployeeDtos.RegisterRequest request) {
        try {
            EmployeeDtos.AuthResponse response = employeeService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                AuthDtos.ErrorResponse.builder()
                    .error("REGISTRATION_FAILED")
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Employee login.
     *
     * Request:
     * {
     *   "email": "alice@company.com",
     *   "password": "mypassword123"
     * }
     */
    @PostMapping("/login")
    @Operation(
        summary = "Employee login",
        description = "Login with your email and password. Returns a JWT token to use for check-ins."
    )
    public ResponseEntity<?> login(@RequestBody EmployeeDtos.LoginRequest request) {
        try {
            EmployeeDtos.AuthResponse response = employeeService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(
                AuthDtos.ErrorResponse.builder()
                    .error("INVALID_CREDENTIALS")
                    .message("Invalid email or password")
                    .build()
            );
        }
    }

    /**
     * Get the logged-in employee's own profile.
     * Employees can only see their own data — not other employees.
     */
    @GetMapping("/me")
    @Operation(
        summary = "Get my profile",
        description = "Returns the logged-in employee's profile. Requires employee JWT token."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            EmployeeDtos.ProfileResponse profile = employeeService.getProfile(userDetails.getUsername());
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get the logged-in employee's check-in history (last 30 days).
     */
    @GetMapping("/me/checkins")
    @Operation(
        summary = "Get my check-in history",
        description = "Returns the last 30 days of check-ins for the logged-in employee."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> getMyCheckIns(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            var history = employeeService.getCheckInHistory(userDetails.getUsername());
            return ResponseEntity.ok(Map.of(
                "count",    history.size(),
                "checkins", history
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * HR Manager endpoint — get the company invite code.
     * Used so HR can share the code with new employees.
     */
    @GetMapping("/invite-code/{companyId}")
    @Operation(
        summary = "Get company invite code",
        description = "Returns the invite code HR managers share with new employees. " +
                      "Requires HR_MANAGER JWT token."
    )
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

    /**
     * HR Manager endpoint — regenerate the company invite code.
     * Use this if the old code was shared with the wrong person.
     */
    @PostMapping("/invite-code/{companyId}/regenerate")
    @Operation(
        summary = "Regenerate invite code",
        description = "Generates a new invite code for the company. The old code will stop working immediately."
    )
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
}