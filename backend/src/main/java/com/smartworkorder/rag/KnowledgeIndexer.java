package com.smartworkorder.rag;

import com.smartworkorder.ticket.TicketDtos;
import com.smartworkorder.ticket.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;

@Service
public class KnowledgeIndexer {
    private static final Logger log=LoggerFactory.getLogger(KnowledgeIndexer.class);
    private final TicketService tickets;
    private final EmbeddingClient embedding;
    private final VectorStore vectors;
    private final boolean autoSync;
    private int page;
    public KnowledgeIndexer(TicketService tickets,EmbeddingClient embedding,VectorStore vectors,
            @Value("${app.rag.auto-sync-enabled}") boolean autoSync) {
        this.tickets=tickets;this.embedding=embedding;this.vectors=vectors;this.autoSync=autoSync;
    }
    @Async("knowledgeExecutor") @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void onCompleted(TicketCompleted event) { indexSafely(tickets.completedCase(event.ticketId())); }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void periodic() { if(autoSync) syncBatch(); }
    @Async("knowledgeExecutor") public void requestSync() { syncBatch(); }
    public synchronized void syncBatch() {
        if(!embedding.configured()) return;
        var batch=tickets.completedCases(page,20);
        batch.forEach(this::indexSafely);page=batch.size()<20 ? 0 : page+1;
    }
    public void index(TicketDtos.View ticket) throws java.io.IOException {
        if(ticket==null || !"COMPLETED".equals(ticket.status()) || !embedding.configured()) return;
        String text=caseText(ticket),fingerprint=VectorStore.hash(text);
        if(!vectors.current(ticket.id(),fingerprint)) vectors.upsert(ticket.id(),fingerprint,embedding.embed(text));
    }
    private void indexSafely(TicketDtos.View ticket) {
        try { index(ticket); }
        catch(Exception e) { log.warn("历史工单 {} 暂未建立知识索引，将在后续同步中重试",ticket==null ? "unknown" : ticket.id()); }
    }
    public static String queryText(TicketDtos.View t) {
        return "类别："+t.category()+"\n摘要："+t.summary()+"\n设备："+safe(t.deviceName(),100)
                +"\n故障描述："+safe(t.originalDescription(),1000);
    }
    public static String caseText(TicketDtos.View t) { return queryText(t)+"\n实际解决方案："+safe(t.resolution(),800); }
    private static String safe(String s,int limit) { return s==null ? "" : s.substring(0,Math.min(s.length(),limit)); }
}
