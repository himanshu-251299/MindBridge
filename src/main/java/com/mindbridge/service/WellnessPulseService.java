package com.mindbridge.service;

import com.mindbridge.dto.DashboardDtos;
import com.mindbridge.model.CheckIn;
import com.mindbridge.model.Employee;
import com.mindbridge.repository.BurnoutScoreRepository;
import com.mindbridge.repository.CheckInRepository;
import com.mindbridge.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WellnessPulseService {

    private final CheckInRepository    checkInRepository;
    private final BurnoutScoreRepository burnoutScoreRepository;
    private final EmployeeRepository   employeeRepository;

    /**
     * Computes wellness pulse metrics from today's check-ins.
     * Scales 1-10 scores to 0-100 for the frontend.
     */
    public DashboardDtos.WellnessPulse getPulse(UUID companyId) {
        List<Employee> employees = employeeRepository.findByCompanyIdAndActiveTrue(companyId);
        int totalEmployees = employees.size();

        // Get today's check-ins for this company
        List<CheckIn> todayCheckIns = checkInRepository
            .findByCompanyIdAndCheckInDate(companyId, LocalDate.now());

        int checkInsToday = todayCheckIns.size();

        // ── Average energy score (scale 1-10 → 0-100) ──
        double avgEnergy = todayCheckIns.stream()
            .filter(c -> c.getEnergyScore() != null)
            .mapToInt(CheckIn::getEnergyScore)
            .average()
            .orElse(0.0);
        int energyLevel = (int) Math.round(avgEnergy * 10);

        // ── Average team support score (scale 1-10 → 0-100) ──
        double avgSupport = todayCheckIns.stream()
            .filter(c -> c.getTeamSupportScore() != null)
            .mapToInt(CheckIn::getTeamSupportScore)
            .average()
            .orElse(0.0);
        int supportIndex = (int) Math.round(avgSupport * 10);

        // ── Average burnout risk from today's scores ──
        int burnoutRisk = (int) burnoutScoreRepository
            .findByCompanyIdAndScoreDate(companyId, LocalDate.now())
            .stream()
            .mapToInt(s -> s.getRiskScore() != null ? s.getRiskScore() : 0)
            .average()
            .orElse(0.0);

        double checkInRate = totalEmployees > 0
            ? Math.round((checkInsToday * 100.0) / totalEmployees)
            : 0.0;

        return DashboardDtos.WellnessPulse.builder()
            .energyLevel(energyLevel)
            .energyStatus(scoreToStatus(energyLevel))
            .supportIndex(supportIndex)
            .supportStatus(scoreToStatus(supportIndex))
            .burnoutRisk(burnoutRisk)
            .burnoutStatus(riskToStatus(burnoutRisk))
            .checkInsToday(checkInsToday)
            .totalEmployees(totalEmployees)
            .checkInRate(checkInRate)
            .build();
    }

    private String scoreToStatus(int score) {
        if (score >= 70) return "Good";
        if (score >= 40) return "Moderate";
        return "Low";
    }

    private String riskToStatus(int riskScore) {
        if (riskScore <= 30) return "Low";
        if (riskScore <= 55) return "Moderate";
        if (riskScore <= 75) return "High";
        return "Critical";
    }
}