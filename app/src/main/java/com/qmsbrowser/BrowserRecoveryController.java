package com.qmsbrowser;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public final class BrowserRecoveryController {
    private static final String TAG = "BrowserRecoveryController";
    private static final int INITIAL_RETRY_DELAY_MS = 2000;
    private static final int MAX_RETRY_DELAY_MS = 60000;
    private static final int MAX_RETRY_COUNT = 10;

    private final Context context;
    private final RecoveryListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    
    private boolean isMonitoring = false;
    private boolean hasNetworkFailure = false;
    private int retryCount = 0;
    private int currentDelayMs = INITIAL_RETRY_DELAY_MS;
    
    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (hasNetworkFailure) {
                Log.d(TAG, "Executing scheduled retry attempt #" + retryCount);
                listener.onReloadRequired();
                scheduleNextRetry();
            }
        }
    };

    public interface RecoveryListener {
        void onReloadRequired();
        void onRenderProcessCrashed();
    }

    public BrowserRecoveryController(Context context, RecoveryListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.connectivityManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public synchronized void startMonitoring() {
        if (isMonitoring) return;
        
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
                
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                Log.d(TAG, "Network became available");
                handler.post(() -> {
                    if (hasNetworkFailure) {
                        Log.d(TAG, "Connectivity restored, forcing reload and resetting retry policy");
                        resetRetryPolicy();
                        listener.onReloadRequired();
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.d(TAG, "Network was lost");
            }
        };
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
            isMonitoring = true;
            Log.d(TAG, "Registered network callback");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
        }
    }

    public synchronized void stopMonitoring() {
        if (!isMonitoring) return;
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister network callback", e);
            }
        }
        handler.removeCallbacks(retryRunnable);
        isMonitoring = false;
    }

    public void handleNetworkFailure() {
        if (!hasNetworkFailure) {
            hasNetworkFailure = true;
            retryCount = 0;
            currentDelayMs = INITIAL_RETRY_DELAY_MS;
            Log.d(TAG, "Network failure detected, starting exponential backoff retries");
            scheduleNextRetry();
        }
    }

    public void handlePagePageFinished() {
        if (hasNetworkFailure) {
            Log.d(TAG, "Page loaded successfully, resetting network failure state");
            resetRetryPolicy();
        }
    }

    public void resetRetryPolicy() {
        hasNetworkFailure = false;
        retryCount = 0;
        currentDelayMs = INITIAL_RETRY_DELAY_MS;
        handler.removeCallbacks(retryRunnable);
    }

    private void scheduleNextRetry() {
        handler.removeCallbacks(retryRunnable);
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.d(TAG, "Reached maximum retry count. Bailing out.");
            return;
        }
        
        Log.d(TAG, "Scheduling retry #" + (retryCount + 1) + " in " + currentDelayMs + "ms");
        handler.postDelayed(retryRunnable, currentDelayMs);
        
        // Exponential backoff with a cap
        retryCount++;
        currentDelayMs = Math.min(currentDelayMs * 2, MAX_RETRY_DELAY_MS);
    }

    public boolean isNetworkConnected() {
        if (connectivityManager == null) return false;
        try {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            Log.e(TAG, "Error checking network connectivity", e);
            return false;
        }
    }
}
