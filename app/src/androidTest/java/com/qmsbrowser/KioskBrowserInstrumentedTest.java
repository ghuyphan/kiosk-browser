package com.qmsbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class KioskBrowserInstrumentedTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testCookieClearing() throws InterruptedException {
        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setCookie("https://example.com", "test_cookie=hello_world");
        
        final CountDownLatch latch = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            BrowserSessionManager.getInstance().clearSession(context, null, () -> {
                latch.countDown();
            });
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        String cookies = cookieManager.getCookie("https://example.com");
        assertTrue(cookies == null || cookies.isEmpty());
    }

    @Test
    public void testAutofillConfiguration() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> {
            WebView webView = BrowserSessionManager.getInstance().getActiveWebView();
            assertNotNull(webView);

            SharedPreferences prefs = BrowserPreferences.get(activity);
            boolean autofillEnabled = prefs.getBoolean(BrowserPreferences.AUTOFILL_ENABLED, true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int expected = autofillEnabled ? View.IMPORTANT_FOR_AUTOFILL_YES : View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;
                assertEquals(expected, webView.getImportantForAutofill());
            }
        });
        scenario.close();
    }

    @Test
    public void testRendererCrashRecoverySimulation() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> {
            WebView originalWebView = BrowserSessionManager.getInstance().getActiveWebView();
            assertNotNull(originalWebView);

            // Trigger manual WebView crash recovery recreation
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                // Call recreateWebViewAfterCrash on UI thread
                // Note: since recreateWebViewAfterCrash is private, we can invoke it via reflection or trigger it via listener
                try {
                    java.lang.reflect.Method method = MainActivity.class.getDeclaredMethod("recreateWebViewAfterCrash");
                    method.setAccessible(true);
                    method.invoke(activity);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Wait a brief moment for recreation
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            WebView newWebView = BrowserSessionManager.getInstance().getActiveWebView();
            assertNotNull(newWebView);
            assertNotEquals(originalWebView, newWebView);
        });
        scenario.close();
    }
}
