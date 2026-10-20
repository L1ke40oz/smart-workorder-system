package com.example.smart_workorder_system.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.smart_workorder_system.R;
import com.example.smart_workorder_system.data.Models;
import com.example.smart_workorder_system.databinding.FragmentTicketListBinding;
import java.util.*;

public class TicketListFragment extends BaseFragment {
    public TicketListFragment() { super(R.layout.fragment_ticket_list); }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        super.onViewCreated(view,state);FragmentTicketListBinding b=FragmentTicketListBinding.bind(view);
        b.title.setText(vm.isAdmin() ? "全部工单" : "我的工单");b.subtitle.setText("查看记录，跟进问题的每一步。");b.query.setText(vm.query);
        TicketAdapter adapter=new TicketAdapter(t -> host().showDetail(t.id));b.list.setLayoutManager(new LinearLayoutManager(requireContext()));b.list.setAdapter(adapter);
        int checked="PENDING".equals(vm.filterStatus) ? R.id.filterPending : "PROCESSING".equals(vm.filterStatus) ? R.id.filterProcessing : "COMPLETED".equals(vm.filterStatus) ? R.id.filterCompleted : R.id.filterAll;
        b.statusFilters.check(checked);
        b.statusFilters.setOnCheckedStateChangeListener((group,ids) -> {
            int id=ids.isEmpty() ? R.id.filterAll : ids.get(0);
            vm.filterStatus=id==R.id.filterPending ? "PENDING" : id==R.id.filterProcessing ? "PROCESSING" : id==R.id.filterCompleted ? "COMPLETED" : null;
            vm.page=0;vm.loadList();
        });
        List<String> categories=new ArrayList<>();categories.add("全部类别");categories.addAll(Arrays.asList(Models.CATEGORIES));
        b.category.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_dropdown_item_1line,categories));
        b.category.setText(vm.filterCategory==null ? "全部类别" : vm.filterCategory,false);
        b.category.setOnItemClickListener((parent,v,position,id) -> { vm.filterCategory=position==0 ? null : categories.get(position);vm.page=0;vm.loadList(); });
        Runnable search=() -> { vm.query=Ui.text(b.query);vm.page=0;Ui.hideKeyboard(view);vm.loadList(); };
        b.search.setOnClickListener(v -> search.run());b.query.setOnEditorActionListener((v,action,event) -> {
            if(action==EditorInfo.IME_ACTION_SEARCH) { search.run();return true; }return false;
        });
        b.previous.setOnClickListener(v -> { if(vm.page>0) { vm.page--;vm.loadList(); } });
        b.next.setOnClickListener(v -> { vm.page++;vm.loadList(); });
        vm.list.observe(getViewLifecycleOwner(),s -> {
            adapter.submitList(s.data==null ? Collections.emptyList() : s.data.items);
            b.message.setText(s.error!=null ? s.error : s.loading ? "正在加载…" : s.data!=null && s.data.total==0 ? "没有符合条件的工单。" : "共 "+(s.data==null ? 0 : s.data.total)+" 条记录");
            b.search.setEnabled(!s.loading);b.previous.setEnabled(!s.loading && vm.page>0);
            b.next.setEnabled(!s.loading && s.data!=null && s.data.page+1<s.data.totalPages);
            b.pageLabel.setText(s.data==null ? "" : (s.data.page+1)+" / "+Math.max(1,s.data.totalPages));
        });vm.loadList();
    }
}
