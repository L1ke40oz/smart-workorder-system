package com.example.smart_workorder_system.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.smart_workorder_system.R;
import com.example.smart_workorder_system.data.Models;
import com.example.smart_workorder_system.databinding.FragmentCreateTicketBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

public class CreateTicketFragment extends BaseFragment {
    private FragmentCreateTicketBinding binding;
    private Models.Draft editing;
    public CreateTicketFragment() { super(R.layout.fragment_create_ticket); }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        super.onViewCreated(view,state);binding=FragmentCreateTicketBinding.bind(view);FragmentCreateTicketBinding b=binding;editing=vm.draft;
        b.back.setOnClickListener(v -> host().getOnBackPressedDispatcher().onBackPressed());
        b.category.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_dropdown_item_1line,Models.CATEGORIES));
        b.priority.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_dropdown_item_1line,Models.PRIORITIES));
        b.description.setText(editing.originalDescription);b.summary.setText(editing.summary);b.category.setText(editing.category,false);
        b.priority.setText(editing.priority,false);b.device.setText(editing.deviceName);b.location.setText(editing.location);
        b.tags.setText(android.text.TextUtils.join("，",editing.tags));
        b.sourceNote.setText(editing.analysisId==null ? "可智能整理，也可手动填写。原始描述会完整保存。" : "已智能整理，请核对并修改需要调整的信息。");
        b.useManual.setVisibility(editing.analysisId==null ? View.GONE : View.VISIBLE);
        b.useManual.setOnClickListener(v -> {
            editing.analysisId=null;editing.analyzedOriginal=null;b.useManual.setVisibility(View.GONE);
            b.sourceNote.setText("当前按手动填写信息提交，请核对所有字段。");
        });
        b.analyze.setEnabled(false);
        b.analyze.setOnClickListener(v -> {
            persist();
            if(editing.originalDescription.trim().isEmpty()) { b.message.setText("请先描述故障现象");return; }
            Ui.hideKeyboard(view);vm.analyze(editing.originalDescription);
        });
        b.description.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                if(editing.analysisId!=null && !s.toString().equals(editing.analyzedOriginal)) {
                    editing.analysisId=null;editing.analyzedOriginal=null;
                    b.useManual.setVisibility(View.GONE);
                    b.sourceNote.setText("描述已修改，请重新智能整理，或核对后按手动信息提交。");
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        vm.capabilities.observe(getViewLifecycleOwner(),s -> {
            boolean enabled=s.data!=null && s.data.analysis;
            boolean busy=vm.analysis.getValue()!=null && vm.analysis.getValue().loading;
            boolean submitting=vm.submission.getValue()!=null && vm.submission.getValue().loading;
            b.analyze.setEnabled(enabled && !busy && !submitting);
            if(!enabled && !s.loading && editing.analysisId==null) b.sourceNote.setText(s.error!=null ? s.error : "智能整理暂未启用，可先手动填写工单。");
        });
        vm.analysis.observe(getViewLifecycleOwner(),s -> {
            boolean submitting=vm.submission.getValue()!=null && vm.submission.getValue().loading;
            b.description.setEnabled(!s.loading && !submitting);b.submit.setEnabled(!s.loading && !submitting);
            boolean enabled=vm.capabilities.getValue()!=null && vm.capabilities.getValue().data!=null && vm.capabilities.getValue().data.analysis;
            b.analyze.setEnabled(enabled && !s.loading && !submitting);b.analyze.setText(s.loading ? "正在整理…" : getString(R.string.analyze));
            if(s.error!=null) b.message.setText(s.error);
        });
        vm.analysisReady.observe(getViewLifecycleOwner(),event -> {
            Models.AnalysisPreview preview=event==null ? null : event.consume();if(preview==null) return;
            b.summary.setText(preview.result.summary);b.category.setText(preview.result.category,false);b.priority.setText(preview.result.priority,false);
            b.device.setText(preview.result.deviceName);b.location.setText(preview.result.location);b.tags.setText(android.text.TextUtils.join("，",preview.result.tags));
            b.sourceNote.setText("已智能整理，请核对并修改需要调整的信息。");b.message.setText("");
            b.useManual.setVisibility(View.VISIBLE);
        });
        b.submit.setOnClickListener(v -> {
            persist();
            if(editing.originalDescription.trim().isEmpty() || editing.summary.isEmpty()) { b.message.setText("请填写故障描述和问题摘要");return; }
            if(!Arrays.asList(Models.CATEGORIES).contains(editing.category) || !Arrays.asList(Models.PRIORITIES).contains(editing.priority)) {
                b.message.setText("请选择工单类别和紧急程度");return;
            }
            if(editing.tags.size()>8) { b.message.setText("标签最多 8 个");return; }
            for(String tag : editing.tags) if(tag.length()>24) { b.message.setText("单个标签不能超过 24 个字");return; }
            Ui.hideKeyboard(view);
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm_title)
                    .setMessage("摘要："+editing.summary+"\n类别："+editing.category+"\n紧急程度："+editing.priority
                            +"\n设备："+Ui.orDash(editing.deviceName)+"\n地点："+Ui.orDash(editing.location)
                            +"\n\n原始描述\n"+editing.originalDescription)
                    .setNegativeButton("继续修改",null).setPositiveButton(R.string.confirm_submit,(d,w) -> vm.create()).show();
        });
        vm.submission.observe(getViewLifecycleOwner(),s -> {
            boolean analyzing=vm.analysis.getValue()!=null && vm.analysis.getValue().loading;
            b.submit.setEnabled(!s.loading && !analyzing);b.submit.setText(s.loading ? "正在提交…" : getString(R.string.confirm_submit));
            b.description.setEnabled(!s.loading && !analyzing);b.summary.setEnabled(!s.loading);b.category.setEnabled(!s.loading);
            b.priority.setEnabled(!s.loading);b.device.setEnabled(!s.loading);b.location.setEnabled(!s.loading);b.tags.setEnabled(!s.loading);
            boolean enabled=vm.capabilities.getValue()!=null && vm.capabilities.getValue().data!=null && vm.capabilities.getValue().data.analysis;
            b.analyze.setEnabled(enabled && !s.loading && !analyzing);b.useManual.setEnabled(!s.loading && !analyzing);
            b.message.setText(s.error==null ? "" : s.error);
        });
        vm.created.observe(getViewLifecycleOwner(),event -> {
            Models.Ticket ticket=event==null ? null : event.consume();
            if(ticket!=null) host().showCreated(ticket.id);
        });
        vm.loadCapabilities();
    }
    private void persist() {
        if(binding==null || editing!=vm.draft) return;
        editing.originalDescription=binding.description.getText()==null ? "" : binding.description.getText().toString();
        editing.summary=Ui.text(binding.summary);editing.category=Ui.text(binding.category);editing.priority=Ui.text(binding.priority);
        editing.deviceName=Ui.text(binding.device);editing.location=Ui.text(binding.location);editing.tags=new ArrayList<>();
        for(String tag : Ui.text(binding.tags).split("[,，]")) if(!tag.trim().isEmpty() && !editing.tags.contains(tag.trim())) editing.tags.add(tag.trim());
    }
    @Override public void onPause() { persist();super.onPause(); }
    @Override public void onDestroyView() { binding=null;super.onDestroyView(); }
}
