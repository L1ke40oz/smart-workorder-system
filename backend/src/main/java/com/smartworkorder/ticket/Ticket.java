package com.smartworkorder.ticket;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true, length=32) String number;
    @Column(nullable=false) Long ownerId;
    @Column(nullable=false, length=36) String requestKey;
    @Column(nullable=false, columnDefinition="TEXT") String originalDescription;
    @Column(nullable=false, length=120) String summary;
    @Column(nullable=false, length=20) String category;
    @Column(nullable=false, length=10) String priority;
    @Column(length=100) String deviceName;
    @Column(length=100) String location;
    @Column(nullable=false, length=1000) String tagsJson;
    @Column(nullable=false, length=16) String analysisSource;
    @Column(nullable=false, length=20) String status;
    Long assignedTo;
    @Column(columnDefinition="TEXT") String resolution;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    Instant startedAt;
    Instant completedAt;
    @Version long version;
}
