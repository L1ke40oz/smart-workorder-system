package com.smartworkorder.rag;

import com.smartworkorder.ai.ProviderHttp;
import com.smartworkorder.common.ApiException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingClient {
    private final ProviderHttp http;
    private final String baseUrl,model,key;
    private final int dimensions;
    public EmbeddingClient(ProviderHttp http,@Value("${app.ai.embedding-base-url}") String baseUrl,
            @Value("${app.ai.embedding-model}") String model,@Value("${app.ai.embedding-key}") String key,
            @Value("${app.ai.embedding-dimensions}") int dimensions) {
        this.http=http;this.baseUrl=baseUrl.replaceAll("/+$","");this.model=model;this.key=key;this.dimensions=dimensions;
        if(dimensions<1 || dimensions>1024) throw new IllegalArgumentException("当前向量索引支持 1～1024 维");
    }
    public boolean configured() { return !key.isBlank() && !model.isBlank(); }
    public String space() { return baseUrl+"|"+model+"|"+dimensions; }
    public float[] embed(String text) {
        if(!configured()) throw new ApiException(503,"知识检索暂未启用");
        var result=http.post(baseUrl+"/embeddings",Map.of("Authorization","Bearer "+key),
                Map.of("model",model,"input",text,"encoding_format","float"));
        var vector=result.path("data").path(0).path("embedding");
        if(!vector.isArray() || vector.size()!=dimensions) throw new ApiException(502,"向量维度与配置不一致");
        float[] output=new float[dimensions];double norm=0;
        for(int i=0;i<dimensions;i++) {
            if(!vector.get(i).isNumber()) throw new ApiException(502,"向量格式不正确");
            output[i]=vector.get(i).floatValue();
            if(!Float.isFinite(output[i])) throw new ApiException(502,"向量包含无效数值");
            norm+=(double)output[i]*output[i];
        }
        if(!Double.isFinite(norm) || norm==0) throw new ApiException(502,"向量不能为零或无效值");
        return output;
    }
}
