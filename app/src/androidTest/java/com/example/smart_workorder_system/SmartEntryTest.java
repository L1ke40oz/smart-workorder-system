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
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;

/** Run only against an isolated backend configured with the documented local OpenAI-compatible fixture. */
@RunWith(AndroidJUnit4.class)
public class SmartEntryTest {
    @Test public void smartPreviewFillsFieldsAndAllowsCorrectionsBeforeCreating() {
        assumeTrue("Requires the local provider fixture",Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("smartFixture","false")));
        String apiBaseUrl=InstrumentationRegistry.getArguments().getString("apiBaseUrl",BuildConfig.API_BASE_URL);
        InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("session",0).edit()
                .clear().putString("baseUrl",apiBaseUrl).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.toggleMode)).perform(scrollTo(),click());
            onView(withId(R.id.username)).perform(scrollTo(),replaceText("smart_"+System.currentTimeMillis()));
            onView(withId(R.id.displayName)).perform(scrollTo(),replaceText("智能录入验收用户"));
            onView(withId(R.id.password)).perform(scrollTo(),replaceText("Password123!"),closeSoftKeyboard());
            onView(withId(R.id.submit)).perform(scrollTo(),click());
            waitFor(withId(R.id.primaryAction));onView(withId(R.id.primaryAction)).perform(scrollTo(),click());
            waitFor(allEnabled(R.id.analyze));
            onView(withId(R.id.analyze)).perform(scrollTo(),click());
            onView(withId(R.id.descriptionLayout)).check(matches(hasError("请填写故障描述")));
            onView(withId(R.id.description)).check(matches(hasFocus()));
            String original="今天上午办公室 5 人打印时提示 E03，重启后仍卡纸。";
            onView(withId(R.id.description)).perform(scrollTo(),replaceText(original),closeSoftKeyboard());
            onView(withId(R.id.analyze)).perform(scrollTo(),click());waitFor(withId(R.id.fieldsPanel));
            onView(withId(R.id.summary)).perform(scrollTo()).check(matches(withText("打印机卡纸")));
            onView(withId(R.id.impactScope)).perform(scrollTo()).check(matches(withText("办公室 5 人")));
            onView(withId(R.id.faultStartedAt)).perform(scrollTo()).check(matches(withText("今天上午")));
            onView(withId(R.id.errorMessage)).perform(scrollTo()).check(matches(withText("E03")));
            onView(withId(R.id.attemptedActions)).perform(scrollTo()).check(matches(withText("重启后仍卡纸")));
            // Changing the narrative invalidates the preview instead of sending a stale analysis ID.
            onView(withId(R.id.description)).perform(scrollTo(),replaceText(original+"补充：还有异响。"),closeSoftKeyboard());
            onView(withId(R.id.fieldsPanel)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.submit)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.analyze)).perform(scrollTo(),click());waitFor(withId(R.id.fieldsPanel));
            onView(withId(R.id.impactScope)).perform(scrollTo(),replaceText("办公室 6 人（核对后修正）"),closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.impactScope)).perform(scrollTo()).check(matches(withText("办公室 6 人（核对后修正）")));
            onView(withId(R.id.submit)).perform(scrollTo(),click());
            onView(withId(android.R.id.button2)).perform(click()); // Cancelling keeps the draft editable.
            onView(withId(R.id.submit)).perform(scrollTo(),click());onView(withId(android.R.id.button1)).perform(click());
            waitFor(withId(R.id.original));
            onView(withId(R.id.original)).perform(scrollTo()).check(matches(withText(original+"补充：还有异响。")));
            onView(withId(R.id.structured)).perform(scrollTo()).check(matches(withText(containsString("办公室 6 人"))))
                    .check(matches(withText(containsString("智能整理后确认"))));
        }
    }
    private Matcher<View> allEnabled(int id) { return org.hamcrest.Matchers.allOf(withId(id),isEnabled()); }
    private Matcher<View> hasError(String text) {
        return new TypeSafeMatcher<View>() {
            @Override public void describeTo(Description description) { description.appendText("error: "+text); }
            @Override protected boolean matchesSafely(View view) {
                return view instanceof TextInputLayout && text.contentEquals(((TextInputLayout)view).getError()==null
                        ? "" : ((TextInputLayout)view).getError());
            }
        };
    }
    private void waitFor(Matcher<View> matcher) {
        long deadline=SystemClock.uptimeMillis()+20000;Throwable last=null;
        while(SystemClock.uptimeMillis()<deadline) {
            try { onView(matcher).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));return; }
            catch(AssertionError | RuntimeException e) { last=e;SystemClock.sleep(150); }
        }
        throw new AssertionError("Timed out waiting for "+matcher,last);
    }
}
