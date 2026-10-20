package com.example.smart_workorder_system.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.smart_workorder_system.MainActivity;
import com.example.smart_workorder_system.viewmodel.WorkorderViewModel;

public abstract class BaseFragment extends Fragment {
    protected WorkorderViewModel vm;
    protected BaseFragment(int layout) { super(layout); }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle savedInstanceState) {
        super.onViewCreated(view,savedInstanceState); vm=new ViewModelProvider(requireActivity()).get(WorkorderViewModel.class);
    }
    protected MainActivity host() { return (MainActivity)requireActivity(); }
}
