package com.example.smart_workorder_system.data;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;

public class WorkorderRepository {
    public interface Result<T> { void success(T value); void failure(String message,int status); }
    public final SessionStore session;
    private ApiService api;
    public WorkorderRepository(SessionStore session) { this.session=session; rebuild(); }
    public void rebuild() {
        OkHttpClient client=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(110,TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request.Builder request=chain.request().newBuilder();
                    String token=session.token();
                    if(token!=null) request.header("Authorization","Bearer "+token);
                    return chain.proceed(request.build());
                }).build();
        api=new Retrofit.Builder().baseUrl(session.baseUrl()).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService.class);
    }
    public ApiService api() { return api; }
    public <T> void execute(Call<T> call, Result<T> result) {
        call.enqueue(new Callback<T>() {
            @Override public void onResponse(Call<T> call, Response<T> response) {
                if(response.isSuccessful() && response.body()!=null) { result.success(response.body()); return; }
                String message="请求未完成，请稍后重试（"+response.code()+"）";
                try {
                    if(response.errorBody()!=null) {
                        String parsed=JsonParser.parseString(response.errorBody().string()).getAsJsonObject().get("message").getAsString();
                        if(!parsed.isEmpty()) message=parsed;
                    }
                } catch(Exception ignored) {}
                result.failure(message,response.code());
            }
            @Override public void onFailure(Call<T> call, Throwable t) {
                if(!call.isCanceled()) result.failure(t instanceof IOException ? "连接失败，请检查网络和服务地址后重试" : "响应内容异常，请稍后重试",0);
            }
        });
    }
}
