package com.smartworkorder.ai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="analysis_previews")
public class AnalysisPreview {
    @Id @Column(length=36) String id;
    @Column(nullable=false) Long ownerId;
    @Column(nullable=false,columnDefinition="TEXT") String originalDescription;
    @Column(nullable=false,columnDefinition="TEXT") String resultJson;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant expiresAt;
    Long ticketId;
}
