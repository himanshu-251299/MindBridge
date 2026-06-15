package com.mindbridge.security;

import com.mindbridge.repository.EmployeeRepository;
import com.mindbridge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Supports login for both HR Managers (users table)
 * and Employees (employees table).
 *
 * Spring Security calls loadUserByUsername(email) during login.
 * We check both tables — HR managers first, then employees.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // Check HR managers / admins first
        var hrUser = userRepository.findByEmail(email);
        if (hrUser.isPresent()) {
            com.mindbridge.model.User user = hrUser.get();
            return new User(
                    user.getEmail(),
                    user.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
        }

        // Then check employees
        var employee = employeeRepository.findByEmail(email);
        if (employee.isPresent()) {
            com.mindbridge.model.Employee emp = employee.get();

            if (emp.getPassword() == null) {
                throw new UsernameNotFoundException(
                        "Employee exists but has no password set. Please complete registration.");
            }

            return new User(
                    emp.getEmail(),
                    emp.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + emp.getRole()))
            );
        }

        throw new UsernameNotFoundException("No account found for email: " + email);
    }
}