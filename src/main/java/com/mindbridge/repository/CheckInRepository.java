package com.mindbridge.repository;

import com.mindbridge.model.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    Optional<CheckIn> findByEmployeeIdAndCheckInDate(UUID employeeId, LocalDate date);

    List<CheckIn> findByEmployeeIdAndCheckInDateAfterOrderByCheckInDateAsc(
            UUID employeeId, LocalDate after);

    boolean existsByEmployeeIdAndCheckInDate(UUID employeeId, LocalDate date);

    List<CheckIn> findByCompanyIdAndCheckInDate(UUID companyId, LocalDate date);
}