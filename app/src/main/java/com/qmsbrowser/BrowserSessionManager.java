package com.qmsbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class BrowserSessionManager {
    private static final String TAG = "BrowserSessionManager";
    private static final String PREF_LAST_SUCCESSFUL_URL = "last_successful_url";
    private static final String PREF_CURRENT_URL = "current_url";

    private static BrowserSessionManager instance;

    private String currentUrl;
    private String lastSuccessfulUrl;
    private java.lang.ref.WeakReference<WebView> activeWebViewRef;

    // Inactivity fields
    private final Handler inactivityHandler = new Handler(Looper.getMainLooper());
    private boolean isInactivityTimerRunning = false;
    private long inactivityThresholdMs = 15 * 60 * 1000; // default 15 minutes
    private Context appCtx;
    
    private final Runnable inactivityRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Inactivity timeout reached. Clearing session.");
            WebView webView = getActiveWebView();
            clearSession(appCtx, webView, () -> {
                // Return to configured home page after clearing
                if (webView != null) {
                    String startUrl = BrowserPreferences.get(appCtx).getString(
                            BrowserPreferences.START_URL,
                            BrowserPreferences.DEFAULT_START_URL
                    );
                    webView.loadUrl(startUrl);
                }
            });
            // Re-schedule
            scheduleInactivityTimer();
        }
    };

    private BrowserSessionManager() {}

    public static synchronized BrowserSessionManager getInstance() {
        if (instance == null) {
            instance = new BrowserSessionManager();
        }
        return instance;
    }

    public synchronized void setActiveWebView(WebView webView) {
        this.activeWebViewRef = new java.lang.ref.WeakReference<>(webView);
    }

    public synchronized WebView getActiveWebView() {
        return activeWebViewRef != null ? activeWebViewRef.get() : null;
    }

    public synchronized String getCurrentUrl() {
        return currentUrl;
    }

    public synchronized void setCurrentUrl(String url) {
        this.currentUrl = url;
    }

    public synchronized String getLastSuccessfulUrl(Context context) {
        if (lastSuccessfulUrl == null) {
            SharedPreferences prefs = BrowserPreferences.get(context);
            lastSuccessfulUrl = prefs.getString(PREF_LAST_SUCCESSFUL_URL, null);
        }
        return lastSuccessfulUrl != null ? lastSuccessfulUrl : BrowserPreferences.get(context).getString(BrowserPreferences.START_URL, BrowserPreferences.DEFAULT_START_URL);
    }

    public synchronized void setLastSuccessfulUrl(Context context, String url) {
        this.lastSuccessfulUrl = url;
        BrowserPreferences.get(context).edit()
                .putString(PREF_LAST_SUCCESSFUL_URL, url)
                .apply();
    }

    /**
     * Clears all session data: cookies, web storage, cache, history, HTTP-auth, and SSL preferences.
     */
    public void clearSession(Context context, WebView activeWebView, Runnable onComplete) {
        Log.d(TAG, "Starting clearSession");
        
        if (activeWebView == null) {
            activeWebView = getActiveWebView();
        }
        
        // 1. Clear WebStorage (databases, DOM storage, app cache, etc.)
        try {
            WebStorage.getInstance().deleteAllData();
            Log.d(TAG, "WebStorage data deleted");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing WebStorage", e);
        }

        // 2. Clear HTTP Authentication database
        try {
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword();
            Log.d(TAG, "HTTP Auth database cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing HTTP Auth database", e);
        }

        // 3. Clear Active WebView cache, history, and SSL preferences
        if (activeWebView != null) {
            try {
                activeWebView.clearCache(true);
                activeWebView.clearHistory();
                activeWebView.clearSslPreferences();
                Log.d(TAG, "Active WebView cache, history, and SSL preferences cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing active WebView details", e);
            }
        }

        // 4. Clear Cookies and invoke callback
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(value -> {
                cookieManager.flush();
                Log.d(TAG, "All cookies removed. Flush completed.");
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error clearing cookies", e);
            if (onComplete != null) {
                new Handler(Looper.getMainLooper()).post(onComplete);
            }
        }
    }

    /**
     * Checks the policy and executes app-start/overdue daily clearing at launch.
     */
    public void checkAndClearSessionOnLaunch(Context context, WebView webView, Runnable onComplete) {
        SharedPreferences prefs = BrowserPreferences.get(context);
        String policy = prefs.getString(BrowserPreferences.SESSION_CLEAR_POLICY, "never");
        
        if ("app_start".equals(policy)) {
            Log.d(TAG, "Policy is app_start. Clearing session on launch.");
            clearSession(context, webView, onComplete);
            return;
        }
        
        if ("daily".equals(policy)) {
            long nextClear = prefs.getLong(BrowserPreferences.NEXT_SCHEDULED_CLEAR_TIME, 0);
            if (nextClear > 0 && System.currentTimeMillis() >= nextClear) {
                Log.d(TAG, "Session clear is overdue. Running clearSession now.");
                long nextTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
                prefs.edit().putLong(BrowserPreferences.NEXT_SCHEDULED_CLEAR_TIME, nextTime).apply();
                scheduleDailyClear(context, nextTime);
                
                clearSession(context, webView, onComplete);
                return;
            }
        }
        
        if (onComplete != null) {
            onComplete.run();
        }
    }

    /**
     * Schedules the unique WorkManager task to clear session at a specific future time.
     */
    public void scheduleDailyClear(Context context, long nextTriggerTime) {
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            long delay = nextTriggerTime - System.currentTimeMillis();
            if (delay < 0) {
                delay = 0;
            }
            
            OneTimeWorkRequest clearRequest = new OneTimeWorkRequest.Builder(SessionClearWorker.class)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .addTag("SESSION_CLEAR_TAG")
                    .build();
                    
            workManager.enqueueUniqueWork(
                    "daily_session_clear",
                    ExistingWorkPolicy.REPLACE,
                    clearRequest
            );
            Log.d(TAG, "Enqueued daily session clear with initial delay of " + delay + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule WorkManager task", e);
        }
    }
    
    public void cancelScheduledClearing(Context context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("daily_session_clear");
            Log.d(TAG, "Cancelled scheduled daily session clear");
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel unique work", e);
        }
    }

    public void startInactivityMonitoring(Context context) {
        this.appCtx = context.getApplicationContext();
        SharedPreferences prefs = BrowserPreferences.get(context);
        String policy = prefs.getString(BrowserPreferences.SESSION_CLEAR_POLICY, "never");
        
        if ("inactivity".equals(policy)) {
            int mins = prefs.getInt(BrowserPreferences.SESSION_INACTIVITY_DURATION_MINS, 15);
            inactivityThresholdMs = (long) mins * 60 * 1000;
            isInactivityTimerRunning = true;
            scheduleInactivityTimer();
            Log.d(TAG, "Started inactivity monitoring. Threshold: " + mins + " minutes.");
        } else {
            stopInactivityMonitoring();
        }
    }
    
    public void stopInactivityMonitoring() {
        isInactivityTimerRunning = false;
        inactivityHandler.removeCallbacks(inactivityRunnable);
        Log.d(TAG, "Stopped inactivity monitoring");
    }
    
    public void resetInactivityTimer() {
        if (isInactivityTimerRunning) {
            inactivityHandler.removeCallbacks(inactivityRunnable);
            scheduleInactivityTimer();
        }
    }
    
    private void scheduleInactivityTimer() {
        inactivityHandler.postDelayed(inactivityRunnable, inactivityThresholdMs);
    }

    public void applySessionPolicy(Context context) {
        SharedPreferences prefs = BrowserPreferences.get(context);
        String policy = prefs.getString(BrowserPreferences.SESSION_CLEAR_POLICY, "never");
        
        cancelScheduledClearing(context);
        stopInactivityMonitoring();
        
        if ("daily".equals(policy)) {
            long nextTime = prefs.getLong(BrowserPreferences.NEXT_SCHEDULED_CLEAR_TIME, 0);
            if (nextTime <= System.currentTimeMillis()) {
                nextTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
                prefs.edit().putLong(BrowserPreferences.NEXT_SCHEDULED_CLEAR_TIME, nextTime).apply();
            }
            scheduleDailyClear(context, nextTime);
        } else if ("inactivity".equals(policy)) {
            startInactivityMonitoring(context);
        }
    }
}
