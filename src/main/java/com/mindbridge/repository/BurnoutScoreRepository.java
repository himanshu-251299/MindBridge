package com.mindbridge.repository;

import com.mindbridge.model.BurnoutScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BurnoutScoreRepository extends JpaRepository<BurnoutScore, UUID> {

    Optional<BurnoutScore> findByEmployeeIdAndScoreDate(UUID employeeId, LocalDate date);

    List<BurnoutScore> findByEmployeeIdOrderByScoreDateDesc(UUID employeeId);

    // Fetch latest score per employee for a given company
    @Query("""
        SELECT bs FROM BurnoutScore bs
        WHERE bs.companyId = :companyId
          AND bs.scoreDate = :date
        ORDER BY bs.riskScore DESC
    """)
    List<BurnoutScore> findByCompanyIdAndScoreDate(UUID companyId, LocalDate date);
}