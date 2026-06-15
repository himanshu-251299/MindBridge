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
@Table(name = "check_ins")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate = LocalDate.now();

    @Column(name = "energy_score")
    private Integer energyScore;

    @Column(name = "workload_tag")
    private String workloadTag;

    @Column(name = "team_support_score")
    private Integer teamSupportScore;

    @Column(name = "raw_sentiment", columnDefinition = "TEXT")
    private String rawSentiment;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "flagged_keywords", columnDefinition = "TEXT[]")
    private List<String> flaggedKeywords;

    @Column(name = "ai_conversation", columnDefinition = "TEXT")
    private String aiConversation;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}