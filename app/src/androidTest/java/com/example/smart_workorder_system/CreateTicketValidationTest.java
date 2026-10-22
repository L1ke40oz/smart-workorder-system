package com.example.smart_workorder_system;

import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.textfield.TextInputLayout;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;

/** Manual entry must not require the smart-entry narrative. */
@RunWith(AndroidJUnit4.class)
public class CreateTicketValidationTest {
    @Test public void manualFieldsSurviveModeSwitchAndOnlyMissingSummaryBlocksSubmission() {
        String apiBaseUrl=InstrumentationRegistry.getArguments().getString("apiBaseUrl",BuildConfig.API_BASE_URL);
        InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("session",0).edit()
                .clear().putString("baseUrl",apiBaseUrl).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.toggleMode)).perform(scrollTo(),click());
            onView(withId(R.id.username)).perform(scrollTo(),replaceText("form_"+System.currentTimeMillis()));
            onView(withId(R.id.displayName)).perform(scrollTo(),replaceText("表单验收用户"));
            onView(withId(R.id.password)).perform(scrollTo(),replaceText("Password123!"),closeSoftKeyboard());
            onView(withId(R.id.submit)).perform(scrollTo(),click());
            waitFor(withId(R.id.primaryAction));onView(withId(R.id.primaryAction)).perform(scrollTo(),click());
            onView(withId(R.id.fieldsPanel)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.submit)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.manualMode)).perform(scrollTo(),click());
            onView(withId(R.id.smartPanel)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.summary)).perform(scrollTo(),replaceText("打印机卡纸"),closeSoftKeyboard());
            onView(withId(R.id.device)).perform(scrollTo(),replaceText("打印机"),closeSoftKeyboard());
            onView(withId(R.id.location)).perform(scrollTo(),replaceText("办公室"),closeSoftKeyboard());
            onView(withId(R.id.impactScope)).perform(scrollTo(),replaceText("办公室 5 人"),closeSoftKeyboard());
            onView(withId(R.id.smartMode)).perform(scrollTo(),click());
            onView(withId(R.id.description)).perform(scrollTo(),replaceText("这段原文只用于智能报修，手动提交不应携带。"),closeSoftKeyboard());
            onView(withId(R.id.manualMode)).perform(scrollTo(),click());
            scenario.recreate();
            onView(withId(R.id.summary)).perform(scrollTo()).check(matches(withText("打印机卡纸")));
            onView(withId(R.id.location)).perform(scrollTo()).check(matches(withText("办公室")));
            onView(withId(R.id.impactScope)).perform(scrollTo()).check(matches(withText("办公室 5 人")));
            onView(withId(R.id.summary)).perform(scrollTo(),replaceText("  "),closeSoftKeyboard());
            onView(withId(R.id.submit)).perform(scrollTo(),click());
            onView(withId(R.id.summaryLayout)).check(matches(hasError("请填写问题摘要")));
            onView(withId(R.id.summary)).check(matches(hasFocus())).check(matches(isDisplayed()));
            onView(withId(R.id.summary)).perform(replaceText("打印机卡纸"),closeSoftKeyboard());
            onView(withId(R.id.summaryLayout)).check(matches(not(hasError("请填写问题摘要"))));
            onView(withId(R.id.submit)).perform(scrollTo(),click());
            onView(withId(android.R.id.button1)).check(matches(withText(R.string.confirm_submit)));
            onView(withId(android.R.id.button2)).perform(click());
            onView(withId(R.id.submit)).perform(scrollTo(),click());onView(withId(android.R.id.button1)).perform(click());
            waitFor(withId(R.id.structured));
            onView(withId(R.id.original)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.structured)).perform(scrollTo()).check(matches(withText(org.hamcrest.Matchers.containsString("手动填写"))));
        }
    }
    private Matcher<View> hasError(String message) {
        return new TypeSafeMatcher<View>() {
            @Override public void describeTo(Description description) { description.appendText("TextInputLayout error: "+message); }
            @Override protected boolean matchesSafely(View view) {
                if(!(view instanceof TextInputLayout)) return false;
                CharSequence error=((TextInputLayout)view).getError();
                return error!=null && message.contentEquals(error);
            }
        };
    }
    private void waitFor(Matcher<View> matcher) {
        long deadline=SystemClock.uptimeMillis()+20000;Throwable last=null;
        while(SystemClock.uptimeMillis()<deadline) {
            try { onView(matcher).check(matches(isDisplayed()));return; }
            catch(AssertionError | RuntimeException e) { last=e;SystemClock.sleep(150); }
        }
        throw new AssertionError("Timed out waiting for "+matcher,last);
    }
}
