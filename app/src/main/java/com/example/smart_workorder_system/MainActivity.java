package com.example.smart_workorder_system;

import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.example.smart_workorder_system.databinding.ActivityMainBinding;
import com.example.smart_workorder_system.ui.*;
import com.example.smart_workorder_system.viewmodel.WorkorderViewModel;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private WorkorderViewModel vm;
    private boolean syncing;
    private Long activeUser;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);EdgeToEdge.enable(this);binding=ActivityMainBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main,(v,insets) -> {
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets keyboard=insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom));return WindowInsetsCompat.CONSUMED;
        });
        vm=new ViewModelProvider(this).get(WorkorderViewModel.class);
        if(state!=null && vm.user.getValue()!=null) activeUser=vm.user.getValue().id;
        binding.bottomNav.setOnItemSelectedListener(item -> {
            if(syncing) return true;
            if(item.getItemId()==R.id.nav_home) showRoot(new DashboardFragment(),"home");
            else if(item.getItemId()==R.id.nav_tickets) showList(null);
            else showRoot(new ProfileFragment(),"profile");
            return true;
        });
        getSupportFragmentManager().addOnBackStackChangedListener(this::syncNavigation);
        vm.user.observe(this,user -> {
            if(user==null) { activeUser=null;showRoot(new LoginFragment(),"login"); }
            else if(activeUser==null || activeUser!=user.id) { activeUser=user.id;showRoot(new DashboardFragment(),"home"); }
            else syncNavigation();
        });
    }
    public void showList(String status) {
        vm.filterStatus=status;vm.page=0;showRoot(new TicketListFragment(),"tickets");
    }
    public void showCreate() { vm.newDraft();push(new CreateTicketFragment(),"create"); }
    public void showDetail(long id) { push(TicketDetailFragment.of(id),"detail"); }
    public void showCreated(long id) {
        getSupportFragmentManager().popBackStackImmediate();push(TicketDetailFragment.of(id),"detail");
    }
    private void showRoot(Fragment fragment,String tag) {
        FragmentManager manager=getSupportFragmentManager();if(manager.isStateSaved()) return;
        manager.popBackStackImmediate(null,FragmentManager.POP_BACK_STACK_INCLUSIVE);
        manager.beginTransaction().setReorderingAllowed(true).replace(R.id.content,fragment,tag).runOnCommit(this::syncNavigation).commit();
    }
    private void push(Fragment fragment,String tag) {
        if(getSupportFragmentManager().isStateSaved()) return;
        binding.bottomNav.setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction().setReorderingAllowed(true).replace(R.id.content,fragment,tag).addToBackStack(tag).commit();
    }
    private void syncNavigation() {
        Fragment current=getSupportFragmentManager().findFragmentById(R.id.content);
        if(current==null) return;
        String tag=current.getTag();
        boolean root="home".equals(tag) || "tickets".equals(tag) || "profile".equals(tag);
        binding.bottomNav.setVisibility(root && vm.user.getValue()!=null ? View.VISIBLE : View.GONE);
        if(root) {
            syncing=true;binding.bottomNav.setSelectedItemId("tickets".equals(tag) ? R.id.nav_tickets : "profile".equals(tag) ? R.id.nav_profile : R.id.nav_home);syncing=false;
        }
    }
}
