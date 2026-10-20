package com.example.smart_workorder_system.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.smart_workorder_system.R;
import com.example.smart_workorder_system.data.Models;
import com.example.smart_workorder_system.databinding.FragmentDashboardBinding;
import java.util.Collections;

public class DashboardFragment extends BaseFragment {
    public DashboardFragment() { super(R.layout.fragment_dashboard); }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        super.onViewCreated(view,state);FragmentDashboardBinding b=FragmentDashboardBinding.bind(view);
        Models.User user=vm.user.getValue();if(user==null) return;
        boolean admin=user.isAdmin();b.greeting.setText(admin ? "服务工作台" : "你好，"+user.displayName);
        b.subtitle.setText(admin ? "关注待办，让每一份报修得到回应。" : "把问题交给我们，处理进度随时可查。");
        b.heroLabel.setText(admin ? "待办中心" : "故障报修");
        b.heroTitle.setText(admin ? "从这里，开始处理。" : "遇到问题？说说看。");
        b.heroBody.setText(admin ? "查看当前待处理工单，接单后记录处理过程与结果。" : "描述设备、地点和故障现象，提交后即可跟进处理。");
        b.primaryAction.setText(admin ? "查看待处理工单" : getString(R.string.create_ticket));
        b.primaryAction.setOnClickListener(v -> { if(admin) host().showList("PENDING");else host().showCreate(); });
        b.pendingCard.setOnClickListener(v -> host().showList("PENDING"));b.processingCard.setOnClickListener(v -> host().showList("PROCESSING"));
        b.completedCard.setOnClickListener(v -> host().showList("COMPLETED"));b.viewAll.setOnClickListener(v -> host().showList(null));
        b.recentLabel.setText(admin ? "最近提交" : "最近的工单");b.categorySection.setVisibility(admin ? View.VISIBLE : View.GONE);
        TicketAdapter adapter=new TicketAdapter(t -> host().showDetail(t.id));b.recentList.setLayoutManager(new LinearLayoutManager(requireContext()));b.recentList.setAdapter(adapter);
        vm.statistics.observe(getViewLifecycleOwner(),s -> {
            if(s.data!=null) {
                b.pendingCount.setText(String.valueOf(s.data.pending));b.processingCount.setText(String.valueOf(s.data.processing));
                b.completedCount.setText(String.valueOf(s.data.completed));
                b.statisticsNote.setText("累计 "+s.data.total+" 张工单"+(s.error==null ? "" : " · 更新失败"));
                if(s.data.byCategory!=null) {
                    StringBuilder categories=new StringBuilder();
                    s.data.byCategory.forEach((c,n) -> { if(categories.length()>0) categories.append('\n');categories.append(c).append("     ").append(n); });
                    b.categoryStats.setText(categories.toString());
                }
            } else b.statisticsNote.setText(s.loading ? "正在更新统计…" : s.error);
        });
        vm.recent.observe(getViewLifecycleOwner(),s -> {
            adapter.submitList(s.data==null ? Collections.emptyList() : s.data.items);
            b.message.setText(s.error!=null ? s.error : s.loading ? "正在加载…" : s.data!=null && s.data.total==0 ? "暂无工单，新的服务记录会显示在这里。" : "");
            b.refresh.setEnabled(!s.loading);
        });
        b.refresh.setOnClickListener(v -> vm.loadDashboard());vm.loadDashboard();
    }
}
