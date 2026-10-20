package com.smartworkorder.ai;

import com.smartworkorder.auth.CurrentUser;
import com.smartworkorder.rag.RagService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class AiController {
    private final AnalysisService analysis;
    private final CurrentUser current;
    private final GeminiClient gemini;
    private final RagService rag;
    public AiController(AnalysisService analysis,CurrentUser current,GeminiClient gemini,RagService rag) {
        this.analysis=analysis;this.current=current;this.gemini=gemini;this.rag=rag;
    }
    @GetMapping("/capabilities") public Map<String,Boolean> capabilities(@AuthenticationPrincipal Jwt jwt) {
        current.require(jwt);return Map.of("analysis",gemini.configured(),"rag",rag.configured());
    }
    @PostMapping("/analyses")
    public AnalysisService.View analyze(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AnalysisService.Input input) {
        return analysis.analyze(current.require(jwt),input.originalDescription());
    }
}
