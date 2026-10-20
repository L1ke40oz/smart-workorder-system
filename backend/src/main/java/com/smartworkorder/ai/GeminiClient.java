package com.smartworkorder.ai;

import com.smartworkorder.common.ApiException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiClient {
    private final ProviderHttp http;
    private final String baseUrl,model,key;
    public GeminiClient(ProviderHttp http,@Value("${app.ai.gemini-base-url}") String baseUrl,
            @Value("${app.ai.gemini-model}") String model,@Value("${app.ai.gemini-key}") String key) {
        this.http=http;this.baseUrl=baseUrl.replaceAll("/+$","");this.model=model;this.key=key;
    }
    public boolean configured() { return !key.isBlank() && model.matches("[a-zA-Z0-9._-]+"); }
    public String generate(String instruction,String content,Map<String,Object> schema) {
        if(!configured()) throw new ApiException(503,"智能整理暂未启用，可先手动填写工单");
        Map<String,Object> config=new LinkedHashMap<>();config.put("temperature",0.2);config.put("maxOutputTokens",4096);
        if(schema!=null) { config.put("responseMimeType","application/json");config.put("responseSchema",schema); }
        var result=http.post(baseUrl+"/models/"+model+":generateContent",Map.of("x-goog-api-key",key),Map.of(
                "systemInstruction",Map.of("parts",List.of(Map.of("text",instruction))),
                "contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",content)))),"generationConfig",config));
        var candidate=result.path("candidates").path(0);
        if(!"STOP".equals(candidate.path("finishReason").asText())) throw new ApiException(502,"模型未返回完整结果，请重试或修改描述");
        StringBuilder text=new StringBuilder();
        for(var part : candidate.path("content").path("parts")) {
            if(!part.path("thought").asBoolean(false) && part.path("text").isTextual()) text.append(part.path("text").textValue());
        }
        if(text.isEmpty() || text.length()>20000) throw new ApiException(502,"模型返回内容为空或超出长度限制");
        return text.toString();
    }
}
