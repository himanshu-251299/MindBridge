package com.mindbridge.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "burnout_scores")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BurnoutScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate = LocalDate.now();

    @Column(name = "risk_level", nullable = false)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "trend_direction")
    @Enumerated(EnumType.STRING)
    private TrendDirection trendDirection;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "primary_stressors", columnDefinition = "TEXT[]")
    private List<String> primaryStressors;

    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum TrendDirection {
        IMPROVING, STABLE, DECLINING
    }
}