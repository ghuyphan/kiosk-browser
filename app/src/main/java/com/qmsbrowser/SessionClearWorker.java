package com.qmsbrowser;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

public final class SessionClearWorker extends Worker {
    private static final String TAG = "SessionClearWorker";

    public SessionClearWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "SessionClearWorker triggered in background");
        Context ctx = getApplicationContext();
        
        try {
            boolean cleared = BrowserSessionManager.getInstance()
                    .clearSessionBlocking(ctx, 30, TimeUnit.SECONDS);
            if (!cleared) {
                Log.w(TAG, "Session clearing did not complete before timeout");
                return Result.retry();
            }
            Log.d(TAG, "Background session clearing completed");

            // Re-schedule daily clearing if active
            String policy = BrowserPreferences.get(ctx).getString(BrowserPreferences.SESSION_CLEAR_POLICY, "never");
            if ("daily".equals(policy)) {
                long nextTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
                BrowserPreferences.get(ctx).edit()
                        .putLong(BrowserPreferences.NEXT_SCHEDULED_CLEAR_TIME, nextTime)
                        .apply();
                BrowserSessionManager.getInstance().scheduleDailyClear(ctx, nextTime);
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error in SessionClearWorker", e);
            return Result.failure();
        }
    }
}
