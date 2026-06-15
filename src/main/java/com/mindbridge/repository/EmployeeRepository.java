package com.mindbridge.repository;

import com.mindbridge.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    List<Employee> findByCompanyIdAndActiveTrue(UUID companyId);

    Optional<Employee> findBySlackUserId(String slackUserId);

    Optional<Employee> findByEmail(String email);
}