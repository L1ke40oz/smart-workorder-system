package com.smartworkorder.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartworkorder.common.ApiException;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OpenAI-compatible, non-streaming Chat Completions transport. */
@Component
public class LlmClient {
    private final ProviderHttp http;
    private final ObjectMapper json;
    private final String baseUrl,model,key,format;
    public LlmClient(ProviderHttp http,ObjectMapper json,@Value("${app.ai.llm-base-url}") String baseUrl,
            @Value("${app.ai.llm-model}") String model,@Value("${app.ai.llm-key}") String key,
            @Value("${app.ai.llm-response-format:json_object}") String format) {
        this.http=http;this.json=json;this.baseUrl=baseUrl.trim().replaceAll("/+$","");
        this.model=model.trim();this.key=key.trim();this.format=format.trim().toLowerCase(Locale.ROOT);
        if(!Set.of("json_object","json_schema","text").contains(this.format)) {
            throw new IllegalArgumentException("LLM_RESPONSE_FORMAT must be json_object, json_schema or text");
        }
    }
    public boolean configured() {
        if(key.isBlank() || model.isBlank() || model.length()>200 || model.chars().anyMatch(Character::isISOControl)) return false;
        try {
            URI uri=URI.create(baseUrl);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost()!=null
                    && uri.getRawUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null;
        } catch(IllegalArgumentException e) { return false; }
    }
    public String generate(String instruction,String content,Map<String,Object> schema) {
        if(!configured()) throw new ApiException(503,"智能整理暂未启用，请检查 LLM 地址、密钥和模型配置，或先手动填写工单");
        String system=instruction+"\n仅返回一个符合以下 JSON Schema 的 JSON 对象，不要 Markdown、解释或额外字段。"
                +"所有键必须存在，缺失的选填信息使用 null。JSON Schema:\n"+json.valueToTree(schema);
        Map<String,Object> request=new LinkedHashMap<>();request.put("model",model);request.put("stream",false);
        request.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",content)));
        if("json_schema".equals(format)) request.put("response_format",Map.of("type","json_schema","json_schema",
                Map.of("name","workorder_result","strict",true,"schema",schema)));
        else if("json_object".equals(format)) request.put("response_format",Map.of("type","json_object"));
        String endpoint=baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl+"/chat/completions";
        JsonNode response=http.post(endpoint,Map.of("Authorization","Bearer "+key),request);
        if(response==null) throw new ApiException(502,"智能服务响应为空");
        JsonNode choice=response.path("choices").path(0),message=choice.path("message");
        if(!message.path("refusal").isNull() && !message.path("refusal").isMissingNode() && !message.path("refusal").asText().isBlank()) {
            throw new ApiException(502,"模型拒绝处理该内容，请修改描述或手动填写");
        }
        if(!"stop".equals(choice.path("finish_reason").asText()) || message.path("tool_calls").size()>0) {
            throw new ApiException(502,"模型未返回完整结果，请重试或修改描述");
        }
        JsonNode value=message.path("content");
        if(!value.isTextual() || value.asText().isBlank() || value.asText().length()>20000) {
            throw new ApiException(502,"模型返回内容为空、类型不正确或超出长度限制");
        }
        String text=value.asText().trim();
        if("text".equals(format)) text=text.replace("\r\n","\n");
        // Some text-only gateways wrap JSON in a single code fence. Never extract from arbitrary prose.
        if("text".equals(format) && text.startsWith("```json\n") && text.endsWith("```")) text=text.substring(8,text.length()-3).trim();
        else if("text".equals(format) && text.startsWith("```\n") && text.endsWith("```")) text=text.substring(4,text.length()-3).trim();
        try {
            JsonNode object=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
            if(object==null || !object.isObject()) throw new IllegalArgumentException();
        } catch(Exception e) { throw new ApiException(502,"模型未返回合法的 JSON 对象，请重试或手动填写"); }
        return text;
    }
}
