package com.smartworkorder.ticket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartworkorder.auth.User;
import com.smartworkorder.auth.UserRepository;
import com.smartworkorder.ai.AnalysisService;
import com.smartworkorder.rag.TicketCompleted;
import org.springframework.context.ApplicationEventPublisher;
import com.smartworkorder.common.ApiException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class TicketService {
    public static final List<String> CATEGORIES = List.of("设备故障", "网络问题", "软件问题", "账号问题", "其他问题");
    public static final List<String> STATUSES = List.of("PENDING", "PROCESSING", "COMPLETED");
    private final TicketRepository tickets;
    private final UserRepository users;
    private final ObjectMapper json;
    private final AnalysisService analysis;
    private final ApplicationEventPublisher events;
    public TicketService(TicketRepository tickets, UserRepository users, ObjectMapper json,AnalysisService analysis,ApplicationEventPublisher events) {
        this.tickets = tickets; this.users = users; this.json = json;this.analysis=analysis;this.events=events;
    }
    @Transactional
    public TicketDtos.View create(User actor, TicketDtos.Create input) {
        if (actor.isAdmin()) throw new ApiException(403, "请使用普通用户账号提交工单");
        // Lock the submitter to serialize retries of the same idempotency key.
        users.lockById(actor.getId()).orElseThrow(() -> new ApiException(401, "账号不存在"));
        Ticket existing = tickets.findByOwnerIdAndRequestKey(actor.getId(), input.clientRequestId().toString()).orElse(null);
        if (existing != null) {
            if (!existing.originalDescription.equals(input.originalDescription()) || !existing.summary.equals(input.summary().trim())
                    || !existing.category.equals(input.category()) || !existing.priority.equals(input.priority())
                    || !Objects.equals(existing.deviceName,clean(input.deviceName())) || !Objects.equals(existing.location,clean(input.location()))
                    || !existing.tagsJson.equals(write(input.tags()))) {
                throw new ApiException(409, "本次提交标识已使用，请重新新建工单");
            }
            return view(existing);
        }
        analysis.validateForCreation(input.analysisId(),actor,input.originalDescription());
        Ticket t = new Ticket();
        t.number = "WO" + DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.ofHours(8)).format(Instant.now())
                + UUID.randomUUID().toString().replace("-", "").substring(0,16).toUpperCase(Locale.ROOT);
        t.ownerId = actor.getId(); t.requestKey = input.clientRequestId().toString();
        t.originalDescription = input.originalDescription(); t.summary = input.summary().trim();
        t.category = input.category(); t.priority = input.priority(); t.deviceName = clean(input.deviceName());
        t.location = clean(input.location()); t.tagsJson = write(input.tags());
        t.analysisSource = input.analysisId()==null ? "MANUAL" : "GEMINI"; t.status = "PENDING";
        t.createdAt = Instant.now(); t.updatedAt = t.createdAt;
        tickets.save(t);analysis.markUsed(input.analysisId(),actor,t.id);return view(t);
    }
    public TicketDtos.Page list(User actor, String status, String category, String query, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new ApiException(400, "分页参数不合法");
        if (status != null && !STATUSES.contains(status)) throw new ApiException(400, "工单状态不合法");
        if (category != null && !CATEGORIES.contains(category)) throw new ApiException(400, "工单类别不合法");
        if (query != null && query.length() > 100) throw new ApiException(400, "搜索关键词过长");
        Specification<Ticket> spec = scope(actor);
        if (status != null) spec = spec.and((r,q,b) -> b.equal(r.get("status"), status));
        if (category != null) spec = spec.and((r,q,b) -> b.equal(r.get("category"), category));
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((r,q,b) -> b.or(b.like(b.lower(r.get("summary")), pattern, '\\'),
                    b.like(b.lower(r.get("number")), pattern, '\\')));
        }
        var result = tickets.findAll(spec, PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        return new TicketDtos.Page(result.getContent().stream().map(this::view).toList(), result.getTotalElements(), page, size, result.getTotalPages());
    }
    public TicketDtos.View detail(User actor, Long id) { return view(visible(actor, id)); }
    public Ticket visible(User actor, Long id) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new ApiException(404, "工单不存在"));
        if (!actor.isAdmin() && !t.ownerId.equals(actor.getId())) throw new ApiException(404, "工单不存在");
        return t;
    }
    @Transactional
    public TicketDtos.View start(User admin, Long id) {
        requireAdmin(admin);
        Ticket t = tickets.lockById(id).orElseThrow(() -> new ApiException(404, "工单不存在"));
        if (!"PENDING".equals(t.status)) throw new ApiException(409, "只有待处理工单可以接单，请刷新后重试");
        t.status = "PROCESSING"; t.assignedTo = admin.getId(); t.startedAt = Instant.now(); t.updatedAt = t.startedAt;
        return view(t);
    }
    @Transactional
    public TicketDtos.View complete(User admin, Long id, String resolution) {
        requireAdmin(admin);
        Ticket t = tickets.lockById(id).orElseThrow(() -> new ApiException(404, "工单不存在"));
        if (!"PROCESSING".equals(t.status)) throw new ApiException(409, "仅处理中的工单可以完成");
        if (!Objects.equals(t.assignedTo, admin.getId())) throw new ApiException(403, "请由接单管理员填写处理结果");
        t.status = "COMPLETED"; t.resolution = resolution.trim(); t.completedAt = Instant.now(); t.updatedAt = t.completedAt;
        events.publishEvent(new TicketCompleted(t.id));
        return view(t);
    }
    public List<TicketDtos.View> completedCases(int page,int size) {
        return tickets.findAll((r,q,b) -> b.equal(r.get("status"),"COMPLETED"),PageRequest.of(page,size,Sort.by("id")))
                .getContent().stream().map(this::view).toList();
    }
    public TicketDtos.View completedCase(Long id) {
        return tickets.findById(id).filter(t -> "COMPLETED".equals(t.status)).map(this::view).orElse(null);
    }
    public TicketDtos.Statistics statistics(User actor) {
        Specification<Ticket> spec = scope(actor);
        Map<String,Long> categories = new LinkedHashMap<>();
        for (String c : CATEGORIES) categories.put(c, tickets.count(spec.and((r,q,b) -> b.equal(r.get("category"), c))));
        return new TicketDtos.Statistics(tickets.count(spec), count(spec,"PENDING"),count(spec,"PROCESSING"),count(spec,"COMPLETED"),categories);
    }
    private long count(Specification<Ticket> spec, String status) { return tickets.count(spec.and((r,q,b) -> b.equal(r.get("status"), status))); }
    private Specification<Ticket> scope(User actor) { return (r,q,b) -> actor.isAdmin() ? b.conjunction() : b.equal(r.get("ownerId"),actor.getId()); }
    private void requireAdmin(User u) { if (!u.isAdmin()) throw new ApiException(403, "仅管理员可以处理工单"); }
    private String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private String write(List<String> tags) {
        try { return json.writeValueAsString(tags); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private TicketDtos.View view(Ticket t) {
        List<String> tags;
        try { tags = json.readValue(t.tagsJson, new TypeReference<List<String>>() {}); }
        catch (Exception e) { throw new IllegalStateException("Invalid persisted tags", e); }
        String submitter = users.findById(t.ownerId).map(User::getDisplayName).orElse("用户");
        String assignee = t.assignedTo == null ? null : users.findById(t.assignedTo).map(User::getDisplayName).orElse("管理员");
        return new TicketDtos.View(t.id,t.number,t.ownerId,submitter,t.originalDescription,t.summary,t.category,t.priority,
                t.deviceName,t.location,tags,t.analysisSource,t.status,t.assignedTo,assignee,t.resolution,
                t.createdAt,t.updatedAt,t.startedAt,t.completedAt);
    }
}
