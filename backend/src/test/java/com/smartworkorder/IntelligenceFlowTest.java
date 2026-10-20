package com.smartworkorder;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartworkorder.rag.*;
import com.smartworkorder.ticket.TicketService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP serialization + H2 transactions + real Lucene index, using local provider fixtures. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:intelligence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.bootstrap.admin-password=TestAdmin123!","app.ai.gemini-model=test-gemini","app.ai.gemini-key=fixture-gemini-key",
        "app.ai.embedding-key=fixture-embedding-key","app.ai.embedding-dimensions=3","app.rag.auto-sync-enabled=false"})
@AutoConfigureMockMvc
class IntelligenceFlowTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final AtomicReference<String> MODE=new AtomicReference<>("NORMAL");
    static final AtomicInteger MODEL_CALLS=new AtomicInteger();
    static final HttpServer SERVER=startProvider();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TicketService tickets;
    @Autowired KnowledgeIndexer indexer;
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry) {
        String base="http://127.0.0.1:"+SERVER.getAddress().getPort();
        registry.add("app.ai.gemini-base-url",() -> base+"/v1beta");registry.add("app.ai.embedding-base-url",() -> base+"/v1");
        registry.add("app.rag.index-directory",() -> {
            try { return Files.createTempDirectory("workorder-vector-test-").toString(); }catch(Exception e) { throw new IllegalStateException(e); }
        });
    }
    @BeforeEach void reset() { MODE.set("NORMAL"); }
    @AfterAll static void stop() { SERVER.stop(0); }

    @Test void analysisConfirmationAndRagUseActualCompletedCases() throws Exception {
        String user=register(),other=register(),admin=login();
        String original=" 办公室三楼的打印机反复卡纸，有异响，无法使用。 ";
        JsonNode preview=call(post("/api/analyses"),user,Map.of("originalDescription",original),200);
        assertThat(preview.path("result").path("category").asText()).isEqualTo("设备故障");
        assertThat(preview.path("originalDescription").asText()).isEqualTo(original);
        assertThat(call(get("/api/tickets"),user,null,200).path("total").asLong()).isZero();
        ObjectNode draft=(ObjectNode)preview.path("result").deepCopy();draft.put("originalDescription",original);
        draft.put("clientRequestId",UUID.randomUUID().toString());draft.put("analysisId",preview.path("analysisId").asText());
        call(post("/api/tickets"),other,draft,404);
        draft.put("originalDescription",original+"内容已变化");call(post("/api/tickets"),user,draft,409);draft.put("originalDescription",original);
        JsonNode created=call(post("/api/tickets"),user,draft,201);long historical=created.path("id").asLong();
        assertThat(created.path("analysisSource").asText()).isEqualTo("GEMINI");
        assertThat(call(post("/api/tickets"),user,draft,201).path("id").asLong()).isEqualTo(historical);
        draft.put("clientRequestId",UUID.randomUUID().toString());call(post("/api/tickets"),user,draft,409);
        int before=MODEL_CALLS.get();
        JsonNode empty=call(get("/api/tickets/"+historical+"/suggestions"),admin,null,200);
        assertThat(empty.path("matches").size()).isZero();assertThat(MODEL_CALLS.get()).isEqualTo(before);
        call(post("/api/tickets/"+historical+"/start"),admin,null,200);
        call(post("/api/tickets/"+historical+"/complete"),admin,Map.of("resolution","清洁进纸通道，更换搓纸轮后恢复正常。"),200);
        indexer.index(tickets.completedCase(historical));indexer.index(tickets.completedCase(historical));
        draft.remove("analysisId");draft.put("clientRequestId",UUID.randomUUID().toString());draft.put("summary","新打印机卡纸故障");
        long target=call(post("/api/tickets"),user,draft,201).path("id").asLong();
        JsonNode advice=call(get("/api/tickets/"+target+"/suggestions"),admin,null,200);
        assertThat(advice.path("matches").size()).isEqualTo(1);
        assertThat(advice.path("matches").get(0).path("ticketId").asLong()).isEqualTo(historical);
        assertThat(advice.path("suggestion").asText()).contains("进纸通道");
        assertThat(advice.path("referenceNumbers").get(0).asText()).isEqualTo(created.path("number").asText());
        call(get("/api/tickets/"+target+"/suggestions"),user,null,403);call(post("/api/knowledge/sync"),other,null,403);
        MODE.set("PROVIDER_ERROR");JsonNode partial=call(get("/api/tickets/"+target+"/suggestions"),admin,null,200);
        assertThat(partial.path("matches").size()).isEqualTo(1);assertThat(partial.path("suggestion").isNull()).isTrue();
        assertThat(partial.path("suggestionError").asText()).isNotBlank();
        JsonNode unchanged=call(get("/api/tickets/"+target),user,null,200);
        assertThat(unchanged.path("status").asText()).isEqualTo("PENDING");assertThat(unchanged.path("resolution").isNull()).isTrue();
    }
    @Test void malformedOrIllegalModelResultsNeverCreateTickets() throws Exception {
        String user=register();
        for(String mode : List.of("BAD_JSON","BAD_CATEGORY","BAD_TYPE","INCOMPLETE")) {
            MODE.set(mode);call(post("/api/analyses"),user,Map.of("originalDescription","打印机卡纸"),502);
        }
        MODE.set("RATE_LIMIT");call(post("/api/analyses"),user,Map.of("originalDescription","打印机卡纸"),503);
        assertThat(call(get("/api/tickets"),user,null,200).path("total").asLong()).isZero();
    }
    @Test void badEmbeddingVectorsDoNotProduceFakeMatches() throws Exception {
        String user=register(),admin=login();
        ObjectNode draft=json.createObjectNode();draft.put("clientRequestId",UUID.randomUUID().toString());draft.put("originalDescription","网络断开");
        draft.put("summary","网络断开");draft.put("category","网络问题");draft.put("priority","一般");draft.putArray("tags");
        long id=call(post("/api/tickets"),user,draft,201).path("id").asLong();
        MODE.set("ZERO_VECTOR");call(get("/api/tickets/"+id+"/suggestions"),admin,null,502);
        MODE.set("WRONG_DIMENSIONS");call(get("/api/tickets/"+id+"/suggestions"),admin,null,502);
        MODE.set("NORMAL");JsonNode unmatched=call(get("/api/tickets/"+id+"/suggestions"),admin,null,200);
        assertThat(unmatched.path("matches").size()).isZero();
    }
    private String register() throws Exception {
        return call(post("/api/auth/register"),null,Map.of("username","ai_"+UUID.randomUUID().toString().replace("-","").substring(0,16),
                "displayName","智能验收用户","password","Password123!"),201).path("token").asText();
    }
    private String login() throws Exception { return call(post("/api/auth/login"),null,Map.of("username","admin","password","TestAdmin123!"),200).path("token").asText(); }
    private JsonNode call(MockHttpServletRequestBuilder req,String token,Object body,int status) throws Exception {
        if(token!=null) req.header("Authorization","Bearer "+token);
        if(body!=null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return json.readTree(mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse().getContentAsByteArray());
    }
    private static HttpServer startProvider() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange -> {
                String mode=MODE.get();JsonNode body=JSON.readTree(exchange.getRequestBody());Object response;int code=200;
                if(exchange.getRequestURI().getPath().endsWith("/embeddings")) {
                    if(!"Bearer fixture-embedding-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) code=401;
                    List<Integer> vector=mode.equals("ZERO_VECTOR") ? List.of(0,0,0) : mode.equals("WRONG_DIMENSIONS") ? List.of(1,0)
                            : body.path("input").asText().contains("网络") ? List.of(0,1,0) : List.of(1,0,0);
                    response=Map.of("data",List.of(Map.of("index",0,"embedding",vector)));
                } else {
                    MODEL_CALLS.incrementAndGet();
                    if(!"fixture-gemini-key".equals(exchange.getRequestHeaders().getFirst("x-goog-api-key"))) code=401;
                    if(mode.equals("RATE_LIMIT")) code=429;if(mode.equals("PROVIDER_ERROR")) code=500;
                    String text;
                    if(body.path("generationConfig").path("responseSchema").path("properties").has("referenceNumbers")) {
                        JsonNode context=JSON.readTree(body.path("contents").get(0).path("parts").get(0).path("text").asText());
                        text=JSON.writeValueAsString(Map.of("suggestion","参考历史案例，先断电检查进纸通道，再验证连续打印。",
                                "referenceNumbers",List.of(context.path("cases").get(0).path("number").asText())));
                    } else {
                        ObjectNode result=JSON.createObjectNode();result.put("category",mode.equals("BAD_CATEGORY") ? "自创分类" : "设备故障");
                        result.put("deviceName","打印机");result.putNull("location");result.put("priority","较急");result.putArray("tags").add("卡纸");
                        result.put("summary","打印机卡纸并伴随异响");if(mode.equals("BAD_TYPE")) result.put("summary",42);
                        text=mode.equals("BAD_JSON") ? "not-json" : result.toString();
                    }
                    response=Map.of("candidates",List.of(Map.of("finishReason",mode.equals("INCOMPLETE") ? "MAX_TOKENS" : "STOP",
                            "content",Map.of("parts",List.of(Map.of("text",text))))));
                }
                byte[] bytes=JSON.writeValueAsBytes(response);exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();return server;
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
}
