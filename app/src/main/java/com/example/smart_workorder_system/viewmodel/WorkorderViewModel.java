package com.example.smart_workorder_system.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.example.smart_workorder_system.data.*;
import retrofit2.Call;
import java.util.function.Consumer;

public class WorkorderViewModel extends AndroidViewModel {
    private final WorkorderRepository repository;
    public final MutableLiveData<Models.User> user=new MutableLiveData<>();
    public final MutableLiveData<UiState<Models.User>> auth=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<UiState<Models.TicketPage>> recent=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<UiState<Models.TicketPage>> list=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<UiState<Models.Statistics>> statistics=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<UiState<Models.Ticket>> detail=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<UiState<Models.Ticket>> submission=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<Event<Models.Ticket>> created=new MutableLiveData<>();
    public final MutableLiveData<UiState<Models.Capabilities>> capabilities=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<UiState<Models.AnalysisPreview>> analysis=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<Event<Models.AnalysisPreview>> analysisReady=new MutableLiveData<>();
    public final MutableLiveData<UiState<Models.Suggestions>> suggestions=new MutableLiveData<>(UiState.idle());
    public final MutableLiveData<UiState<Models.SyncResult>> knowledgeSync=new MutableLiveData<>(UiState.idle());
    public Models.Draft draft=new Models.Draft();
    public String filterStatus, filterCategory, query="";
    public int page;
    private int sessionGeneration,listGeneration,detailGeneration,analysisGeneration;
    public WorkorderViewModel(@NonNull Application app) {
        super(app); repository=new WorkorderRepository(new SessionStore(app)); user.setValue(repository.session.user());
        if(user.getValue()!=null) request(repository.api().me(),new MutableLiveData<>(),true,u -> {
            repository.session.updateUser(u); user.setValue(u);
        });
    }
    public boolean isAdmin() { return user.getValue()!=null && user.getValue().isAdmin(); }
    public String baseUrl() { return repository.session.baseUrl(); }
    public void baseUrl(String url) { logout(); repository.session.baseUrl(url); repository.rebuild(); }
    public void login(String username,String password,String displayName,boolean registration) {
        if(auth.getValue()!=null && auth.getValue().loading) return;
        Call<Models.Session> call=registration ? repository.api().register(new Models.Registration(username,password,displayName))
                : repository.api().login(new Models.Login(username,password));
        auth.setValue(new UiState<>(true,null,null));
        int generation=sessionGeneration;
        repository.execute(call,new WorkorderRepository.Result<Models.Session>() {
            @Override public void success(Models.Session value) {
                if(generation!=sessionGeneration) return;
                try { repository.session.save(value); auth.setValue(new UiState<>(false,value.user,null)); user.setValue(value.user); }
                catch(Exception e) { auth.setValue(new UiState<>(false,null,"无法安全保存登录信息，请重试")); }
            }
            @Override public void failure(String message,int status) {
                if(generation==sessionGeneration) auth.setValue(new UiState<>(false,null,message));
            }
        });
    }
    public void logout() {
        sessionGeneration++; listGeneration++; detailGeneration++;
        repository.session.clear();
        recent.setValue(UiState.idle());list.setValue(UiState.idle());statistics.setValue(UiState.idle());detail.setValue(UiState.idle());
        submission.setValue(UiState.idle());created.setValue(null);draft=new Models.Draft();
        capabilities.setValue(UiState.idle());analysis.setValue(UiState.idle());analysisReady.setValue(null);
        suggestions.setValue(UiState.idle());knowledgeSync.setValue(UiState.idle());analysisGeneration++;
        filterStatus=null;filterCategory=null;query="";page=0;auth.setValue(UiState.idle()); user.setValue(null);
    }
    public void loadDashboard() {
        request(repository.api().statistics(),statistics,true,null);
        request(repository.api().tickets(null,null,null,0,3),recent,true,null);
    }
    public void loadList() {
        int generation=++listGeneration;
        requestGuarded(repository.api().tickets(filterStatus,filterCategory,query,page,20),list,() -> generation==listGeneration,null);
    }
    public void loadDetail(long id) {
        int generation=++detailGeneration;
        detail.setValue(UiState.idle());
        suggestions.setValue(UiState.idle());knowledgeSync.setValue(UiState.idle());
        requestGuarded(repository.api().detail(id),detail,() -> generation==detailGeneration,null);
    }
    public void newDraft() {
        analysisGeneration++;draft=new Models.Draft();submission.setValue(UiState.idle());analysis.setValue(UiState.idle());analysisReady.setValue(null);
    }
    public void loadCapabilities() { request(repository.api().capabilities(),capabilities,true,null); }
    public void analyze(String original) {
        if(analysis.getValue()!=null && analysis.getValue().loading) return;
        int generation=++analysisGeneration;
        requestGuarded(repository.api().analyze(new Models.AnalysisInput(original)),analysis,() -> generation==analysisGeneration,p -> {
            draft.analysisId=p.analysisId;draft.analyzedOriginal=p.originalDescription;draft.originalDescription=p.originalDescription;
            draft.category=p.result.category;draft.priority=p.result.priority;draft.summary=p.result.summary;
            draft.deviceName=p.result.deviceName;draft.location=p.result.location;draft.tags=p.result.tags;
            analysisReady.setValue(new Event<>(p));
        });
    }
    public void loadSuggestions(long id) {
        if(suggestions.getValue()!=null && suggestions.getValue().loading) return;
        int generation=detailGeneration;
        requestGuarded(repository.api().suggestions(id),suggestions,() -> generation==detailGeneration,null);
    }
    public void syncKnowledge() { request(repository.api().syncKnowledge(),knowledgeSync,true,null); }
    public void create() {
        if(submission.getValue()!=null && submission.getValue().loading) return;
        request(repository.api().create(draft),submission,true,t -> { draft=new Models.Draft(); created.setValue(new Event<>(t)); });
    }
    public void start(long id) { mutate(repository.api().start(id)); }
    public void complete(long id,String result) { mutate(repository.api().complete(id,new Models.Complete(result))); }
    private void mutate(Call<Models.Ticket> call) {
        if(detail.getValue()!=null && detail.getValue().loading) return;
        int generation=detailGeneration;
        requestGuarded(call,detail,() -> generation==detailGeneration,null);
    }
    private <T> void request(Call<T> call,MutableLiveData<UiState<T>> target,boolean authenticated,Consumer<T> success) {
        requestGuarded(call,target,() -> true,success);
    }
    private interface Guard { boolean valid(); }
    private <T> void requestGuarded(Call<T> call,MutableLiveData<UiState<T>> target,Guard guard,Consumer<T> success) {
        T previous=target.getValue()==null ? null : target.getValue().data;
        target.setValue(new UiState<>(true,previous,null));
        int generation=sessionGeneration;
        repository.execute(call,new WorkorderRepository.Result<T>() {
            @Override public void success(T value) {
                if(generation!=sessionGeneration || !guard.valid()) return;
                target.setValue(new UiState<>(false,value,null)); if(success!=null) success.accept(value);
            }
            @Override public void failure(String message,int status) {
                if(generation!=sessionGeneration || !guard.valid()) return;
                if(status==401) { logout();auth.setValue(new UiState<>(false,null,"登录已失效，请重新登录"));return; }
                target.setValue(new UiState<>(false,previous,message));
            }
        });
    }
}
