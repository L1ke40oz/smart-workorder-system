package com.example.smart_workorder_system.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.smart_workorder_system.R;
import com.example.smart_workorder_system.data.Models;
import com.example.smart_workorder_system.databinding.FragmentProfileBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ProfileFragment extends BaseFragment {
    public ProfileFragment() { super(R.layout.fragment_profile); }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        super.onViewCreated(view,state);FragmentProfileBinding b=FragmentProfileBinding.bind(view);
        Models.User u=vm.user.getValue();if(u==null) return;
        b.name.setText(u.displayName);b.account.setText("账号  "+u.username);b.role.setText(u.isAdmin() ? "管理员 / 工程人员" : "普通用户");
        b.roleDescription.setText(u.isAdmin() ? "查看全部报修工单，接单处理并记录实际解决方案。" : "提交故障描述，查询自己的历史工单和处理结果。");
        b.logout.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext()).setTitle("退出当前账号？")
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.logout,(d,w) -> vm.logout()).show());
    }
}
