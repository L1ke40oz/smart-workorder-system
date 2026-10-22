package com.example.smart_workorder_system.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Models {
    private Models() {}
    public static final String[] CATEGORIES = {"设备故障", "网络问题", "软件问题", "账号问题", "其他问题"};
    public static final String[] PRIORITIES = {"一般", "较急", "紧急"};
    public static class User {
        public long id;
        public String username, displayName, role;
        public boolean isAdmin() { return "ADMIN".equals(role); }
    }
    public static class Session { public String token, expiresAt; public User user; }
    public static class Login {
        public final String username, password;
        public Login(String username, String password) { this.username=username; this.password=password; }
    }
    public static class Registration {
        public final String username, password, displayName;
        public Registration(String username, String password, String displayName) {
            this.username=username; this.password=password; this.displayName=displayName;
        }
    }
    public static class Ticket {
        public long id, ownerId;
        public Long assignedTo;
        public String number, submitterName, originalDescription, summary, category, priority, deviceName, location;
        public String impactScope, faultStartedAt, errorMessage, attemptedActions;
        public String analysisSource, status, assigneeName, resolution, createdAt, updatedAt, startedAt, completedAt;
        public List<String> tags = new ArrayList<>();
    }
    public static class TicketPage {
        public List<Ticket> items = new ArrayList<>();
        public long total;
        public int page, size, totalPages;
    }
    public static class Statistics {
        public long total, pending, processing, completed;
        public Map<String,Long> byCategory;
    }
    public static class Draft {
        public String clientRequestId = UUID.randomUUID().toString();
        public String originalDescription="", summary="", category="设备故障", priority="一般", deviceName="", location="";
        public List<String> tags = new ArrayList<>();
        public String analysisId;
        public transient String analyzedOriginal;
        public transient boolean manualEntry;
        public String impactScope="", faultStartedAt="", errorMessage="", attemptedActions="";
        public Draft forSubmission() {
            Draft input=new Draft();input.clientRequestId=clientRequestId;
            input.originalDescription=manualEntry ? null : originalDescription;
            input.analysisId=manualEntry ? null : analysisId;
            input.summary=summary;input.category=category;input.priority=priority;input.deviceName=deviceName;input.location=location;
            input.tags=new ArrayList<>(tags);input.impactScope=impactScope;input.faultStartedAt=faultStartedAt;
            input.errorMessage=errorMessage;input.attemptedActions=attemptedActions;return input;
        }
    }
    public static class Capabilities { public boolean analysis, rag; }
    public static class AnalysisInput {
        public final String originalDescription;
        public AnalysisInput(String text) { originalDescription=text; }
    }
    public static class AnalysisResult {
        public String category, deviceName, location, priority, summary;
        public String impactScope, faultStartedAt, errorMessage, attemptedActions;
        public List<String> tags;
    }
    public static class AnalysisPreview {
        public String analysisId, originalDescription, expiresAt;
        public AnalysisResult result;
    }
    public static class SimilarCase {
        public long ticketId;
        public String number, summary, resolution;
        public float similarity;
    }
    public static class Suggestions {
        public List<SimilarCase> matches;
        public String suggestion, suggestionError;
        public List<String> referenceNumbers;
    }
    public static class SyncResult { public String message; }
    public static class Complete {
        public final String resolution;
        public Complete(String resolution) { this.resolution=resolution; }
    }
}
