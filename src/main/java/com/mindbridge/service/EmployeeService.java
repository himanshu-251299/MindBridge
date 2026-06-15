package com.mindbridge.service;

import com.mindbridge.dto.EmployeeDtos;
import com.mindbridge.model.CheckIn;
import com.mindbridge.model.Company;
import com.mindbridge.model.Employee;
import com.mindbridge.repository.CheckInRepository;
import com.mindbridge.repository.CompanyRepository;
import com.mindbridge.repository.EmployeeRepository;
import com.mindbridge.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final CheckInRepository checkInRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    /**
     * Employee self-registration using company invite code.
     * HR shares the invite code with their team.
     */
    @Transactional
    public EmployeeDtos.AuthResponse register(EmployeeDtos.RegisterRequest request) {

        // Validate invite code
        Company company = companyRepository.findByInviteCode(request.getInviteCode())
            .orElseThrow(() -> new IllegalArgumentException(
                "Invalid invite code. Please check with your HR manager."));

        // Check email not already taken
        if (employeeRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        // Create employee account
        Employee employee = Employee.builder()
            .companyId(company.getId())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .role("EMPLOYEE")
            .build();

        Employee saved = employeeRepository.save(employee);
        log.info("Employee registered: {} for company {}", saved.getEmail(), company.getName());

        // Generate JWT
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails, company.getId().toString());

        return EmployeeDtos.AuthResponse.builder()
            .token(token)
            .employeeId(saved.getId())
            .email(saved.getEmail())
            .fullName(saved.getFullName())
            .companyId(company.getId().toString())
            .expiresIn(jwtService.getExpirationMs())
            .build();
    }

    /**
     * Employee login — returns JWT.
     */
    public EmployeeDtos.AuthResponse login(EmployeeDtos.LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Employee employee = employeeRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails, employee.getCompanyId().toString());

        log.info("Employee logged in: {}", request.getEmail());

        return EmployeeDtos.AuthResponse.builder()
            .token(token)
            .employeeId(employee.getId())
            .email(employee.getEmail())
            .fullName(employee.getFullName())
            .companyId(employee.getCompanyId().toString())
            .expiresIn(jwtService.getExpirationMs())
            .build();
    }

    /**
     * Returns the employee's own profile.
     */
    public EmployeeDtos.ProfileResponse getProfile(String email) {
        Employee employee = employeeRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + email));

        return EmployeeDtos.ProfileResponse.builder()
            .id(employee.getId())
            .fullName(employee.getFullName())
            .email(employee.getEmail())
            .companyId(employee.getCompanyId().toString())
            .teamId(employee.getTeamId() != null ? employee.getTeamId().toString() : null)
            .active(employee.isActive())
            .createdAt(employee.getCreatedAt())
            .build();
    }

    /**
     * Returns the employee's last 30 days of check-in history.
     * Employees can only see their own data.
     */
    public List<EmployeeDtos.CheckInSummary> getCheckInHistory(String email) {
        Employee employee = employeeRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + email));

        List<CheckIn> history = checkInRepository
            .findByEmployeeIdAndCheckInDateAfterOrderByCheckInDateAsc(
                employee.getId(),
                LocalDate.now().minusDays(30)
            );

        return history.stream()
            .map(c -> EmployeeDtos.CheckInSummary.builder()
                .id(c.getId())
                .date(c.getCheckInDate())
                .energyScore(c.getEnergyScore())
                .workloadTag(c.getWorkloadTag())
                .teamSupportScore(c.getTeamSupportScore())
                .rawSentiment(c.getRawSentiment())
                .build())
            .toList();
    }

    /**
     * Generates a new invite code for a company.
     * Called by HR managers to regenerate their invite code.
     */
    @Transactional
    public String regenerateInviteCode(UUID companyId) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        String newCode = generateInviteCode();
        company.setInviteCode(newCode);
        companyRepository.save(company);

        log.info("Invite code regenerated for company: {}", companyId);
        return newCode;
    }

    public java.util.Optional<String> getCompanyInviteCode(UUID companyId) {
        return companyRepository.findById(companyId)
            .map(Company::getInviteCode);
    }

    private String generateInviteCode() {
        // 8-char alphanumeric uppercase code e.g. "MB-X7K2P9"
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // no O,0,I,1 to avoid confusion
        StringBuilder code = new StringBuilder("MB-");
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }
}