package com.mindbridge.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_recommendations")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "insight_id")
    private UUID insightId;

    @Column(name = "priority", nullable = false)
    private String priority;            // URGENT | HIGH | MEDIUM

    @Column(name = "icon")
    private String icon;                // emoji

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "detail", nullable = false, columnDefinition = "TEXT")
    private String detail;

    @Column(name = "action_label")
    @Builder.Default
    private String actionLabel = "Take action";

    @Column(name = "status")
    @Builder.Default
    private String status = "PENDING";  // PENDING | ACTIONED | DISMISSED

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}