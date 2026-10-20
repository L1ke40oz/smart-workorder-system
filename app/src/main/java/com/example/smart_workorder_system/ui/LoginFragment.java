package com.example.smart_workorder_system.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.smart_workorder_system.BuildConfig;
import com.example.smart_workorder_system.R;
import com.example.smart_workorder_system.databinding.FragmentLoginBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import okhttp3.HttpUrl;

public class LoginFragment extends BaseFragment {
    private boolean registration;
    public LoginFragment() { super(R.layout.fragment_login); }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        super.onViewCreated(view,state); FragmentLoginBinding b=FragmentLoginBinding.bind(view);
        registration=state!=null && state.getBoolean("registration");renderMode(b);
        b.toggleMode.setOnClickListener(v -> { registration=!registration;renderMode(b);b.error.setText(""); });
        b.submit.setOnClickListener(v -> {
            String username=Ui.text(b.username),password=b.password.getText()==null ? "" : b.password.getText().toString();
            if(username.isEmpty() || password.isEmpty()) { b.error.setText("请填写账号和密码");return; }
            if(registration && (!username.matches("[A-Za-z0-9_]{4,32}") || password.length()<8 || Ui.text(b.displayName).isEmpty())) {
                b.error.setText("账号需为 4～32 位字母数字下划线，密码至少 8 位，并填写称呼");return;
            }
            Ui.hideKeyboard(view);vm.login(username,password,Ui.text(b.displayName),registration);
        });
        b.serverSettings.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        b.serverSettings.setOnClickListener(v -> {
            EditText input=new EditText(requireContext());input.setText(vm.baseUrl());input.setSingleLine(true);
            int padding=(int)(24*getResources().getDisplayMetrics().density);input.setPadding(padding,padding,padding,padding);
            androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.server_settings)
                    .setMessage("输入工单服务的 API 地址，末尾包含 /api/。模拟器连接本机可使用默认地址。")
                    .setView(input).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,null).create();
            dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(button -> {
                String url=Ui.text(input);if(!url.endsWith("/")) url+="/";
                HttpUrl parsed=HttpUrl.parse(url);
                if(parsed==null || !parsed.username().isEmpty() || !parsed.password().isEmpty()
                        || parsed.query()!=null || parsed.fragment()!=null || !parsed.encodedPath().endsWith("/api/")) {
                    input.setError("请输入有效地址，例如 http://10.0.2.2:8080/api/");return;
                }
                vm.baseUrl(parsed.toString());dialog.dismiss();
            }));dialog.show();
        });
        vm.auth.observe(getViewLifecycleOwner(),s -> {
            b.submit.setEnabled(!s.loading);b.toggleMode.setEnabled(!s.loading);b.serverSettings.setEnabled(!s.loading);
            b.submit.setText(s.loading ? "正在连接…" : registration ? getString(R.string.register) : getString(R.string.login));
            b.error.setText(s.error==null ? "" : s.error);
        });
    }
    private void renderMode(FragmentLoginBinding b) {
        b.displayNameLayout.setVisibility(registration ? View.VISIBLE : View.GONE);
        b.formTitle.setText(registration ? R.string.register : R.string.login);
        b.submit.setText(registration ? R.string.register : R.string.login);
        b.toggleMode.setText(registration ? "已有账号，去登录" : "还没有账号？注册账号");
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) { state.putBoolean("registration",registration);super.onSaveInstanceState(state); }
}
