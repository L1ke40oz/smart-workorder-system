package com.smartworkorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartworkorder.ai.LlmClient;
import com.smartworkorder.ai.ProviderHttp;
import com.smartworkorder.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Exercises real HTTP request/response envelopes without external credentials or network services. */
class LlmClientTest {
    final ObjectMapper json=new ObjectMapper();
    final AtomicReference<JsonNode> sent=new AtomicReference<>();
    final AtomicReference<String> path=new AtomicReference<>(),authorization=new AtomicReference<>();
    volatile int status=200;
    volatile Object response=completion("{\"summary\":\"打印机卡纸\"}","stop");
    HttpServer server;
    String base;
    static final Map<String,Object> SCHEMA=Map.of("type","object","additionalProperties",false,
            "properties",Map.of("summary",Map.of("type","string")),"required",List.of("summary"));

    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            sent.set(json.readTree(exchange.getRequestBody()));path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes=json.writeValueAsBytes(response);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();base="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }
    LlmClient client(String url,String format) {
        return new LlmClient(new ProviderHttp(json),json,url,"vendor/model:free","fixture-only-key",format);
    }
    static Map<String,Object> completion(Object content,String reason) {
        Map<String,Object> message=new HashMap<>();message.put("role","assistant");message.put("content",content);
        return Map.of("choices",List.of(Map.of("index",0,"finish_reason",reason,"message",message)));
    }

    @Test void jsonObjectUsesBearerMessagesAndBodyModelWithoutChangingOriginal() {
        String original="  办公室打印机卡纸。  ";
        assertThat(client(base+"/v1///","json_object").generate("提取故障",original,SCHEMA)).contains("打印机卡纸");
        assertThat(path.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer fixture-only-key");
        JsonNode body=sent.get();assertThat(body.path("model").asText()).isEqualTo("vendor/model:free");
        assertThat(body.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").path(0).path("content").asText()).contains("JSON Schema","additionalProperties");
        assertThat(body.path("messages").path(1).path("content").asText()).isEqualTo(original);
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.has("generationConfig")).isFalse();assertThat(body.has("max_tokens")).isFalse();
    }
    @Test void jsonSchemaUsesStrictStandardSchemaAndAcceptsFullEndpoint() {
        client(base+"/gateway/v1/chat/completions/","json_schema").generate("提取故障","卡纸",SCHEMA);
        assertThat(path.get()).isEqualTo("/gateway/v1/chat/completions");
        JsonNode format=sent.get().path("response_format");assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema")).isEqualTo(json.valueToTree(SCHEMA));
    }
    @Test void textModeOmitsUnsupportedFormatAndAcceptsOnlyWholeJsonFence() {
        response=completion("```json\r\n{\"summary\":\"打印机卡纸\"}\r\n```","stop");
        assertThat(client(base+"/v1","text").generate("提取","卡纸",SCHEMA)).isEqualTo("{\"summary\":\"打印机卡纸\"}");
        assertThat(sent.get().has("response_format")).isFalse();
        response=completion("说明：\n```json\n{}\n```","stop");
        assertThatThrownBy(() -> client(base+"/v1","text").generate("提取","卡纸",SCHEMA)).isInstanceOf(ApiException.class);
    }
    @Test void incompleteRefusedToolOrEmptyResponsesAreRejected() {
        for(Object invalid : List.of(completion("{}","length"),completion("{}","content_filter"),completion("","stop"),
                completion(42,"stop"),completion(null,"stop"),Map.of("choices",List.of()),
                Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("refusal","declined","content","{}")))),
                Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content","{}","tool_calls",List.of(Map.of("id","fake")))))))) {
            response=invalid;
            assertThatThrownBy(() -> client(base+"/v1","json_object").generate("提取","卡纸",SCHEMA)).isInstanceOf(ApiException.class);
        }
    }
    @Test void nonObjectsTrailingJsonAndOversizeContentAreRejected() {
        for(String invalid : List.of("not-json","[]","null","{} {}","{} trailing","x".repeat(20001))) {
            response=completion(invalid,"stop");
            assertThatThrownBy(() -> client(base+"/v1","json_object").generate("提取","卡纸",SCHEMA)).isInstanceOf(ApiException.class);
        }
    }
    @Test void providerErrorsExplainConfigurationWithoutEchoingProviderSecrets() {
        response=Map.of("error",Map.of("message","sensitive-provider-detail"));
        for(int code : List.of(400,401,403,404,422,429,500)) {
            status=code;
            assertThatThrownBy(() -> client(base+"/v1","json_object").generate("提取","卡纸",SCHEMA))
                    .isInstanceOf(ApiException.class).hasMessageNotContaining("sensitive-provider-detail");
        }
    }
    @Test void missingCredentialsInvalidAddressesAndFormatAreDetectedBeforeCalling() {
        for(String invalid : List.of("","not-a-url","ftp://localhost/v1","https://user:pass@example.com/v1","https://example.com/v1?key=secret")) {
            assertThat(client(invalid,"json_object").configured()).isFalse();
        }
        assertThat(new LlmClient(new ProviderHttp(json),json,base,"","key","text").configured()).isFalse();
        assertThat(new LlmClient(new ProviderHttp(json),json,base,"model","","text").configured()).isFalse();
        assertThatThrownBy(() -> client(base,"unsupported")).isInstanceOf(IllegalArgumentException.class);
        assertThat(sent.get()).isNull();
    }
}
