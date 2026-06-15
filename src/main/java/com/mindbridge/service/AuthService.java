package com.mindbridge.service;

import com.mindbridge.dto.AuthDtos;
import com.mindbridge.model.Company;
import com.mindbridge.model.User;
import com.mindbridge.repository.CompanyRepository;
import com.mindbridge.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    /**
     * Registers a new company + HR Manager account in one step.
     * Used by companies signing up for MindBridge.
     */
    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {

        // Check email not already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        // Create the company
        // Generate invite code HR will share with employees
        String inviteCode = "MB-" + java.util.UUID.randomUUID()
                .toString().replace("-", "").substring(0, 6).toUpperCase();

        Company company = Company.builder()
                .name(request.getCompanyName())
                .plan("starter")
                .inviteCode(inviteCode)
                .build();
        Company savedCompany = companyRepository.save(company);

        // Create the HR Manager user
        User user = User.builder()
                .companyId(savedCompany.getId())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.HR_MANAGER)
                .build();
        userRepository.save(user);

        log.info("New company registered: {} ({})", savedCompany.getName(), savedCompany.getId());

        // Generate JWT
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails, savedCompany.getId().toString());

        return AuthDtos.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .companyId(savedCompany.getId().toString())
                .inviteCode(savedCompany.getInviteCode())
                .expiresIn(jwtService.getExpirationMs())
                .build();
    }

    /**
     * Authenticates an existing HR Manager and returns a JWT.
     */
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {

        // Spring Security validates credentials — throws if wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails, user.getCompanyId().toString());

        log.info("User logged in: {}", request.getEmail());

        return AuthDtos.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .companyId(user.getCompanyId().toString())
                .expiresIn(jwtService.getExpirationMs())
                .build();
    }
}