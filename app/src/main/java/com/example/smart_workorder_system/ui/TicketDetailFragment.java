package com.example.smart_workorder_system.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.smart_workorder_system.R;
import com.example.smart_workorder_system.data.Models;
import com.example.smart_workorder_system.databinding.FragmentTicketDetailBinding;
import com.example.smart_workorder_system.databinding.ItemSimilarCaseBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class TicketDetailFragment extends BaseFragment {
    public TicketDetailFragment() { super(R.layout.fragment_ticket_detail); }
    public static TicketDetailFragment of(long id) {
        TicketDetailFragment f=new TicketDetailFragment();Bundle args=new Bundle();args.putLong("id",id);f.setArguments(args);return f;
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        super.onViewCreated(view,state);FragmentTicketDetailBinding b=FragmentTicketDetailBinding.bind(view);
        long id=requireArguments().getLong("id");b.back.setOnClickListener(v -> host().getOnBackPressedDispatcher().onBackPressed());
        b.ragSection.setVisibility(vm.isAdmin() ? View.VISIBLE : View.GONE);
        b.loadSuggestions.setOnClickListener(v -> vm.loadSuggestions(id));b.syncKnowledge.setOnClickListener(v -> vm.syncKnowledge());
        vm.suggestions.observe(getViewLifecycleOwner(),s -> {
            b.loadSuggestions.setEnabled(!s.loading);b.loadSuggestions.setText(s.loading ? "正在检索…" : getString(R.string.find_similar));
            b.ragMessage.setText(s.error!=null ? s.error : s.loading ? "正在查找相似案例并整理参考建议…" : s.data==null ? "检索已完成的故障处理记录，获取相似案例。" : s.data.suggestionError);
            b.cases.removeAllViews();b.suggestion.setText("");
            if(s.data!=null) {
                if(s.data.matches!=null) for(Models.SimilarCase c : s.data.matches) {
                    ItemSimilarCaseBinding row=ItemSimilarCaseBinding.inflate(getLayoutInflater(),b.cases,false);
                    row.title.setText(c.summary);row.score.setText("匹配度 "+Math.round(Math.max(0,c.similarity)*100)+"% · "+c.number);
                    row.resolution.setText(c.resolution);row.openCase.setOnClickListener(v -> host().showDetail(c.ticketId));b.cases.addView(row.getRoot());
                }
                if(s.data.suggestion!=null) b.suggestion.setText(s.data.suggestion+"\n\n参考："+android.text.TextUtils.join("、",s.data.referenceNumbers));
            }
        });
        vm.knowledgeSync.observe(getViewLifecycleOwner(),s -> {
            b.syncKnowledge.setEnabled(!s.loading);
            if(s.error!=null) b.ragMessage.setText(s.error);else if(s.data!=null) b.ragMessage.setText(s.data.message);
        });
        b.refresh.setOnClickListener(v -> vm.loadDetail(id));
        b.action.setOnClickListener(v -> {
            Models.Ticket ticket=vm.detail.getValue()==null ? null : vm.detail.getValue().data;
            if(ticket==null || ticket.id!=id) return;
            if("PENDING".equals(ticket.status)) { vm.start(id);return; }
            String resolution=Ui.text(b.resultInput);
            if(resolution.isEmpty()) { b.resultLayout.setError("请填写实际处理结果");return; }
            b.resultLayout.setError(null);Ui.hideKeyboard(view);
            new MaterialAlertDialogBuilder(requireContext()).setTitle("完成这张工单？").setMessage("处理结果将向提交人展示，请确认问题已处理完成。")
                    .setNegativeButton(R.string.cancel,null).setPositiveButton("确认完成",(d,w) -> vm.complete(id,resolution)).show();
        });
        vm.detail.observe(getViewLifecycleOwner(),s -> {
            b.message.setText(s.error!=null ? s.error : s.loading ? "正在更新…" : "");b.action.setEnabled(!s.loading);b.refresh.setEnabled(!s.loading);
            Models.Ticket t=s.data;if(t==null || t.id!=id) { b.details.setVisibility(View.GONE);return; }
            b.details.setVisibility(View.VISIBLE);b.number.setText(t.number);b.title.setText(t.summary);Ui.badge(b.status,t.status);
            String timeline="提交  ·  "+Ui.date(t.createdAt)+"\n";
            timeline+=t.startedAt==null ? "等待管理员接单" : "接单  ·  "+Ui.date(t.startedAt)+"  "+Ui.orDash(t.assigneeName);
            if(t.completedAt!=null) timeline+="\n完成  ·  "+Ui.date(t.completedAt);
            b.timeline.setText(timeline);
            boolean hasOriginal=t.originalDescription!=null && !t.originalDescription.trim().isEmpty();
            b.originalTitle.setVisibility(hasOriginal ? View.VISIBLE : View.GONE);
            b.original.setVisibility(hasOriginal ? View.VISIBLE : View.GONE);b.original.setText(t.originalDescription);
            b.structured.setText("类别    "+t.category+"\n紧急程度    "+t.priority+"\n设备    "+Ui.orDash(t.deviceName)
                    +"\n地点    "+Ui.orDash(t.location)+"\n提交人    "+Ui.orDash(t.submitterName)
                    +"\n标签    "+(t.tags==null || t.tags.isEmpty() ? "未提供" : android.text.TextUtils.join("、",t.tags))
                    +"\n影响范围    "+Ui.orDash(t.impactScope)+"\n故障开始时间    "+Ui.orDash(t.faultStartedAt)
                    +"\n错误提示    "+Ui.orDash(t.errorMessage)+"\n已尝试操作    "+Ui.orDash(t.attemptedActions)
                    +"\n信息来源    "+("LLM".equals(t.analysisSource) || "GEMINI".equals(t.analysisSource) ? "智能整理后确认" : "手动填写"));
            b.resolution.setText(t.resolution==null ? "尚未填写处理结果，请耐心等待。" : t.resolution);
            Models.User actor=vm.user.getValue();
            boolean canComplete=actor!=null && actor.isAdmin() && "PROCESSING".equals(t.status) && t.assignedTo!=null && t.assignedTo==actor.id;
            boolean canStart=vm.isAdmin() && "PENDING".equals(t.status);
            b.adminActions.setVisibility(canStart || canComplete ? View.VISIBLE : View.GONE);
            b.resultLayout.setVisibility(canComplete ? View.VISIBLE : View.GONE);b.action.setText(canStart ? R.string.accept : R.string.complete);
        });vm.loadDetail(id);
    }
}
