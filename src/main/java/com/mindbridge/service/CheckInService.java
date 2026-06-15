package com.mindbridge.service;

import com.mindbridge.agent.CheckInAgent;
import com.mindbridge.model.CheckIn;
import com.mindbridge.model.Employee;
import com.mindbridge.repository.CheckInRepository;
import com.mindbridge.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInService {

    private final CheckInAgent checkInAgent;
    private final CheckInRepository checkInRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * Processes an employee's check-in message through the AI agent
     * and persists the result.
     *
     * @param employeeId  The employee's UUID
     * @param message     The employee's free-text message / response
     * @return The saved CheckIn entity
     */
    @Transactional
    public CheckIn processAndSaveCheckIn(UUID employeeId, String message) {
        // Verify employee exists
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        // Prevent duplicate check-ins on the same day
        if (checkInRepository.existsByEmployeeIdAndCheckInDate(employeeId, LocalDate.now())) {
            log.info("Employee {} already checked in today", employeeId);
            throw new IllegalStateException("Already checked in today. See you tomorrow!");
        }

        // Run the AI agent
        CheckInAgent.CheckInResult result = checkInAgent.processCheckIn(
            employeeId,
            employee.getCompanyId(),
            employee.getFullName(),
            message
        );

        // Build and save the CheckIn entity
        CheckIn checkIn = CheckIn.builder()
            .employeeId(result.getEmployeeId())
            .companyId(result.getCompanyId())
            .checkInDate(LocalDate.now())
            .energyScore(result.getEnergyScore())
            .workloadTag(result.getWorkloadTag())
            .teamSupportScore(result.getTeamSupportScore())
            .rawSentiment(result.getRawSentiment())
            .flaggedKeywords(result.getFlaggedKeywords())
            .aiConversation(result.getAiConversation())
            .build();

        CheckIn saved = checkInRepository.save(checkIn);
        log.info("Check-in saved for employee {} with risk signals: {}",
                 employeeId, result.getFlaggedKeywords());

        return saved;
    }

    /**
     * Returns the opening check-in prompt for a given employee.
     * Used by the Slack bot to initiate the conversation.
     */
    public String getCheckInOpeningMessage(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        return checkInAgent.generateCheckInPrompt(employee.getFullName());
    }
}