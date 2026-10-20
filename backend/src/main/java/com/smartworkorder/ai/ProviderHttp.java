package com.smartworkorder.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartworkorder.common.ApiException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProviderHttp {
    private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public ProviderHttp(ObjectMapper json) { this.json=json; }
    public JsonNode post(String url,Map<String,String> headers,Object body) {
        try {
            var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            headers.forEach(builder::header);
            HttpResponse<String> response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if(response.statusCode()==429) throw new ApiException(503,"智能服务繁忙，请稍后重试");
            if(response.statusCode()<200 || response.statusCode()>=300) throw new ApiException(502,"智能服务调用失败，请稍后重试或联系管理员");
            if(response.body().length()>1_000_000) throw new ApiException(502,"智能服务响应过大");
            return json.readTree(response.body());
        } catch(ApiException e) { throw e; }
        catch(HttpTimeoutException e) { throw new ApiException(504,"智能服务响应超时，请重试"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new ApiException(503,"智能服务请求已中断"); }
        catch(Exception e) { throw new ApiException(502,"无法连接智能服务或响应格式无效"); }
    }
}
