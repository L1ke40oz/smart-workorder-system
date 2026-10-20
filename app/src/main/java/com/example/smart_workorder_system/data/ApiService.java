package com.example.smart_workorder_system.data;

import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {
    @POST("auth/login") Call<Models.Session> login(@Body Models.Login input);
    @POST("auth/register") Call<Models.Session> register(@Body Models.Registration input);
    @GET("auth/me") Call<Models.User> me();
    @GET("tickets") Call<Models.TicketPage> tickets(@Query("status") String status, @Query("category") String category,
            @Query("query") String query, @Query("page") int page, @Query("size") int size);
    @POST("tickets") Call<Models.Ticket> create(@Body Models.Draft input);
    @GET("tickets/{id}") Call<Models.Ticket> detail(@Path("id") long id);
    @POST("tickets/{id}/start") Call<Models.Ticket> start(@Path("id") long id);
    @POST("tickets/{id}/complete") Call<Models.Ticket> complete(@Path("id") long id, @Body Models.Complete input);
    @GET("statistics") Call<Models.Statistics> statistics();
    @GET("capabilities") Call<Models.Capabilities> capabilities();
    @POST("analyses") Call<Models.AnalysisPreview> analyze(@Body Models.AnalysisInput input);
    @GET("tickets/{id}/suggestions") Call<Models.Suggestions> suggestions(@Path("id") long id);
    @POST("knowledge/sync") Call<Models.SyncResult> syncKnowledge();
}
