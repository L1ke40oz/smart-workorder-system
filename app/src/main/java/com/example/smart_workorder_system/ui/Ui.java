package com.example.smart_workorder_system.ui;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import com.example.smart_workorder_system.R;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public final class Ui {
    private Ui() {}
    public static String text(TextView view) { return view.getText().toString().trim(); }
    public static String orDash(String s) { return s==null || s.trim().isEmpty() ? "未提供" : s; }
    public static String status(String s) {
        if("PENDING".equals(s)) return "待处理";
        if("PROCESSING".equals(s)) return "处理中";
        if("COMPLETED".equals(s)) return "已完成";
        return "未知状态";
    }
    public static void badge(TextView v,String status) {
        v.setText(status(status));
        int color="PENDING".equals(status) ? R.color.amber : R.color.primary;
        v.setTextColor(v.getContext().getColor(color));
    }
    public static String date(String value) {
        if(value==null) return "—";
        try {
            SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(input.parse(value));
        } catch(Exception e) { return value; }
    }
    public static void hideKeyboard(View v) {
        ((InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);
    }
}
