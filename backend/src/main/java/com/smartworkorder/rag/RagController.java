package com.smartworkorder.rag;

import com.smartworkorder.auth.CurrentUser;
import com.smartworkorder.common.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class RagController {
    private final CurrentUser current;
    private final RagService rag;
    private final KnowledgeIndexer indexer;
    public RagController(CurrentUser current,RagService rag,KnowledgeIndexer indexer) { this.current=current;this.rag=rag;this.indexer=indexer; }
    @GetMapping("/tickets/{id}/suggestions")
    public RagService.View advice(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return rag.advice(current.admin(jwt),id); }
    @PostMapping("/knowledge/sync") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> sync(@AuthenticationPrincipal Jwt jwt) {
        current.admin(jwt);if(!rag.configured()) throw new ApiException(503,"知识检索暂未启用");
        indexer.requestSync();return Map.of("message","已安排同步已完成工单，请稍后重新检索");
    }
}
