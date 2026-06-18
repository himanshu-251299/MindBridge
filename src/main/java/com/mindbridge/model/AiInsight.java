package com.mindbridge.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_insights")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "generated_date", nullable = false)
    @Builder.Default
    private LocalDate generatedDate = LocalDate.now();

    @Column(name = "headline", nullable = false, columnDefinition = "TEXT")
    private String headline;

    @Column(name = "narrative", nullable = false, columnDefinition = "TEXT")
    private String narrative;

    @Column(name = "wellness_score")
    private Integer wellnessScore;

    @Column(name = "trend_vs_last")
    private Integer trendVsLast;        // e.g. +4 or -2

    @Column(name = "trend_direction")
    private String trendDirection;      // IMPROVING | STABLE | DECLINING

    @Column(name = "severity")
    @Builder.Default
    private String severity = "LOW";

    @Column(name = "raw_ai_response", columnDefinition = "TEXT")
    private String rawAiResponse;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // Recommendations linked to this insight
    @OneToMany(mappedBy = "insightId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AiRecommendation> recommendations;
}