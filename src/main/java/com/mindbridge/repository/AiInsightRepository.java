package com.mindbridge.repository;

import com.mindbridge.model.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsight, UUID> {

    Optional<AiInsight> findByCompanyIdAndGeneratedDate(UUID companyId, LocalDate date);

    // Last 5 insights for historical timeline
    List<AiInsight> findTop5ByCompanyIdOrderByGeneratedDateDesc(UUID companyId);

    // Check if today's insight already exists
    boolean existsByCompanyIdAndGeneratedDate(UUID companyId, LocalDate date);
}