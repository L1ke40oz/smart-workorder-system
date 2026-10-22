package com.smartworkorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:flows;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.bootstrap.admin-password=TestAdmin123!","app.ai.llm-key=","app.ai.llm-model=",
        "app.ai.embedding-key=","app.rag.auto-sync-enabled=false"})
@AutoConfigureMockMvc
class WorkorderFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void fullLifecyclePreservesOriginalAndResolution() throws Exception {
        String user = register(); String admin = login("admin","TestAdmin123!");
        Map<String,Object> input = ticket();
        JsonNode created = perform(post("/api/tickets"),user,input,201);
        long id = created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        assertThat(created.path("originalDescription").asText()).isEqualTo(input.get("originalDescription"));
        perform(post("/api/tickets/"+id+"/complete"),admin,Map.of("resolution","未接单"),409);
        perform(post("/api/tickets/"+id+"/start"),admin,null,200);
        perform(post("/api/tickets/"+id+"/start"),admin,null,409);
        perform(post("/api/tickets/"+id+"/complete"),admin,Map.of("resolution","  "),400);
        perform(post("/api/tickets/"+id+"/complete"),admin,Map.of("resolution","清理进纸通道并更换搓纸轮，连续打印正常。"),200);
        JsonNode finished = perform(get("/api/tickets/"+id),user,null,200);
        assertThat(finished.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.path("resolution").asText()).contains("更换搓纸轮");
        assertThat(finished.path("completedAt").isTextual()).isTrue();
        assertThat(finished.path("originalDescription").asText()).isEqualTo(input.get("originalDescription"));
        perform(post("/api/tickets/"+id+"/complete"),admin,Map.of("resolution","重复完成"),409);
        JsonNode stats = perform(get("/api/statistics"),user,null,200);
        assertThat(stats.path("total").asLong()).isEqualTo(1);
        assertThat(stats.path("completed").asLong()).isEqualTo(1);
    }

    @Test void ownershipAndRolesAreEnforcedOnBackend() throws Exception {
        String owner=register(), other=register(), admin=login("admin","TestAdmin123!");
        long id=perform(post("/api/tickets"),owner,ticket(),201).path("id").asLong();
        perform(get("/api/tickets/"+id),other,null,404);
        assertThat(perform(get("/api/tickets"),other,null,200).path("total").asLong()).isZero();
        perform(post("/api/tickets/"+id+"/start"),owner,null,403);
        perform(post("/api/tickets/"+id+"/complete"),other,Map.of("resolution","越权处理"),403);
        perform(get("/api/tickets/"+id),admin,null,200);
        perform(post("/api/tickets"),admin,ticket(),403);
    }

    @Test void validationRejectsInventedCategoriesAndPrivilegeEscalation() throws Exception {
        String token=register();
        Map<String,Object> invalid=ticket(); invalid.put("category","自由创建类别");
        perform(post("/api/tickets"),token,invalid,400);
        invalid=ticket(); invalid.put("priority","最高级"); perform(post("/api/tickets"),token,invalid,400);
        invalid=ticket(); invalid.put("tags",List.of("")); perform(post("/api/tickets"),token,invalid,400);
        invalid=ticket(); invalid.put("ownerId",1); perform(post("/api/tickets"),token,invalid,400);
        perform(post("/api/auth/register"),null,Map.of("username","evil_user","displayName","测试","password","Password123","role","ADMIN"),400);
        Map<String,Object> noUsername=new HashMap<>(); noUsername.put("password","Password123"); noUsername.put("displayName","测试");
        perform(post("/api/auth/register"),null,noUsername,400);
    }

    @Test void retriesDoNotCreateDuplicateTicketsAndFiltersAreScoped() throws Exception {
        String token=register(); Map<String,Object> input=ticket();
        JsonNode one=perform(post("/api/tickets"),token,input,201);
        JsonNode two=perform(post("/api/tickets"),token,input,201);
        assertThat(two.path("id")).isEqualTo(one.path("id"));
        JsonNode page=perform(get("/api/tickets").param("status","PENDING").param("category","设备故障").param("query","打印机"),token,null,200);
        assertThat(page.path("total").asLong()).isEqualTo(1);
        assertThat(perform(get("/api/tickets").param("status","COMPLETED"),token,null,200).path("total").asLong()).isZero();
        input.put("summary","另一个问题"); perform(post("/api/tickets"),token,input,409);
        perform(get("/api/tickets").param("size","10000"),token,null,400);
        perform(get("/api/tickets").param("status","HACKED"),token,null,400);
    }

    @Test void anonymousInvalidAndTamperedTokensAreRejected() throws Exception {
        perform(get("/api/health"),null,null,200);
        perform(get("/api/tickets"),null,null,401);
        perform(get("/api/tickets"),"not-a-jwt",null,401);
        perform(post("/api/auth/login"),null,Map.of("username","admin","password","wrongpass"),401);
        String token=register();
        JsonNode me=perform(get("/api/auth/me"),token,null,200);
        assertThat(me.path("role").asText()).isEqualTo("USER");
        assertThat(me.has("passwordHash")).isFalse();
        perform(post("/api/analyses"),token,Map.of("originalDescription","打印机卡纸"),503);
        assertThat(perform(get("/api/capabilities"),token,null,200).path("analysis").asBoolean()).isFalse();
    }

    @Test void concurrentRetriesAndClaimsHaveOneWinner() throws Exception {
        String user=register(),admin=login("admin","TestAdmin123!");Map<String,Object> input=ticket();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Callable<JsonNode>> creations=List.of(() -> perform(post("/api/tickets"),user,input,201),
                    () -> perform(post("/api/tickets"),user,input,201));
            var results=pool.invokeAll(creations);long id=results.get(0).get().path("id").asLong();
            assertThat(results.get(1).get().path("id").asLong()).isEqualTo(id);
            List<Callable<Integer>> claims=List.of(() -> mvc.perform(post("/api/tickets/"+id+"/start").header("Authorization","Bearer "+admin)).andReturn().getResponse().getStatus(),
                    () -> mvc.perform(post("/api/tickets/"+id+"/start").header("Authorization","Bearer "+admin)).andReturn().getResponse().getStatus());
            var statuses=pool.invokeAll(claims);
            assertThat(List.of(statuses.get(0).get(),statuses.get(1).get())).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
    }

    @Test void manualEntryNeedsNoSeparateNarrativeAndSavesFaultContext() throws Exception {
        String token=register();Map<String,Object> input=ticket();input.remove("originalDescription");
        input.put("impactScope","办公室 5 人");input.put("faultStartedAt","今天上午");
        input.put("errorMessage","E03");input.put("attemptedActions","重启后仍卡纸");
        JsonNode created=perform(post("/api/tickets"),token,input,201);
        assertThat(created.path("originalDescription").isNull()).isTrue();
        assertThat(created.path("analysisSource").asText()).isEqualTo("MANUAL");
        JsonNode detail=perform(get("/api/tickets/"+created.path("id").asLong()),token,null,200);
        for(String field : List.of("impactScope","faultStartedAt","errorMessage","attemptedActions")) {
            assertThat(detail.path(field).asText()).isEqualTo(input.get(field));
        }
        assertThat(perform(post("/api/tickets"),token,input,201).path("id")).isEqualTo(created.path("id"));
        input.put("originalDescription","  ");
        assertThat(perform(post("/api/tickets"),token,input,201).path("id")).isEqualTo(created.path("id"));
        for(String field : List.of("impactScope","faultStartedAt","errorMessage","attemptedActions")) {
            Object previous=input.put(field,"不同内容");perform(post("/api/tickets"),token,input,409);input.put(field,previous);
        }
    }

    @Test void newContextFieldsAreOptionalButStillValidated() throws Exception {
        String token=register();Map<String,Object> input=ticket();input.remove("originalDescription");
        for(var constraint : Map.of("impactScope",100,"faultStartedAt",100,"errorMessage",500,"attemptedActions",1000).entrySet()) {
            input.put(constraint.getKey(),"x".repeat(constraint.getValue()+1));
            perform(post("/api/tickets"),token,input,400);input.remove(constraint.getKey());
        }
        input.put("summary"," ");perform(post("/api/tickets"),token,input,400);
        assertThat(perform(get("/api/tickets"),token,null,200).path("total").asLong()).isZero();
    }

    private String register() throws Exception {
        return perform(post("/api/auth/register"),null,Map.of("username","u"+UUID.randomUUID().toString().replace("-","").substring(0,16),
                "displayName","测试用户","password","Password123!"),201).path("token").asText();
    }
    private String login(String username,String password) throws Exception {
        return perform(post("/api/auth/login"),null,Map.of("username",username,"password",password),200).path("token").asText();
    }
    private Map<String,Object> ticket() {
        Map<String,Object> input=new HashMap<>(); input.put("clientRequestId",UUID.randomUUID().toString());
        input.put("originalDescription","  办公室三楼的打印机一直卡纸，还有奇怪的声音。  ");
        input.put("summary","打印机卡纸并伴随异响");input.put("category","设备故障");input.put("priority","较急");
        input.put("location","办公室三楼");input.put("deviceName","打印机");input.put("tags",List.of("卡纸","异响"));
        return input;
    }
    private JsonNode perform(MockHttpServletRequestBuilder request,String token,Object body,int status) throws Exception {
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        MvcResult result=mvc.perform(request).andExpect(status().is(status)).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
