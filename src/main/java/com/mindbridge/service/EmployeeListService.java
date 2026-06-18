package com.mindbridge.service;

import com.mindbridge.dto.DashboardDtos;
import com.mindbridge.model.BurnoutScore;
import com.mindbridge.model.CheckIn;
import com.mindbridge.model.Employee;
import com.mindbridge.repository.BurnoutScoreRepository;
import com.mindbridge.repository.CheckInRepository;
import com.mindbridge.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeListService {

    private final EmployeeRepository     employeeRepository;
    private final BurnoutScoreRepository burnoutScoreRepository;
    private final CheckInRepository      checkInRepository;

    /**
     * Returns enriched employee list for the HR dashboard table.
     * Includes wellness score, risk level, last check-in, and status.
     *
     * @param companyId  company to query
     * @param search     optional name/email/dept search
     * @param riskFilter optional risk level filter (LOW/MEDIUM/HIGH/CRITICAL)
     * @param department optional department filter
     */
    public List<DashboardDtos.EmployeeListItem> getEmployeeList(
        UUID companyId, String search, String riskFilter, String department
    ) {
        List<Employee> employees = employeeRepository.findByCompanyIdAndActiveTrue(companyId);

        // Today's burnout scores — build a lookup map by employeeId
        Map<UUID, BurnoutScore> scoreMap = burnoutScoreRepository
            .findByCompanyIdAndScoreDate(companyId, LocalDate.now())
            .stream()
            .collect(Collectors.toMap(BurnoutScore::getEmployeeId, s -> s, (a, b) -> a));

        // Latest check-in date per employee
        Map<UUID, LocalDate> lastCheckInMap = new HashMap<>();
        for (Employee emp : employees) {
            checkInRepository
                .findByEmployeeIdAndCheckInDateAfterOrderByCheckInDateAsc(
                    emp.getId(), LocalDate.now().minusDays(30))
                .stream()
                .max(Comparator.comparing(CheckIn::getCheckInDate))
                .ifPresent(c -> lastCheckInMap.put(emp.getId(), c.getCheckInDate()));
        }

        return employees.stream()
            .map(emp -> buildListItem(emp, scoreMap.get(emp.getId()), lastCheckInMap.get(emp.getId())))
            // ── Apply filters ──
            .filter(item -> search == null || search.isBlank()
                || item.getFullName().toLowerCase().contains(search.toLowerCase())
                || (item.getEmail() != null && item.getEmail().toLowerCase().contains(search.toLowerCase()))
                || (item.getDepartment() != null && item.getDepartment().toLowerCase().contains(search.toLowerCase())))
            .filter(item -> riskFilter == null || riskFilter.isBlank() || riskFilter.equalsIgnoreCase("ALL")
                || item.getRiskLevel().equalsIgnoreCase(riskFilter))
            .filter(item -> department == null || department.isBlank() || department.equalsIgnoreCase("ALL")
                || (item.getDepartment() != null && item.getDepartment().equalsIgnoreCase(department)))
            .sorted(Comparator.comparingInt(DashboardDtos.EmployeeListItem::getRiskScore).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Returns the 4 top stat cards for the employees page.
     */
    public DashboardDtos.EmployeeStats getStats(UUID companyId) {
        List<Employee> employees = employeeRepository.findByCompanyIdAndActiveTrue(companyId);
        int total = employees.size();

        List<BurnoutScore> scores = burnoutScoreRepository
            .findByCompanyIdAndScoreDate(companyId, LocalDate.now());

        Map<UUID, BurnoutScore> scoreMap = scores.stream()
            .collect(Collectors.toMap(BurnoutScore::getEmployeeId, s -> s, (a, b) -> a));

        int healthy  = 0, atRisk = 0, critical = 0, noData = 0;

        for (Employee emp : employees) {
            BurnoutScore score = scoreMap.get(emp.getId());
            if (score == null) { noData++; continue; }
            switch (score.getRiskLevel()) {
                case LOW      -> healthy++;
                case MEDIUM, HIGH -> atRisk++;
                case CRITICAL -> critical++;
            }
        }

        return DashboardDtos.EmployeeStats.builder()
            .totalEmployees(total)
            .healthyCount(healthy)
            .atRiskCount(atRisk)
            .criticalCount(critical)
            .noDataCount(noData)
            .healthyPercent(total > 0 ? Math.round((healthy  * 100.0) / total) : 0)
            .atRiskPercent(total  > 0 ? Math.round((atRisk   * 100.0) / total) : 0)
            .criticalPercent(total > 0 ? Math.round((critical * 100.0) / total) : 0)
            .build();
    }

    /**
     * Returns unique departments for the filter dropdown.
     */
    public List<String> getDepartments(UUID companyId) {
        return employeeRepository.findByCompanyIdAndActiveTrue(companyId)
            .stream()
            .map(Employee::getDepartment)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────

    private DashboardDtos.EmployeeListItem buildListItem(
        Employee emp, BurnoutScore score, LocalDate lastCheckInDate
    ) {
        int riskScore    = score != null && score.getRiskScore()  != null ? score.getRiskScore()  : 0;
        String riskLevel = score != null ? score.getRiskLevel().name() : "NO_DATA";
        int wellnessScore = Math.max(0, 100 - riskScore);   // invert: low risk = high wellness

        String lastCheckIn = formatLastCheckIn(lastCheckInDate);
        String status = computeStatus(riskLevel, lastCheckInDate);
        String initials = buildInitials(emp.getFullName());

        return DashboardDtos.EmployeeListItem.builder()
            .id(emp.getId())
            .fullName(emp.getFullName() != null ? emp.getFullName() : "Unknown")
            .email(emp.getEmail())
            .department(emp.getDepartment())
            .role(emp.getRole())
            .wellnessScore(wellnessScore)
            .riskLevel(riskLevel)
            .riskScore(riskScore)
            .lastCheckIn(lastCheckIn)
            .status(status)
            .initials(initials)
            .build();
    }

    private String formatLastCheckIn(LocalDate date) {
        if (date == null) return "Never";
        long days = ChronoUnit.DAYS.between(date, LocalDate.now());
        if (days == 0) return "Today";
        if (days == 1) return "Yesterday";
        return days + " days ago";
    }

    private String computeStatus(String riskLevel, LocalDate lastCheckIn) {
        if (lastCheckIn == null) return "No Data";
        long daysSince = ChronoUnit.DAYS.between(lastCheckIn, LocalDate.now());
        if ("CRITICAL".equals(riskLevel)) return "Alert";
        if ("HIGH".equals(riskLevel) || daysSince > 3) return "Needs Attention";
        return "Active";
    }

    private String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}