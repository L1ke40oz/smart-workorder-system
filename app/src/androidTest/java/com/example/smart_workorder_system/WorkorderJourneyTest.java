package com.example.smart_workorder_system;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;

/** Runs against the local development backend; creates real validation records. */
@RunWith(AndroidJUnit4.class)
public class WorkorderJourneyTest {
    @Test public void userSubmitsAdminProcessesUserSeesResult() throws Exception {
        String username="ui_"+System.currentTimeMillis();
        String title="打印机卡纸与异响（验收"+(System.currentTimeMillis()%10000)+"）";
        String apiBaseUrl=InstrumentationRegistry.getArguments().getString("apiBaseUrl",BuildConfig.API_BASE_URL);
        InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("session",0).edit()
                .clear().putString("baseUrl",apiBaseUrl).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            waitFor(withId(R.id.toggleMode));
            onView(withId(R.id.toggleMode)).perform(scrollTo(),click());
            onView(withId(R.id.username)).perform(scrollTo(),replaceText(username));
            onView(withId(R.id.displayName)).perform(scrollTo(),replaceText("验收用户"));
            onView(withId(R.id.password)).perform(scrollTo(),replaceText("Password123!"),closeSoftKeyboard());
            onView(withId(R.id.submit)).perform(scrollTo(),click());
            waitFor(withText("你好，验收用户"));screenshot("02-user-dashboard");
            onView(withId(R.id.primaryAction)).perform(scrollTo(),click());
            onView(withId(R.id.manualMode)).perform(scrollTo(),click());
            onView(withId(R.id.summary)).perform(scrollTo(),replaceText(title),closeSoftKeyboard());
            onView(withId(R.id.location)).perform(scrollTo(),replaceText("办公室三楼"),closeSoftKeyboard());
            onView(withId(R.id.device)).perform(scrollTo(),replaceText("打印机"),closeSoftKeyboard());
            onView(withId(R.id.impactScope)).perform(scrollTo(),replaceText("同办公室 5 人"),closeSoftKeyboard());
            onView(withId(R.id.faultStartedAt)).perform(scrollTo(),replaceText("今天上午"),closeSoftKeyboard());
            onView(withId(R.id.errorMessage)).perform(scrollTo(),replaceText("E03"),closeSoftKeyboard());
            onView(withId(R.id.attemptedActions)).perform(scrollTo(),replaceText("重启后仍卡纸"),closeSoftKeyboard());
            onView(withId(R.id.submit)).perform(scrollTo(),click());
            onView(withId(android.R.id.button1)).perform(click());
            waitFor(withId(R.id.structured));
            onView(withId(R.id.structured)).perform(scrollTo()).check(matches(withText(containsString("同办公室 5 人"))))
                    .check(matches(withText(containsString("今天上午")))).check(matches(withText(containsString("E03"))))
                    .check(matches(withText(containsString("重启后仍卡纸")))).check(matches(withText(containsString("手动填写"))));
            onView(withId(R.id.original)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            screenshot("03-ticket-detail");pressBack();
            logout();login("admin","Admin123456!");waitFor(withText("服务工作台"));screenshot("04-admin-dashboard");
            onView(withId(R.id.primaryAction)).perform(scrollTo(),click());
            onView(withId(R.id.query)).perform(replaceText(title),closeSoftKeyboard());onView(withId(R.id.search)).perform(click());
            waitFor(allOf(withId(R.id.summary),withText(title)));
            onView(allOf(withId(R.id.summary),withText(title))).perform(click());
            waitFor(withText("接单并开始处理"));onView(withId(R.id.action)).perform(scrollTo(),click());
            waitFor(withText("提交结果并完成"));
            onView(withId(R.id.resultInput)).perform(scrollTo(),replaceText("清理进纸通道，更换搓纸轮，连续打印测试正常。"),closeSoftKeyboard());
            onView(withId(R.id.action)).perform(scrollTo(),click());onView(withId(android.R.id.button1)).perform(click());
            waitFor(allOf(withId(R.id.status),withText("已完成")));screenshot("05-admin-completed");pressBack();
            logout();login(username,"Password123!");waitFor(withText("你好，验收用户"));
            waitFor(withText("累计 1 张工单"));screenshot("07-user-dashboard");
            onView(withId(R.id.nav_tickets)).perform(click());
            waitFor(allOf(withId(R.id.summary),withText(title)));
            onView(allOf(withId(R.id.summary),withText(title))).perform(click());
            waitFor(withId(R.id.resolution));onView(withId(R.id.resolution)).perform(scrollTo()).check(matches(withText(containsString("连续打印测试正常"))));
            screenshot("06-user-result");pressBack();logout();
        }
    }
    private void login(String username,String password) {
        waitFor(withId(R.id.username));onView(withId(R.id.username)).perform(scrollTo(),replaceText(username));
        onView(withId(R.id.password)).perform(scrollTo(),replaceText(password),closeSoftKeyboard());onView(withId(R.id.submit)).perform(scrollTo(),click());
    }
    private void logout() {
        onView(withId(R.id.nav_profile)).perform(click());onView(withId(R.id.logout)).perform(scrollTo(),click());
        onView(withId(android.R.id.button1)).perform(click());waitFor(withId(R.id.username));
    }
    private void waitFor(Matcher<View> matcher) {
        long deadline=SystemClock.uptimeMillis()+20000;Throwable last=null;
        while(SystemClock.uptimeMillis()<deadline) {
            try { onView(matcher).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));return; }
            catch(AssertionError | RuntimeException e) { last=e;SystemClock.sleep(150); }
        }
        throw new AssertionError("Timed out waiting for "+matcher,last);
    }
    private void screenshot(String name) throws Exception {
        ParcelFileDescriptor result=InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("screencap -p /sdcard/Download/workorder-"+name+".png");
        try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(result)) {
            byte[] buffer=new byte[1024];while(stream.read(buffer)!=-1) { /* wait until the screenshot is written */ }
        }
    }
}
