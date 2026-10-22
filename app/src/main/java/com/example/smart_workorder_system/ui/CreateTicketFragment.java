package com.example.smart_workorder_system.ui;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.smart_workorder_system.R;
import com.example.smart_workorder_system.data.Models;
import com.example.smart_workorder_system.databinding.FragmentCreateTicketBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
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
        b.description.setText(editing.originalDescription);populateFields();
        b.entryMode.check(editing.manualEntry ? R.id.manualMode : R.id.smartMode);
        b.entryMode.addOnButtonCheckedListener((group,id,checked) -> {
            if(!checked) return;
            persist();editing.manualEntry=id==R.id.manualMode;
            b.descriptionLayout.setError(null);b.summaryLayout.setError(null);b.message.setText("");
            Ui.hideKeyboard(view);updateMode();
        });
        clearErrorWhenEdited(b.description,b.descriptionLayout,b.message,R.string.description_missing);
        clearErrorWhenEdited(b.summary,b.summaryLayout,b.message,R.string.summary_missing);
        b.analyze.setOnClickListener(v -> {
            persist();
            if(editing.originalDescription.trim().isEmpty()) { showRequiredError(b.descriptionLayout,b.description,b.message,R.string.description_missing);return; }
            Ui.hideKeyboard(view);b.message.setText("");vm.analyze(editing.originalDescription);
        });
        b.description.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                if(editing.analysisId!=null && !s.toString().equals(editing.analyzedOriginal)) {
                    editing.analysisId=null;editing.analyzedOriginal=null;updateMode();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        vm.capabilities.observe(getViewLifecycleOwner(),s -> refreshControls());
        vm.analysis.observe(getViewLifecycleOwner(),s -> {
            refreshControls();
            if(s.error!=null && !editing.manualEntry) b.message.setText(s.error);
        });
        vm.analysisReady.observe(getViewLifecycleOwner(),event -> {
            Models.AnalysisPreview preview=event==null ? null : event.consume();if(preview==null) return;
            populateFields();b.message.setText("");updateMode();
        });
        b.submit.setOnClickListener(v -> {
            persist();b.summaryLayout.setError(null);
            if(!editing.manualEntry && editing.analysisId==null) { b.message.setText("请先智能整理，或切换到手动填写");return; }
            if(editing.summary.isEmpty()) { showRequiredError(b.summaryLayout,b.summary,b.message,R.string.summary_missing);return; }
            if(!Arrays.asList(Models.CATEGORIES).contains(editing.category) || !Arrays.asList(Models.PRIORITIES).contains(editing.priority)) {
                b.message.setText("请选择工单类别和紧急程度");return;
            }
            if(editing.tags.size()>8) { b.message.setText("标签最多 8 个");return; }
            for(String tag : editing.tags) if(tag.length()>24) { b.message.setText("单个标签不能超过 24 个字");return; }
            Ui.hideKeyboard(view);
            String confirmation="录入方式："+(editing.manualEntry ? "手动填写" : "智能整理后确认")
                    +"\n摘要："+editing.summary+"\n类别："+editing.category+"\n紧急程度："+editing.priority
                    +"\n设备："+Ui.orDash(editing.deviceName)+"\n地点："+Ui.orDash(editing.location)
                    +"\n标签："+(editing.tags.isEmpty() ? "未提供" : android.text.TextUtils.join("、",editing.tags))
                    +"\n影响范围："+Ui.orDash(editing.impactScope)+"\n故障开始时间："+Ui.orDash(editing.faultStartedAt)
                    +"\n错误提示："+Ui.orDash(editing.errorMessage)+"\n已尝试操作："+Ui.orDash(editing.attemptedActions);
            if(!editing.manualEntry) confirmation+="\n\n原始描述\n"+editing.originalDescription;
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm_title).setMessage(confirmation)
                    .setNegativeButton("继续修改",null).setPositiveButton(R.string.confirm_submit,(d,w) -> vm.create()).show();
        });
        vm.submission.observe(getViewLifecycleOwner(),s -> {
            refreshControls();b.message.setText(s.error==null ? "" : s.error);
        });
        vm.created.observe(getViewLifecycleOwner(),event -> {
            Models.Ticket ticket=event==null ? null : event.consume();if(ticket!=null) host().showCreated(ticket.id);
        });
        refreshControls();vm.loadCapabilities();
    }

    private void populateFields() {
        FragmentCreateTicketBinding b=binding;
        b.summary.setText(editing.summary);b.category.setText(editing.category,false);b.priority.setText(editing.priority,false);
        b.device.setText(editing.deviceName);b.location.setText(editing.location);b.tags.setText(android.text.TextUtils.join("，",editing.tags));
        b.impactScope.setText(editing.impactScope);b.faultStartedAt.setText(editing.faultStartedAt);
        b.errorMessage.setText(editing.errorMessage);b.attemptedActions.setText(editing.attemptedActions);
    }

    private void updateMode() {
        FragmentCreateTicketBinding b=binding;
        boolean ready=editing.manualEntry || editing.analysisId!=null;
        b.smartPanel.setVisibility(editing.manualEntry ? View.GONE : View.VISIBLE);
        b.fieldsPanel.setVisibility(ready ? View.VISIBLE : View.GONE);b.submit.setVisibility(ready ? View.VISIBLE : View.GONE);
        b.fieldsTitle.setText(editing.manualEntry ? R.string.manual_entry : R.string.review_entry);
        if(editing.manualEntry) b.sourceNote.setText("直接填写下方信息即可提交，无需另写智能报修的描述。");
        else if(editing.analysisId!=null) b.sourceNote.setText("已生成工单信息，请核对后提交。未提到的线索会留空。");
        else if(vm.capabilities.getValue()!=null && !vm.capabilities.getValue().loading
                && (vm.capabilities.getValue().data==null || !vm.capabilities.getValue().data.analysis)) {
            b.sourceNote.setText(vm.capabilities.getValue().error!=null
                    ? vm.capabilities.getValue().error+"，可切换到手动填写。" : "智能整理暂未启用，请切换到手动填写。");
        } else b.sourceNote.setText("描述设备、地点、发生时间和已尝试的操作，系统会整理成可修改的工单。修改原文后需重新整理。");
    }

    private void refreshControls() {
        boolean analyzing=vm.analysis.getValue()!=null && vm.analysis.getValue().loading;
        boolean submitting=vm.submission.getValue()!=null && vm.submission.getValue().loading;
        boolean available=vm.capabilities.getValue()!=null && vm.capabilities.getValue().data!=null && vm.capabilities.getValue().data.analysis;
        boolean busy=analyzing || submitting;
        FragmentCreateTicketBinding b=binding;
        b.smartMode.setEnabled(!busy);b.manualMode.setEnabled(!busy);b.submit.setEnabled(!busy);
        b.analyze.setEnabled(available && !busy);b.analyze.setText(analyzing ? "正在整理…" : getString(R.string.analyze));
        b.submit.setText(submitting ? "正在提交…" : getString(R.string.confirm_submit));
        for(View field : new View[]{b.description,b.summary,b.category,b.priority,b.device,b.location,b.tags,
                b.impactScope,b.faultStartedAt,b.errorMessage,b.attemptedActions}) field.setEnabled(!busy);
        updateMode();
    }

    private void persist() {
        if(binding==null || editing!=vm.draft) return;
        editing.originalDescription=binding.description.getText()==null ? "" : binding.description.getText().toString();
        editing.summary=Ui.text(binding.summary);editing.category=Ui.text(binding.category);editing.priority=Ui.text(binding.priority);
        editing.deviceName=Ui.text(binding.device);editing.location=Ui.text(binding.location);editing.tags=new ArrayList<>();
        editing.impactScope=Ui.text(binding.impactScope);editing.faultStartedAt=Ui.text(binding.faultStartedAt);
        editing.errorMessage=Ui.text(binding.errorMessage);editing.attemptedActions=Ui.text(binding.attemptedActions);
        for(String tag : Ui.text(binding.tags).split("[,，]")) if(!tag.trim().isEmpty() && !editing.tags.contains(tag.trim())) editing.tags.add(tag.trim());
    }

    private void showRequiredError(TextInputLayout layout,TextView input,TextView feedback,int message) {
        String error=getString(message);layout.setError(error);feedback.setText(error);input.requestFocus();
        layout.post(() -> layout.requestRectangleOnScreen(new Rect(0,0,layout.getWidth(),layout.getHeight()),true));
    }
    private void clearErrorWhenEdited(TextView input,TextInputLayout layout,TextView feedback,int message) {
        String error=getString(message);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {}
            @Override public void afterTextChanged(Editable s) {
                layout.setError(null);if(error.contentEquals(feedback.getText())) feedback.setText("");
            }
        });
    }
    @Override public void onPause() { persist();super.onPause(); }
    @Override public void onDestroyView() { binding=null;super.onDestroyView(); }
}
