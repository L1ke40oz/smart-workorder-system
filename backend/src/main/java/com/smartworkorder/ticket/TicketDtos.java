package com.smartworkorder.ticket;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TicketDtos {
    private TicketDtos() {}
    public record Create(
            @NotNull java.util.UUID clientRequestId,
            @NotBlank @Size(max=5000) String originalDescription,
            @NotBlank @Size(max=120) String summary,
            @NotNull @Pattern(regexp="设备故障|网络问题|软件问题|账号问题|其他问题") String category,
            @NotNull @Pattern(regexp="一般|较急|紧急") String priority,
            @Size(max=100) String deviceName,
            @Size(max=100) String location,
            @NotNull @Size(max=8) List<@NotBlank @Size(max=24) String> tags,
            java.util.UUID analysisId) {}
    public record Complete(@NotBlank @Size(max=5000) String resolution) {}
    public record View(Long id, String number, Long ownerId, String submitterName, String originalDescription,
            String summary, String category, String priority, String deviceName, String location, List<String> tags,
            String analysisSource, String status, Long assignedTo, String assigneeName, String resolution,
            Instant createdAt, Instant updatedAt, Instant startedAt, Instant completedAt) {}
    public record Page(List<View> items, long total, int page, int size, int totalPages) {}
    public record Statistics(long total, long pending, long processing, long completed, Map<String,Long> byCategory) {}
}
