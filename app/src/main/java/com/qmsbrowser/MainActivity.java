package com.qmsbrowser;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQUEST_SETTINGS = 100;
    private static final int REQUEST_FILE = 101;
    private static final int REQUEST_WEB_PERMISSIONS = 102;
    private static final int REQUEST_LOCATION = 103;
    private static final int REQUEST_DOWNLOAD = 104;
    private static final int REQUEST_SCAN_QR = 105;
    private static final long AUTO_HIDE_DELAY_MS = 2200;

    private FrameLayout root;
    private FrameLayout webViewContainer;
    private TextView remoteCursor;
    private LinearLayout toolbar;
    private LinearLayout addressContainer;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageButton reloadButton;
    private ImageButton clearAddressButton;
    private ImageButton menuButton;
    private ImageView securityIcon;
    private BrowserWebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private FrameLayout pullIndicator;
    private ImageView pullIndicatorIcon;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest webPermissionRequest;
    private GeolocationPermissions.Callback geolocationCallback;
    private String geolocationOrigin;
    private PendingDownload pendingDownload;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideToolbarRunnable = () -> hideToolbar(true);
    private boolean toolbarVisible = true;
    private boolean addressFocused;
    private boolean pullArmed;
    private boolean isRefreshing;
    private boolean gestureStartedAtTop;
    private boolean gestureStartedWithToolbarHidden;
    private float gestureStartY = -1;
    private String mobileUserAgent;
    private String configuredStartUrl = BrowserPreferences.DEFAULT_START_URL;
    private boolean fullscreenMode;
    private boolean restrictToStartHost;
    private boolean blockExternalApps;
    private LinearLayout findBar;
    private View findBarDivider;
    private EditText findInput;
    private TextView findStatus;
    private RemoteControlClient remoteClient;
    private String remoteControlTopic;
    private String remoteControlSecret;
    private AlertDialog remoteControlDialog;
    private float remotePointerX;
    private float remotePointerY;
    private String pendingWebPermissionOrigin;
    private String[] pendingWebPermissionResources;
    
    // Phase 1: Recovery and state tracking
    private BrowserRecoveryController recoveryController;
    private boolean hasLoadError = false;

    // Phase 2: Auth, Autofill, and SSO
    private AuthenticationController authController;
    private android.app.Dialog popupDialog;
    private WebView popupWebView;

    // Phase 4: Download and permission controllers
    private DownloadController downloadController;
    private PermissionController permissionController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        
        SharedPreferences prefs = BrowserPreferences.get(this);
        remoteControlTopic = prefs.getString("remote_control_topic", null);
        remoteControlSecret = prefs.getString("remote_control_secret", null);
        if (remoteControlTopic == null || remoteControlTopic.length() < 32
                || remoteControlSecret == null || remoteControlSecret.length() < 43) {
            remoteControlTopic = "qms-kiosk-" + randomToken(24);
            remoteControlSecret = randomToken(32);
            prefs.edit()
                    .putString("remote_control_topic", remoteControlTopic)
                    .putString("remote_control_secret", remoteControlSecret)
                    .apply();
        }
        boolean remoteEnabled = prefs.getBoolean("remote_control_enabled", false);
        if (remoteEnabled) {
            remoteClient = createRemoteClient();
            remoteClient.start();
        }
        
        // Apply dark theme colors to status and navigation bars
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().setStatusBarColor(Color.rgb(16, 17, 36));
        getWindow().setNavigationBarColor(Color.rgb(16, 17, 36));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        authController = new AuthenticationController(this);
        downloadController = new DownloadController(this);
        permissionController = new PermissionController(this);
        buildBrowser();
        applyPreferences(false);

        recoveryController = new BrowserRecoveryController(this, new BrowserRecoveryController.RecoveryListener() {
            @Override
            public void onReloadRequired() {
                runOnUiThread(() -> {
                    if (webView != null) {
                        Log.d("MainActivity", "Recovery reload triggered");
                        webView.reload();
                    }
                });
            }

            @Override
            public void onRenderProcessCrashed() {
                recreateWebViewAfterCrash();
            }
        });
        recoveryController.startMonitoring();

        BrowserSessionManager.getInstance().checkAndClearSessionOnLaunch(this, webView, () -> {
            runOnUiThread(() -> {
                if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
                    Uri launchUri = getIntent().getData();
                    if (launchUri != null && isRemoteControllerUrl(launchUri.toString())) {
                        openNativeRemoteController(launchUri.toString());
                        launchUri = null;
                    }
                    String startUrl = launchUri != null
                            ? launchUri.toString()
                            : BrowserPreferences.get(this).getString(
                                    BrowserPreferences.START_URL,
                                    BrowserPreferences.DEFAULT_START_URL
                            );
                    navigateTo(startUrl, false);
                }
            });
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreenMode) {
            applyFullscreenMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BrowserSessionManager.getInstance().applySessionPolicy(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        BrowserSessionManager.getInstance().stopInactivityMonitoring();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BrowserSessionManager.getInstance().resetInactivityTimer();
        }
        return super.dispatchTouchEvent(ev);
    }



    private void buildBrowser() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 17, 36)); // Midnight background (#101124)
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    0,
                    fullscreenMode ? 0 : insets.getSystemWindowInsetTop(),
                    0,
                    fullscreenMode ? 0 : insets.getSystemWindowInsetBottom()
            );
            return insets;
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        toolbar = buildToolbar();
        content.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.rgb(124, 58, 237))); // Violet
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(45, 48, 86))); // dark track
        progressBar.setVisibility(View.GONE);

        findBar = buildFindBar();
        content.addView(findBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        findBarDivider = new View(this);
        findBarDivider.setBackgroundColor(Color.argb(20, 255, 255, 255));
        findBarDivider.setVisibility(View.GONE);
        content.addView(findBarDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        webViewContainer = new FrameLayout(this);
        webView = new BrowserWebView(this);
        BrowserSessionManager.getInstance().setActiveWebView(webView);
        configureWebView();
        webViewContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.gravity = Gravity.TOP;
        webViewContainer.addView(progressBar, progressParams);

        pullIndicator = new FrameLayout(this);
        pullIndicator.setBackground(roundedBackground(Color.rgb(21, 23, 44), Color.rgb(45, 48, 86), 20));
        pullIndicator.setElevation(dp(6));
        pullIndicator.setAlpha(0f);
        pullIndicator.setVisibility(View.GONE);

        pullIndicatorIcon = new ImageView(this);
        pullIndicatorIcon.setImageResource(R.drawable.ic_refresh);
        pullIndicatorIcon.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175)));
        pullIndicatorIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(20), dp(20));
        iconParams.gravity = Gravity.CENTER;
        pullIndicator.addView(pullIndicatorIcon, iconParams);

        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                dp(40),
                dp(40)
        );
        indicatorParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        indicatorParams.topMargin = dp(10);
        webViewContainer.addView(pullIndicator, indicatorParams);

        remoteCursor = new TextView(this);
        remoteCursor.setText("●");
        remoteCursor.setTextSize(26);
        remoteCursor.setTextColor(Color.rgb(124, 58, 237));
        remoteCursor.setGravity(Gravity.CENTER);
        remoteCursor.setVisibility(View.GONE);
        remoteCursor.setElevation(dp(20));
        webViewContainer.addView(
                remoteCursor,
                new FrameLayout.LayoutParams(dp(40), dp(40))
        );

        content.addView(webViewContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
        webViewContainer.post(() -> {
            remotePointerX = webViewContainer.getWidth() / 2f;
            remotePointerY = webViewContainer.getHeight() / 2f;
            positionRemoteCursor();
        });
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(5), dp(6), dp(5));
        bar.setBackgroundColor(Color.rgb(16, 17, 36)); // Midnight background (#101124)
        bar.setElevation(dp(3));

        backButton = toolbarButton(R.drawable.ic_arrow_back, "Back");
        backButton.setEnabled(false);
        backButton.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });
        bar.addView(backButton);

        forwardButton = toolbarButton(R.drawable.ic_arrow_forward, "Forward");
        forwardButton.setEnabled(false);
        forwardButton.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });
        bar.addView(forwardButton);

        reloadButton = toolbarButton(R.drawable.ic_refresh, "Reload");
        reloadButton.setOnClickListener(v -> {
            webView.reload();
            scheduleToolbarHide();
        });
        bar.addView(reloadButton);

        addressContainer = new LinearLayout(this);
        addressContainer.setOrientation(LinearLayout.HORIZONTAL);
        addressContainer.setGravity(Gravity.CENTER_VERTICAL);
        addressContainer.setPadding(dp(12), 0, dp(6), 0);
        addressContainer.setBackground(roundedBackground(Color.rgb(13, 14, 30), Color.rgb(45, 48, 86), 21)); // dark background/border

        securityIcon = new ImageView(this);
        securityIcon.setImageResource(R.drawable.ic_globe);
        securityIcon.setContentDescription("Site information");
        securityIcon.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175))); // Gray 400
        securityIcon.setBackground(getToolbarButtonBackground());
        securityIcon.setPadding(dp(2), dp(2), dp(2), dp(2));
        securityIcon.setOnClickListener(v -> showSecurityDialog());
        securityIcon.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false;
        });
        LinearLayout.LayoutParams securityParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        securityParams.rightMargin = dp(4);
        addressContainer.addView(securityIcon, securityParams);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(15);
        addressBar.setTextColor(Color.WHITE);
        addressBar.setHintTextColor(Color.rgb(95, 100, 138));
        addressBar.setHint("Search or enter address");
        addressBar.setSelectAllOnFocus(true);
        addressBar.setBackgroundColor(Color.TRANSPARENT);
        addressBar.setPadding(dp(8), 0, dp(6), 0);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setOnFocusChangeListener((view, focused) -> {
            setAddressFocused(focused);
            if (focused) {
                addressContainer.setBackground(roundedBackground(Color.rgb(13, 14, 30), Color.rgb(124, 58, 237), 21)); // Violet focus border
            } else {
                addressContainer.setBackground(roundedBackground(Color.rgb(13, 14, 30), Color.rgb(45, 48, 86), 21));
            }
        });
        addressBar.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateFromAddressBar();
                return true;
            }
            return false;
        });
        addressBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateClearAddressButton();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        addressContainer.addView(addressBar, new LinearLayout.LayoutParams(0, dp(42), 1));

        clearAddressButton = toolbarButton(R.drawable.ic_close, "Clear address");
        clearAddressButton.setVisibility(View.GONE);
        clearAddressButton.setOnClickListener(v -> {
            addressBar.setText("");
            addressBar.requestFocus();
        });
        addressContainer.addView(
                clearAddressButton,
                new LinearLayout.LayoutParams(dp(36), dp(36))
        );

        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        addressParams.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(addressContainer, addressParams);

        menuButton = toolbarButton(R.drawable.ic_more_vert, "Menu");
        menuButton.setOnClickListener(this::showMenu);
        bar.addView(menuButton);
        return bar;
    }

    private ImageButton toolbarButton(int iconResource, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconResource);
        button.setImageTintList(getToolbarIconTint());
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setBackground(getToolbarButtonBackground());
        button.setContentDescription(description);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return button;
    }

    private void setAddressFocused(boolean focused) {
        addressFocused = focused;
        TransitionSet transition = new TransitionSet()
                .addTransition(new ChangeBounds())
                .addTransition(new Fade())
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .setDuration(180);
        TransitionManager.beginDelayedTransition(toolbar, transition);
        backButton.setVisibility(focused ? View.GONE : View.VISIBLE);
        forwardButton.setVisibility(focused ? View.GONE : View.VISIBLE);
        reloadButton.setVisibility(focused ? View.GONE : View.VISIBLE);
        menuButton.setVisibility(focused ? View.GONE : View.VISIBLE);
        securityIcon.setVisibility(focused ? View.GONE : View.VISIBLE);
        updateClearAddressButton();

        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) addressContainer.getLayoutParams();
        params.setMargins(focused ? 0 : dp(4), 0, focused ? 0 : dp(4), 0);
        addressContainer.setLayoutParams(params);

        if (focused) {
            showToolbar(false);
            uiHandler.removeCallbacks(hideToolbarRunnable);
            addressBar.selectAll();
        } else {
            scheduleToolbarHide();
        }
    }

    private void updateClearAddressButton() {
        if (clearAddressButton == null) {
            return;
        }
        clearAddressButton.setVisibility(
                addressFocused && addressBar.length() > 0 ? View.VISIBLE : View.GONE
        );
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(webView.canGoBack());
        forwardButton.setEnabled(webView.canGoForward());
    }

    private void updateAddressState(String url) {
        addressBar.setText(url);
        boolean isHttps = url != null && url.startsWith("https://");
        securityIcon.setImageResource(isHttps ? R.drawable.ic_lock : R.drawable.ic_globe);
        securityIcon.setImageTintList(ColorStateList.valueOf(
                isHttps ? Color.rgb(16, 185, 129) : Color.rgb(156, 163, 175) // green lock vs gray globe
        ));
        securityIcon.animate().cancel();
        securityIcon.setScaleX(1f);
        securityIcon.setScaleY(1f);

        updateNavigationButtons();
    }

    private void startReloadAnimation() {
        if (reloadButton == null || reloadButton.getAnimation() != null) {
            return;
        }
        android.view.animation.RotateAnimation rotate = new android.view.animation.RotateAnimation(
                0,
                360,
                android.view.animation.Animation.RELATIVE_TO_SELF,
                0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF,
                0.5f
        );
        rotate.setDuration(850);
        rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
        rotate.setInterpolator(new android.view.animation.LinearInterpolator());
        reloadButton.startAnimation(rotate);
    }

    private void stopReloadAnimation() {
        if (reloadButton != null) {
            reloadButton.clearAnimation();
            reloadButton.setRotation(0f);
        }
    }

    private void configureWebView() {
        boolean autofill = BrowserPreferences.get(this).getBoolean(BrowserPreferences.AUTOFILL_ENABLED, true);
        WebViewConfigurator.configureSettings(webView, autofill);

        mobileUserAgent = webView.getSettings().getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        boolean thirdParty = BrowserPreferences.get(this).getBoolean(BrowserPreferences.THIRD_PARTY_COOKIES_ENABLED, false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, thirdParty);

        webView.setWebViewClient(new BrowserClient());
        webView.setWebChromeClient(new BrowserChromeClient());
        webView.setFindListener((activeMatchIndex, numberOfMatches, isDoneCounting) -> {
            if (findInput != null) {
                if (findInput.getText().toString().isEmpty()) {
                    findStatus.setText("");
                } else if (numberOfMatches == 0) {
                    findStatus.setText("0/0");
                } else {
                    findStatus.setText((activeMatchIndex + 1) + "/" + numberOfMatches);
                }
            }
        });
        if (downloadController != null) {
            webView.setDownloadListener(downloadController.createDownloadListener());
        }
        webView.setOnHoverListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE
                    && event.getY() <= dp(24)) {
                showToolbar(true);
            }
            return false;
        });
    }

    private void recreateWebViewAfterCrash() {
        runOnUiThread(() -> {
            Log.e("MainActivity", "Recreating WebView after render process crash");
            if (webView != null) {
                webViewContainer.removeView(webView);
                try {
                    webView.destroy();
                } catch (Exception e) {
                    Log.e("MainActivity", "Error destroying crashed WebView", e);
                }
            }
            webView = new BrowserWebView(this);
            BrowserSessionManager.getInstance().setActiveWebView(webView);
            configureWebView();
            webViewContainer.addView(webView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            
            // Restore last safe URL
            String safeUrl = BrowserSessionManager.getInstance().getLastSuccessfulUrl(this);
            navigateTo(safeUrl, false);
            Toast.makeText(this, "Recovered from a browser crash", Toast.LENGTH_SHORT).show();
        });
    }

    private void navigateFromAddressBar() {
        navigateTo(normalizeAddress(addressBar.getText().toString()), true);
        addressBar.clearFocus();
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
        scheduleToolbarHide();
    }

    public static String normalizeAddress(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            return BrowserPreferences.DEFAULT_START_URL;
        }
        
        // 1. If it already has a scheme, return it as-is
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return value;
        }
        
        // 2. If it contains spaces, it's definitely a search query
        if (value.contains(" ")) {
            return "https://www.google.com/search?q=" + Uri.encode(value);
        }
        
        // Heuristics to check if it's a URL:
        // - localhost
        // - IP address (e.g., 192.168.1.1)
        // - Domain name (contains a dot, and has a TLD of at least 2 chars, optional port)
        boolean isLocalhost = value.matches("^localhost(:\\d+)?$");
        boolean isIp = value.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?$");
        boolean isDomain = value.matches("^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?$");
        
        if (isLocalhost || isIp || isDomain) {
            return "https://" + value;
        }
        
        // Default to Google search
        return "https://www.google.com/search?q=" + Uri.encode(value);
    }

    private static String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        return android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE
                        | android.util.Base64.NO_PADDING
                        | android.util.Base64.NO_WRAP
        );
    }

    private boolean navigateTo(String url, boolean showError) {
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception error) {
            uri = null;
        }
        if (uri == null || !SecurityPolicy.isAllowedWebUrl(uri.toString())) {
            if (showError) {
                Toast.makeText(
                        this,
                        "Only HTTPS websites can be opened",
                        Toast.LENGTH_SHORT
                ).show();
            }
            return false;
        }
        if (!isAllowedKioskHost(uri)) {
            if (showError) {
                Toast.makeText(
                        this,
                        "Navigation is limited to the startup website",
                        Toast.LENGTH_SHORT
                ).show();
            }
            return false;
        }
        webView.loadUrl(uri.toString());
        return true;
    }

    private void showMenu(View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackground(roundedBackground(Color.rgb(27, 29, 53), Color.rgb(45, 48, 86), 18));
        menu.setElevation(dp(12));

        PopupWindow popup = new PopupWindow(
                menu,
                dp(220),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(12));
        popup.setOutsideTouchable(true);

        addMenuRow(menu, R.drawable.ic_home, "Home", () -> navigateTo(
                BrowserPreferences.get(this).getString(
                        BrowserPreferences.START_URL,
                        BrowserPreferences.DEFAULT_START_URL
                ),
                true
        ), popup);
        addMenuRow(menu, R.drawable.ic_share, "Share page", this::shareCurrentPage, popup);
        addMenuRow(menu, R.drawable.ic_search, "Find in page", this::showFindDialog, popup);
        boolean mobile = webView.getSettings().getUserAgentString().equals(mobileUserAgent);
        addMenuRow(
                menu,
                R.drawable.ic_desktop,
                mobile ? "Desktop site" : "Mobile site",
                () -> setDesktopMode(mobile),
                popup
        );
        boolean toolbarHidden = BrowserPreferences.get(this).getBoolean(BrowserPreferences.TOOLBAR_HIDDEN, false);
        addMenuRow(
                menu,
                toolbarHidden ? R.drawable.ic_visibility : R.drawable.ic_visibility_off,
                toolbarHidden ? "Show controls" : "Hide controls",
                () -> {
                    if (toolbarHidden) {
                        BrowserPreferences.get(this).edit()
                                .putBoolean(BrowserPreferences.TOOLBAR_HIDDEN, false)
                                .apply();
                        showToolbar(true);
                    } else {
                        BrowserPreferences.get(this).edit()
                                .putBoolean(BrowserPreferences.TOOLBAR_HIDDEN, true)
                                .apply();
                        hideToolbar(true);
                    }
                },
                popup
        );
        addMenuRow(menu, R.drawable.ic_key, "Remote control", this::showRemoteControlDialog, popup);
        addMenuRow(menu, R.drawable.ic_settings, "Settings", () ->
                startActivityForResult(
                        new Intent(this, SettingsActivity.class),
                        REQUEST_SETTINGS
                ), popup);

        popup.setOnDismissListener(() -> scheduleToolbarHide());
        uiHandler.removeCallbacks(hideToolbarRunnable);
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        popup.showAtLocation(
                root,
                Gravity.TOP | Gravity.END,
                dp(12),
                anchorLocation[1] + anchor.getHeight() + dp(4)
        );
    }

    private void addMenuRow(
            LinearLayout menu,
            int iconResource,
            String label,
            Runnable action,
            PopupWindow popup
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackground(getMenuRowBackground());
        row.setOnClickListener(view -> {
            popup.dismiss();
            action.run();
        });

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175))); // Gray 400
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(15);
        title.setTextColor(Color.rgb(229, 231, 235)); // Gray 200
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(14), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        menu.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private ColorStateList getToolbarIconTint() {
        int[][] states = new int[][] {
            new int[] { -android.R.attr.state_enabled },
            new int[] { android.R.attr.state_enabled }
        };
        int[] colors = new int[] {
            Color.rgb(75, 85, 99),    // Gray 600 (disabled)
            Color.rgb(229, 231, 235)  // Gray 200 (enabled)
        };
        return new ColorStateList(states, colors);
    }

    private RippleDrawable getToolbarButtonBackground() {
        int rippleColor = Color.argb(40, 255, 255, 255);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            null,
            mask
        );
    }

    private RippleDrawable getMenuRowBackground() {
        int rippleColor = Color.argb(25, 255, 255, 255);
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        return new RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            content,
            null
        );
    }

    private void shareCurrentPage() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, webView.getTitle());
        share.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
        startActivity(Intent.createChooser(share, "Share page"));
    }

    private void showFindDialog() {
        if (findBar != null) {
            findBar.setVisibility(View.VISIBLE);
            findBarDivider.setVisibility(View.VISIBLE);
            findBar.setAlpha(0f);
            findBar.setTranslationY(-dp(12));
            findBar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            findInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void hideFindBar() {
        if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(findInput.getWindowToken(), 0);
            }
            webView.clearMatches();
            
            findBar.animate()
                    .alpha(0f)
                    .translationY(-dp(12))
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        findBar.setVisibility(View.GONE);
                        findBarDivider.setVisibility(View.GONE);
                        findInput.setText("");
                        findStatus.setText("");
                    })
                    .start();
            
            webView.requestFocus();
        }
    }

    private boolean isAllowedKioskHost(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        
        // Check IDP allowlist first
        String allowlist = BrowserPreferences.get(this).getString(BrowserPreferences.IDP_ALLOWLIST, "");
        if (SecurityPolicy.isHostInAllowlist(host, allowlist)) {
            return true;
        }

        Uri startUri = Uri.parse(configuredStartUrl);
        return SecurityPolicy.isAllowedKioskHost(
                host,
                startUri.getHost(),
                restrictToStartHost
        );
    }

    private LinearLayout buildFindBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(6), dp(12), dp(6));
        bar.setBackgroundColor(Color.rgb(21, 23, 44));
        bar.setVisibility(View.GONE);

        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175)));
        bar.addView(searchIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        findInput = new EditText(this);
        findInput.setSingleLine(true);
        findInput.setHint("Find in page...");
        findInput.setHintTextColor(Color.rgb(95, 100, 138));
        findInput.setTextColor(Color.WHITE);
        findInput.setTextSize(14);
        findInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        findInput.setBackground(null);
        findInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        findInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bar.addView(findInput, inputParams);

        findStatus = new TextView(this);
        findStatus.setTextSize(12);
        findStatus.setTextColor(Color.rgb(142, 146, 178));
        findStatus.setPadding(0, 0, dp(8), 0);
        bar.addView(findStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageButton prevBtn = new ImageButton(this);
        prevBtn.setImageResource(R.drawable.ic_chevron_up);
        prevBtn.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175)));
        prevBtn.setBackground(getToolbarButtonBackground());
        prevBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        prevBtn.setScaleType(ImageView.ScaleType.CENTER);
        prevBtn.setOnClickListener(v -> webView.findNext(false));
        bar.addView(prevBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        ImageButton nextBtn = new ImageButton(this);
        nextBtn.setImageResource(R.drawable.ic_chevron_down);
        nextBtn.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175)));
        nextBtn.setBackground(getToolbarButtonBackground());
        nextBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        nextBtn.setScaleType(ImageView.ScaleType.CENTER);
        nextBtn.setOnClickListener(v -> webView.findNext(true));
        bar.addView(nextBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        ImageButton closeBtn = new ImageButton(this);
        closeBtn.setImageResource(R.drawable.ic_close);
        closeBtn.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175)));
        closeBtn.setBackground(getToolbarButtonBackground());
        closeBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        closeBtn.setScaleType(ImageView.ScaleType.CENTER);
        closeBtn.setOnClickListener(v -> hideFindBar());
        bar.addView(closeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        findInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                if (query.isEmpty()) {
                    webView.clearMatches();
                    findStatus.setText("");
                } else {
                    webView.findAllAsync(query);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        findInput.setOnEditorActionListener((v, actionId, event) -> {
            webView.findNext(true);
            return true;
        });

        return bar;
    }

    private void setDesktopMode(boolean enabled) {
        BrowserPreferences.get(this).edit()
                .putBoolean(BrowserPreferences.DESKTOP_MODE, enabled)
                .apply();
        applyDesktopMode(enabled);
        webView.reload();
    }

    private void applyDesktopMode(boolean enabled) {
        if (enabled) {
            String desktop = mobileUserAgent
                    .replace("Android", "X11; Linux x86_64")
                    .replaceAll("Mobile\\s?", "");
            webView.getSettings().setUserAgentString(desktop);
            webView.getSettings().setUseWideViewPort(true);
            webView.getSettings().setLoadWithOverviewMode(true);
        } else {
            webView.getSettings().setUserAgentString(mobileUserAgent);
            webView.getSettings().setLoadWithOverviewMode(false);
        }
    }

    private void applyPreferences(boolean fromSettings) {
        SharedPreferences preferences = BrowserPreferences.get(this);
        String newStartUrl = preferences.getString(
                BrowserPreferences.START_URL,
                BrowserPreferences.DEFAULT_START_URL
        );
        boolean previousDesktop = webView != null
                && !webView.getSettings().getUserAgentString().equals(mobileUserAgent);
        boolean fullscreen = preferences.getBoolean(BrowserPreferences.FULLSCREEN, false);
        boolean keepAwake = preferences.getBoolean(BrowserPreferences.KEEP_SCREEN_ON, false);
        boolean desktop = preferences.getBoolean(BrowserPreferences.DESKTOP_MODE, false);
        restrictToStartHost = preferences.getBoolean(
                BrowserPreferences.RESTRICT_TO_START_HOST,
                false
        );
        blockExternalApps = preferences.getBoolean(
                BrowserPreferences.BLOCK_EXTERNAL_APPS,
                false
        );
        boolean preventScreenshots = preferences.getBoolean(
                BrowserPreferences.PREVENT_SCREENSHOTS,
                false
        );

        fullscreenMode = fullscreen;
        applyFullscreenMode();
        if (fullscreenMode) {
            root.setPadding(0, 0, 0, 0);
        } else {
            root.requestApplyInsets();
        }

        if (keepAwake) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (preventScreenshots) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        applyDesktopMode(desktop);
        boolean toolbarHidden = preferences.getBoolean(BrowserPreferences.TOOLBAR_HIDDEN, false);
        if (toolbarHidden) {
            hideToolbar(false);
        } else {
            showToolbar(false);
        }

        boolean autofillEnabled = preferences.getBoolean(BrowserPreferences.AUTOFILL_ENABLED, true);
        if (webView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webView.setImportantForAutofill(autofillEnabled ? 
                        View.IMPORTANT_FOR_AUTOFILL_YES : 
                        View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            }
            boolean thirdParty = preferences.getBoolean(BrowserPreferences.THIRD_PARTY_COOKIES_ENABLED, false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, thirdParty);
        }

        if (fromSettings) {
            if (!newStartUrl.equals(configuredStartUrl)) {
                navigateTo(newStartUrl, true);
            } else if (desktop != previousDesktop) {
                webView.reload();
            }
        }
        configuredStartUrl = newStartUrl;

        scheduleToolbarHide();
    }



    private void handleWebTouch(MotionEvent event) {
        float y = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isRefreshing) {
                    break;
                }
                gestureStartY = y;
                gestureStartedAtTop = !webView.canScrollVertically(-1);
                gestureStartedWithToolbarHidden = !toolbarVisible;
                pullArmed = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (isRefreshing) {
                    break;
                }
                float totalDelta = y - gestureStartY;
                if (totalDelta > dp(14) && !toolbarVisible) {
                    showToolbar(false);
                }
                if (!gestureStartedWithToolbarHidden
                        && gestureStartedAtTop
                        && !webView.canScrollVertically(-1)
                        && totalDelta > dp(10)) {
                    updatePullIndicator(totalDelta);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isRefreshing) {
                    break;
                }
                if (pullArmed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    isRefreshing = true;
                    pullIndicator.setVisibility(View.VISIBLE);
                    pullIndicator.setAlpha(1f);
                    pullIndicator.setTranslationY(dp(44));

                    android.view.animation.RotateAnimation rotate = new android.view.animation.RotateAnimation(
                        0, 360,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
                    );
                    rotate.setDuration(1200);
                    rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
                    rotate.setInterpolator(new android.view.animation.LinearInterpolator());
                    if (pullIndicatorIcon != null) {
                        pullIndicatorIcon.startAnimation(rotate);
                    }
                    webView.reload();
                } else {
                    hidePullIndicator();
                }
                gestureStartedAtTop = false;
                gestureStartedWithToolbarHidden = false;
                gestureStartY = -1;
                pullArmed = false;
                scheduleToolbarHide();
                break;
            default:
                break;
        }
    }

    private final class BrowserWebView extends WebView {
        BrowserWebView(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            handleWebTouch(event);
            return super.onTouchEvent(event);
        }
    }

    private void applyFullscreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (fullscreenMode) {
                    controller.hide(
                            WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
                    );
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    controller.show(
                            WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
                    );
                }
            }
        } else if (fullscreenMode) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void updatePullIndicator(float distance) {
        float threshold = dp(70);
        float progress = Math.min(1f, distance / threshold);
        pullArmed = distance >= threshold;
        pullIndicator.setVisibility(View.VISIBLE);
        pullIndicator.setAlpha(progress);
        pullIndicator.setTranslationY(Math.min(dp(44), distance * 0.3f));
        if (pullIndicatorIcon != null) {
            pullIndicatorIcon.setRotation(progress * 360f);
            pullIndicatorIcon.setImageTintList(ColorStateList.valueOf(
                pullArmed ? Color.rgb(244, 63, 94) : Color.rgb(156, 163, 175)
            ));
        }
    }

    private void hidePullIndicator() {
        if (pullIndicatorIcon != null) {
            pullIndicatorIcon.clearAnimation();
        }
        pullIndicator.animate()
                .alpha(0f)
                .translationY(0f)
                .setDuration(160)
                .withEndAction(() -> pullIndicator.setVisibility(View.GONE))
                .start();
    }

    private void showToolbar(boolean scheduleHide) {
        uiHandler.removeCallbacks(hideToolbarRunnable);
        if (!toolbarVisible) {
            toolbarVisible = true;
            toolbar.setVisibility(View.VISIBLE);
            toolbar.setAlpha(0f);
            toolbar.setTranslationY(-dp(12));
            toolbar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(170)
                    .start();
        }
        if (scheduleHide) {
            scheduleToolbarHide();
        }
    }

    private void hideToolbar(boolean animate) {
        uiHandler.removeCallbacks(hideToolbarRunnable);
        if (!toolbarVisible || addressFocused || customView != null) {
            return;
        }
        toolbarVisible = false;
        progressBar.setVisibility(View.GONE);
        if (animate) {
            toolbar.animate()
                    .alpha(0f)
                    .translationY(-toolbar.getHeight())
                    .setDuration(170)
                    .withEndAction(() -> {
                        if (!toolbarVisible) {
                            toolbar.setVisibility(View.GONE);
                        }
                    })
                    .start();
        } else {
            toolbar.setVisibility(View.GONE);
        }
    }

    private void scheduleToolbarHide() {
        uiHandler.removeCallbacks(hideToolbarRunnable);
        if (!addressFocused && customView == null) {
            uiHandler.postDelayed(hideToolbarRunnable, AUTO_HIDE_DELAY_MS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WEB_PERMISSIONS || requestCode == REQUEST_LOCATION) {
            if (permissionController != null) {
                permissionController.handlePermissionResult(requestCode, grantResults, webView, restrictToStartHost, configuredStartUrl);
            }
        } else if (requestCode == REQUEST_DOWNLOAD) {
            if (downloadController != null) {
                downloadController.handlePermissionResult(grantResults);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            applyPreferences(true);
        } else if (requestCode == REQUEST_FILE && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        results[i] = clipData.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        } else if (requestCode == REQUEST_SCAN_QR) {
            if (resultCode == RESULT_OK && data != null) {
                String scanResult = data.getStringExtra("SCAN_RESULT");
                if (scanResult == null) {
                    scanResult = data.getDataString();
                }
                if (isRemoteControllerUrl(scanResult)) {
                    openNativeRemoteController(scanResult);
                } else if (scanResult != null
                        && (scanResult.startsWith("http://")
                        || scanResult.startsWith("https://"))) {
                    navigateTo(scanResult, true);
                } else {
                    Toast.makeText(this, "Invalid QR code URL", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
            hideFindBar();
        } else if (addressFocused) {
            addressBar.clearFocus();
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            keyboard.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
        } else if (toolbarVisible) {
            hideToolbar(true);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        if (recoveryController != null) {
            recoveryController.stopMonitoring();
        }
        if (remoteClient != null) {
            remoteClient.stop();
            remoteClient = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        root.removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        showToolbar(true);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean openExternalIfNeeded(Uri uri) {
        String url = uri.toString();
        if (url.startsWith("intent://")) {
            if (blockExternalApps) {
                Toast.makeText(MainActivity.this, "Opening other apps is disabled", Toast.LENGTH_SHORT).show();
                return true;
            }
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                if (intent != null) {
                    if (getPackageManager().resolveActivity(intent, 0) != null) {
                        startActivity(intent);
                        return true;
                    }
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (fallbackUrl != null && fallbackUrl.startsWith("https://")) {
                        webView.loadUrl(fallbackUrl);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error parsing intent URL", e);
            }
            Toast.makeText(MainActivity.this, "No app can open this link", Toast.LENGTH_SHORT).show();
            return true;
        }

        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (!SecurityPolicy.isAllowedWebUrl(uri.toString())
                    || !isAllowedKioskHost(uri)) {
                Toast.makeText(
                        MainActivity.this,
                        "Navigation is limited to the startup website",
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            }
            return false;
        }
        if (blockExternalApps) {
            Toast.makeText(
                    MainActivity.this,
                    "Opening other apps is disabled",
                    Toast.LENGTH_SHORT
            ).show();
            return true;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(MainActivity.this, "No app can open this link", Toast.LENGTH_SHORT)
                    .show();
        }
        return true;
    }

    private final class BrowserClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return openExternalIfNeeded(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return openExternalIfNeeded(Uri.parse(url));
        }





        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            hasLoadError = false;
            BrowserSessionManager.getInstance().setCurrentUrl(url);
            Uri uri = Uri.parse(url);
            if (("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && (!SecurityPolicy.isAllowedWebUrl(uri.toString())
                    || !isAllowedKioskHost(uri))) {
                view.stopLoading();
                Toast.makeText(
                        MainActivity.this,
                        "Navigation is limited to the startup website",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            startReloadAnimation();
            updateAddressState(url);
            if (toolbarVisible) {
                progressBar.setVisibility(View.VISIBLE);
            }
            sendRemotePageStatus();
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            updateAddressState(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            stopReloadAnimation();
            updateAddressState(url);
            progressBar.setVisibility(View.GONE);
            scheduleToolbarHide();
            if (isRefreshing) {
                hidePullIndicator();
                isRefreshing = false;
            }
            if (!hasLoadError) {
                BrowserSessionManager.getInstance().setLastSuccessfulUrl(MainActivity.this, url);
                if (recoveryController != null) {
                    recoveryController.handlePagePageFinished();
                }
            }
            sendRemotePageStatus();
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            if (request.isForMainFrame()) {
                hasLoadError = true;
                stopReloadAnimation();
                Toast.makeText(
                        MainActivity.this,
                        "Page failed to load: " + error.getDescription(),
                        Toast.LENGTH_LONG
                ).show();
                if (isRefreshing) {
                    hidePullIndicator();
                    isRefreshing = false;
                }
                if (recoveryController != null) {
                    recoveryController.handleNetworkFailure();
                }
            }
        }

        @Override
        public void onReceivedSslError(
                WebView view,
                SslErrorHandler handler,
                android.net.http.SslError error
        ) {
            hasLoadError = true;
            handler.cancel();
            Toast.makeText(
                    MainActivity.this,
                    "Blocked an invalid HTTPS certificate",
                    Toast.LENGTH_LONG
            ).show();
            if (recoveryController != null) {
                recoveryController.handleNetworkFailure();
            }
        }

        @Override
        public void onReceivedHttpAuthRequest(
                WebView view,
                android.webkit.HttpAuthHandler handler,
                String host,
                String realm
        ) {
            if (authController != null) {
                authController.handleHttpAuthRequest(view, handler, host, realm);
            } else {
                handler.cancel();
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            Log.e("MainActivity", "Render process crashed or was killed by the system.");
            recreateWebViewAfterCrash();
            return true;
        }
    }

    private final class BrowserChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(
                    newProgress >= 100 || !toolbarVisible ? View.GONE : View.VISIBLE
            );
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            setTitle(title == null || title.isEmpty() ? "Kiosk Browser" : title);
            sendRemotePageStatus();
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> newFileCallback,
                FileChooserParams fileChooserParams
        ) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
            }
            fileCallback = newFileCallback;
            try {
                Intent chooser = fileChooserParams.createIntent();
                chooser.addCategory(Intent.CATEGORY_OPENABLE);
                chooser.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                startActivityForResult(chooser, REQUEST_FILE);
                return true;
            } catch (ActivityNotFoundException error) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "No file picker is installed", Toast.LENGTH_LONG)
                        .show();
                return false;
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                if (permissionController != null) {
                    permissionController.handlePermissionRequest(request, webView, restrictToStartHost, configuredStartUrl);
                } else {
                    request.deny();
                }
            });
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            // Handled inside PermissionController if needed, else ignore
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(
                String origin,
                GeolocationPermissions.Callback callback
        ) {
            if (permissionController != null) {
                permissionController.handleGeolocationPermissionsShowPrompt(origin, callback, webView, restrictToStartHost, configuredStartUrl);
            } else {
                callback.invoke(origin, false, false);
            }
        }

        @Override
        public boolean onCreateWindow(
                WebView view,
                boolean isDialog,
                boolean isUserGesture,
                android.os.Message resultMsg
        ) {
            if (popupDialog != null) {
                popupDialog.dismiss();
            }

            popupDialog = new android.app.Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            
            LinearLayout layout = new LinearLayout(MainActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setBackgroundColor(Color.rgb(16, 17, 36));
            
            LinearLayout header = new LinearLayout(MainActivity.this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(16), dp(12), dp(16), dp(12));
            header.setBackgroundColor(Color.rgb(21, 23, 44));
            
            TextView title = new TextView(MainActivity.this);
            title.setText("Authentication / Sign In");
            title.setTextColor(Color.WHITE);
            title.setTextSize(16);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            
            ImageButton closeBtn = new ImageButton(MainActivity.this);
            closeBtn.setImageResource(R.drawable.ic_close);
            closeBtn.setImageTintList(ColorStateList.valueOf(Color.rgb(229, 231, 235)));
            closeBtn.setBackground(getToolbarButtonBackground());
            closeBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
            closeBtn.setOnClickListener(v -> popupDialog.dismiss());
            header.addView(closeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));
            
            layout.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            
            popupWebView = new WebView(MainActivity.this);
            applyPopupWebViewSettings(popupWebView);
            
            layout.addView(popupWebView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            popupDialog.setContentView(layout);
            
            popupWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView popupView, WebResourceRequest request) {
                    return handlePopupNavigation(request.getUrl());
                }
                
                @Override
                public boolean shouldOverrideUrlLoading(WebView popupView, String url) {
                    return handlePopupNavigation(Uri.parse(url));
                }
                
                private boolean handlePopupNavigation(Uri uri) {
                    if (openExternalIfNeeded(uri)) {
                        return true;
                    }
                    return false;
                }

                @Override
                public void onReceivedHttpAuthRequest(WebView view, android.webkit.HttpAuthHandler handler, String host, String realm) {
                    if (authController != null) {
                        authController.handleHttpAuthRequest(view, handler, host, realm);
                    } else {
                        handler.cancel();
                    }
                }
            });
            
            popupWebView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onCloseWindow(WebView window) {
                    super.onCloseWindow(window);
                    if (popupDialog != null) {
                        popupDialog.dismiss();
                    }
                }
            });
            
            popupDialog.setOnDismissListener(dialogInterface -> {
                if (popupWebView != null) {
                    popupWebView.destroy();
                    popupWebView = null;
                }
                popupDialog = null;
            });
            
            popupDialog.show();
            
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popupWebView);
            resultMsg.sendToTarget();
            return true;
        }

        private void applyPopupWebViewSettings(WebView popup) {
            WebSettings settings = popup.getSettings();
            WebSettings mainSettings = webView.getSettings();
            settings.setJavaScriptEnabled(mainSettings.getJavaScriptEnabled());
            settings.setDomStorageEnabled(mainSettings.getDomStorageEnabled());
            settings.setDatabaseEnabled(mainSettings.getDatabaseEnabled());
            settings.setSupportMultipleWindows(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(mainSettings.getJavaScriptCanOpenWindowsAutomatically());
            
            boolean acceptThirdParty = BrowserPreferences.get(MainActivity.this).getBoolean(BrowserPreferences.THIRD_PARTY_COOKIES_ENABLED, false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(popup, acceptThirdParty);
            CookieManager.getInstance().setAcceptCookie(true);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            toolbarVisible = false;
            toolbar.setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }
    }

    private static final class PendingDownload {
        final String url;
        final String userAgent;
        final String contentDisposition;
        final String mimeType;

        PendingDownload(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType
        ) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }
    private void showSecurityDialog() {
        String url = webView.getUrl();
        String host = url != null ? Uri.parse(url).getHost() : "";
        if (host == null) host = "";
        boolean isHttps = url != null && url.startsWith("https://");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(20), dp(24), dp(20));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(16));

        ImageView icon = new ImageView(this);
        icon.setImageResource(isHttps ? R.drawable.ic_lock : R.drawable.ic_globe);
        icon.setImageTintList(ColorStateList.valueOf(
                isHttps ? Color.rgb(16, 185, 129) : Color.rgb(244, 63, 94) // green vs warning rose-pink
        ));
        header.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView title = new TextView(this);
        title.setText(isHttps ? "Connection is secure" : "Connection is not secure");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        container.addView(header);

        // Message
        TextView message = new TextView(this);
        message.setText(isHttps 
                ? "Your information (for example, passwords or credit card numbers) is private when it is sent to this site."
                : "You should not enter any sensitive information on this site (for example, passwords or credit cards), because it could be stolen by attackers.");
        message.setTextSize(14);
        message.setTextColor(Color.rgb(142, 146, 178)); // gray 400
        message.setPadding(0, 0, 0, dp(20));
        container.addView(message);

        // Domain details
        TextView domainText = new TextView(this);
        domainText.setText("Domain: " + host);
        domainText.setTextSize(14);
        domainText.setTextColor(Color.WHITE);
        domainText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        container.addView(domainText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        
        if (dialog.getWindow() != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(27, 29, 53)); // Midnight card background
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(1), Color.rgb(45, 48, 86));
            dialog.getWindow().setBackgroundDrawable(bg);
        }
        dialog.show();
    }

    private void showRemoteControlDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(20), dp(24), dp(20));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(16));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_key);
        icon.setImageTintList(ColorStateList.valueOf(Color.rgb(124, 58, 237))); // Violet
        header.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView title = new TextView(this);
        title.setText("Remote Browser Control");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Description/Instructions
        TextView description = new TextView(this);
        description.setText("Control this browser from another device or act as a remote controller.");
        description.setTextSize(13);
        description.setTextColor(Color.rgb(142, 146, 178)); // gray 400
        description.setPadding(0, 0, 0, dp(16));
        container.addView(description, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Switch container
        LinearLayout switchContainer = new LinearLayout(this);
        switchContainer.setOrientation(LinearLayout.HORIZONTAL);
        switchContainer.setGravity(Gravity.CENTER_VERTICAL);
        switchContainer.setPadding(dp(16), dp(12), dp(16), dp(12));
        switchContainer.setBackground(roundedBackground(Color.rgb(21, 23, 44), Color.rgb(45, 48, 86), 12));

        LinearLayout switchTextLayout = new LinearLayout(this);
        switchTextLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams switchTextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        switchTextLayout.setLayoutParams(switchTextParams);

        TextView switchTitle = new TextView(this);
        switchTitle.setText("Remote Client Service");
        switchTitle.setTextSize(14);
        switchTitle.setTextColor(Color.WHITE);
        switchTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView switchDesc = new TextView(this);
        switchDesc.setText("Allow other devices to control this webview.");
        switchDesc.setTextSize(11);
        switchDesc.setTextColor(Color.rgb(142, 146, 178));

        switchTextLayout.addView(switchTitle);
        switchTextLayout.addView(switchDesc);
        switchContainer.addView(switchTextLayout);

        Switch remoteSwitch = new Switch(this);
        remoteSwitch.setChecked(remoteClient != null && remoteClient.isRunning());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int[][] states = new int[][] {
                new int[] {-android.R.attr.state_checked},
                new int[] {android.R.attr.state_checked}
            };
            int[] thumbColors = new int[] {
                Color.rgb(156, 163, 175),
                Color.WHITE
            };
            int[] trackColors = new int[] {
                Color.rgb(39, 41, 61),
                Color.rgb(124, 58, 237)
            };
            remoteSwitch.setThumbTintList(new ColorStateList(states, thumbColors));
            remoteSwitch.setTrackTintList(new ColorStateList(states, trackColors));
        }
        switchContainer.addView(remoteSwitch);

        container.addView(switchContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Spacing
        View spacing = new View(this);
        container.addView(spacing, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));

        // QR Code and URL Container
        final LinearLayout qrContainer = new LinearLayout(this);
        qrContainer.setOrientation(LinearLayout.VERTICAL);
        qrContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        qrContainer.setPadding(dp(16), dp(16), dp(16), dp(16));
        qrContainer.setBackground(roundedBackground(Color.rgb(13, 14, 30), Color.rgb(45, 48, 86), 16));
        qrContainer.setVisibility(remoteSwitch.isChecked() ? View.VISIBLE : View.GONE);

        final ProgressBar qrProgress = new ProgressBar(this);
        qrProgress.setIndeterminate(true);
        qrProgress.setVisibility(View.GONE);
        qrContainer.addView(qrProgress, new LinearLayout.LayoutParams(dp(48), dp(48)));

        final ImageView qrImage = new ImageView(this);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(180), dp(180));
        qrParams.bottomMargin = dp(12);
        qrImage.setLayoutParams(qrParams);
        qrImage.setVisibility(View.GONE);
        qrContainer.addView(qrImage);

        TextView urlTitle = new TextView(this);
        urlTitle.setText("CONTROLLER URL (CLICK TO COPY)");
        urlTitle.setTextSize(10);
        urlTitle.setTextColor(Color.rgb(95, 100, 138));
        urlTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        urlTitle.setPadding(0, 0, 0, dp(4));
        qrContainer.addView(urlTitle);

        TextView urlValue = new TextView(this);
        final String controllerUrl =
                "https://raw.githack.com/ghuyphan/kiosk-browser/main/remote.html"
                        + "#topic=" + Uri.encode(remoteControlTopic)
                        + "&secret=" + Uri.encode(remoteControlSecret);
        urlValue.setText(controllerUrl);
        urlValue.setTextSize(12);
        urlValue.setTextColor(Color.rgb(124, 58, 237)); // Violet
        urlValue.setGravity(Gravity.CENTER);
        urlValue.setFocusable(true);
        urlValue.setClickable(true);
        urlValue.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Controller URL", controllerUrl);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Copied URL to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        qrContainer.addView(urlValue);

        container.addView(qrContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Spacing before action button
        View actionSpacing = new View(this);
        container.addView(actionSpacing, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));

        // Scan button (to let this device control another device)
        TextView scanBtn = new TextView(this);
        scanBtn.setText("Scan QR Code to Control Another Device");
        scanBtn.setGravity(Gravity.CENTER);
        scanBtn.setTextColor(Color.WHITE);
        scanBtn.setTextSize(14);
        scanBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        scanBtn.setPadding(0, dp(14), 0, dp(14));
        
        // Ripple effect & background
        GradientDrawable normalBg = roundedBackground(Color.rgb(124, 58, 237), Color.rgb(124, 58, 237), 12);
        RippleDrawable rippleDrawable = new RippleDrawable(
                ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                normalBg,
                null
        );
        scanBtn.setBackground(rippleDrawable);
        scanBtn.setClickable(true);
        scanBtn.setFocusable(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        remoteControlDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (remoteControlDialog == dialog) {
                remoteControlDialog = null;
            }
        });

        scanBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startQrScanner();
        });
        container.addView(scanBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View resetSpacing = new View(this);
        container.addView(resetSpacing, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(10)
        ));

        TextView resetBtn = new TextView(this);
        resetBtn.setText("Disconnect Controller & Reset Link");
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setTextColor(Color.rgb(251, 113, 133));
        resetBtn.setTextSize(13);
        resetBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        resetBtn.setPadding(0, dp(12), 0, dp(12));
        resetBtn.setClickable(true);
        resetBtn.setFocusable(true);
        resetBtn.setBackground(roundedBackground(
                Color.rgb(21, 23, 44),
                Color.rgb(80, 35, 54),
                12
        ));
        resetBtn.setOnClickListener(v -> {
            boolean keepEnabled = remoteSwitch.isChecked();
            rotateRemoteControlCredentials(keepEnabled);
            dialog.dismiss();
            Toast.makeText(
                    MainActivity.this,
                    "Controller disconnected and link reset",
                    Toast.LENGTH_SHORT
            ).show();
            showRemoteControlDialog();
        });
        container.addView(resetBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Spacing before dismiss button
        View closeSpacing = new View(this);
        container.addView(closeSpacing, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));

        // Dismiss button
        TextView closeBtn = new TextView(this);
        closeBtn.setText("Dismiss");
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setTextColor(Color.rgb(142, 146, 178)); // gray 400
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        closeBtn.setPadding(0, dp(14), 0, dp(14));
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        closeBtn.setBackground(roundedBackground(Color.rgb(21, 23, 44), Color.rgb(45, 48, 86), 12));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        container.addView(closeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Switch toggle behavior
        remoteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = BrowserPreferences.get(MainActivity.this).edit();
            editor.putBoolean("remote_control_enabled", isChecked).apply();

            if (isChecked) {
                qrContainer.setVisibility(View.VISIBLE);
                qrProgress.setVisibility(View.VISIBLE);
                qrImage.setVisibility(View.GONE);

                if (remoteClient == null) {
                    remoteClient = createRemoteClient();
                }
                remoteClient.start();
                loadQrCode(controllerUrl, qrImage, qrProgress);
            } else {
                qrContainer.setVisibility(View.GONE);
                if (remoteClient != null) {
                    remoteClient.stop();
                }
            }
        });

        // If switch is already checked, fetch QR code on dialog load
        if (remoteSwitch.isChecked()) {
            qrProgress.setVisibility(View.VISIBLE);
            loadQrCode(controllerUrl, qrImage, qrProgress);
        }

        if (dialog.getWindow() != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(27, 29, 53)); // Midnight card background
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(1), Color.rgb(45, 48, 86));
            dialog.getWindow().setBackgroundDrawable(bg);
        }
        dialog.show();
    }

    private void rotateRemoteControlCredentials(boolean restart) {
        if (remoteClient != null) {
            remoteClient.stop();
            remoteClient = null;
        }
        remoteControlTopic = "qms-kiosk-" + randomToken(24);
        remoteControlSecret = randomToken(32);
        BrowserPreferences.get(this).edit()
                .putString("remote_control_topic", remoteControlTopic)
                .putString("remote_control_secret", remoteControlSecret)
                .putBoolean("remote_control_enabled", restart)
                .apply();
        if (restart) {
            remoteClient = createRemoteClient();
            remoteClient.start();
        }
    }

    private void startQrScanner() {
        try {
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this);
            scanner.startScan()
                    .addOnSuccessListener(barcode -> {
                        String scanResult = barcode.getRawValue();
                        if (isRemoteControllerUrl(scanResult)) {
                            openNativeRemoteController(scanResult);
                        } else if (scanResult != null
                                && (scanResult.startsWith("http://")
                                || scanResult.startsWith("https://"))) {
                            navigateTo(scanResult, true);
                        } else {
                            Toast.makeText(MainActivity.this, "Invalid QR code URL", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w("MainActivity", "Google Code Scanner failed, falling back to intent", e);
                        fallbackToIntentScanner();
                    });
        } catch (Throwable t) {
            Log.w("MainActivity", "Google Code Scanner not available, falling back to intent", t);
            fallbackToIntentScanner();
        }
    }

    private void fallbackToIntentScanner() {
        Intent intent = new Intent("android.provider.action.SCAN_WITHOUT_CREDENTIALS");
        try {
            startActivityForResult(intent, REQUEST_SCAN_QR);
        } catch (ActivityNotFoundException e) {
            // Fallback to ZXing scan intent
            Intent zxingIntent = new Intent("com.google.zxing.client.android.SCAN");
            zxingIntent.putExtra("SCAN_MODE", "QR_CODE_MODE");
            try {
                startActivityForResult(zxingIntent, REQUEST_SCAN_QR);
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(MainActivity.this, "No QR scanner found. Install a scanner app like ZXing.", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void loadQrCode(String url, ImageView imageView, ProgressBar progress) {
        new Thread(() -> {
            Bitmap bitmap = null;
            Exception failure = null;
            try {
                int size = 400;
                BitMatrix matrix = new MultiFormatWriter().encode(
                        url,
                        BarcodeFormat.QR_CODE,
                        size,
                        size
                );
                bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
            } catch (Exception error) {
                failure = error;
            }

            final Bitmap finalBitmap = bitmap;
            final Exception finalException = failure;
            uiHandler.post(() -> {
                if (!isFinishing()) {
                    progress.setVisibility(View.GONE);
                    if (finalBitmap != null) {
                        imageView.setImageBitmap(finalBitmap);
                        imageView.setVisibility(View.VISIBLE);
                    } else {
                        Log.e("MainActivity", "Failed to generate QR code", finalException);
                        Toast.makeText(
                                MainActivity.this,
                                "Failed to generate QR code",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            });
        }).start();
    }

    private RemoteControlClient createRemoteClient() {
        return new RemoteControlClient(
                remoteControlTopic,
                remoteControlSecret,
                (commandId, action, value) -> runOnUiThread(
                        () -> handleRemoteCommand(commandId, action, value)
                )
        );
    }

    private boolean isRemoteControllerUrl(String value) {
        if (value == null) {
            return false;
        }
        Uri uri = Uri.parse(value);
        String fragment = uri.getFragment();
        if (fragment == null || uri.getPath() == null
                || !uri.getPath().endsWith("/remote.html")) {
            return false;
        }
        android.net.UrlQuerySanitizer sanitizer = new android.net.UrlQuerySanitizer();
        sanitizer.setAllowUnregisteredParamaters(true);
        sanitizer.parseQuery(fragment);
        return sanitizer.getValue("topic") != null && sanitizer.getValue("secret") != null;
    }

    private void openNativeRemoteController(String controllerUrl) {
        Intent intent = new Intent(this, RemoteControllerActivity.class);
        intent.putExtra(RemoteControllerActivity.EXTRA_CONTROLLER_URL, controllerUrl);
        startActivity(intent);
    }

    private void handleRemoteCommand(String commandId, String action, String value) {
        if (webView == null || action == null) {
            sendRemoteAck(commandId, false, "Browser is unavailable");
            return;
        }
        Log.d("MainActivity", "Handling authenticated remote command: " + action);
        boolean success = true;
        String message = "OK";
        try {
            switch (action) {
                case "hello":
                    if (remoteControlDialog != null && remoteControlDialog.isShowing()) {
                        remoteControlDialog.dismiss();
                    }
                    Toast.makeText(this, "Remote controller connected", Toast.LENGTH_SHORT).show();
                    sendRemotePageStatus();
                    message = "Connected";
                    break;
                case "back":
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        success = false;
                        message = "No back history";
                    }
                    break;
                case "forward":
                    if (webView.canGoForward()) {
                        webView.goForward();
                    } else {
                        success = false;
                        message = "No forward history";
                    }
                    break;
                case "reload":
                    webView.reload();
                    break;
                case "home":
                    success = navigateTo(BrowserPreferences.get(this).getString(
                            BrowserPreferences.START_URL,
                            BrowserPreferences.DEFAULT_START_URL
                    ), false);
                    message = success ? "Opening home" : "Home URL was blocked";
                    break;
                case "url":
                    success = value != null && !value.isEmpty() && navigateTo(value, false);
                    message = success ? "Opening URL" : "URL was blocked";
                    break;
                case "pointer_move":
                    moveRemotePointer(new JSONObject(value));
                    break;
                case "tap_at":
                    moveRemotePointerTo(new JSONObject(value));
                    dispatchRemoteTap(60);
                    break;
                case "tap":
                    dispatchRemoteTap(60);
                    break;
                case "double_tap":
                    dispatchRemoteTap(60);
                    uiHandler.postDelayed(() -> dispatchRemoteTap(60), 110);
                    break;
                case "long_press":
                    dispatchRemoteTap(650);
                    break;
                case "scroll":
                    JSONObject scroll = new JSONObject(value);
                    webView.scrollBy(scroll.optInt("dx", 0), scroll.optInt("dy", 0));
                    break;
                case "type":
                    insertRemoteText(value == null ? "" : value);
                    break;
                case "key":
                    success = dispatchRemoteKey(value);
                    message = success ? "Key sent" : "Unsupported key";
                    break;
                case "page_status":
                    sendRemotePageStatus();
                    message = "Status sent";
                    break;
                default:
                    success = false;
                    message = "Unsupported command";
                    break;
            }
        } catch (Exception error) {
            success = false;
            message = "Invalid command payload";
            Log.w("MainActivity", "Remote command failed", error);
        }
        boolean transientCommand = "pointer_move".equals(action) || "scroll".equals(action);
        if (!transientCommand) {
            sendRemoteAck(commandId, success, message);
            sendRemotePageStatus();
        }
    }

    private void moveRemotePointer(JSONObject movement) {
        if (webViewContainer == null || remoteCursor == null) {
            return;
        }
        float sensitivity = (float) movement.optDouble("sensitivity", 1.0);
        sensitivity = Math.max(0.25f, Math.min(3f, sensitivity));
        remotePointerX += movement.optDouble("dx", 0) * sensitivity;
        remotePointerY += movement.optDouble("dy", 0) * sensitivity;
        remotePointerX = Math.max(0, Math.min(webView.getWidth() - 1, remotePointerX));
        remotePointerY = Math.max(0, Math.min(webView.getHeight() - 1, remotePointerY));
        positionRemoteCursor();
        remoteCursor.setVisibility(View.VISIBLE);
        remoteCursor.setAlpha(1f);
    }

    private void moveRemotePointerTo(JSONObject position) {
        if (webView == null || remoteCursor == null) {
            return;
        }
        double normalizedX = Math.max(0, Math.min(1, position.optDouble("x", 0.5)));
        double normalizedY = Math.max(0, Math.min(1, position.optDouble("y", 0.5)));
        remotePointerX = (float) (normalizedX * Math.max(0, webView.getWidth() - 1));
        remotePointerY = (float) (normalizedY * Math.max(0, webView.getHeight() - 1));
        positionRemoteCursor();
        remoteCursor.setVisibility(View.VISIBLE);
        remoteCursor.setAlpha(1f);
    }

    private void positionRemoteCursor() {
        if (remoteCursor == null) {
            return;
        }
        remoteCursor.setX(remotePointerX - dp(20));
        remoteCursor.setY(remotePointerY - dp(20));
    }

    private void dispatchRemoteTap(long holdDurationMs) {
        if (webView == null) {
            return;
        }
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                remotePointerX,
                remotePointerY,
                0
        );
        webView.dispatchTouchEvent(down);
        down.recycle();
        uiHandler.postDelayed(() -> {
            if (webView == null) {
                return;
            }
            long eventTime = SystemClock.uptimeMillis();
            MotionEvent up = MotionEvent.obtain(
                    downTime,
                    eventTime,
                    MotionEvent.ACTION_UP,
                    remotePointerX,
                    remotePointerY,
                    0
            );
            webView.dispatchTouchEvent(up);
            up.recycle();
            if (remoteCursor != null) {
                remoteCursor.animate()
                        .scaleX(1.45f)
                        .scaleY(1.45f)
                        .setDuration(90)
                        .withEndAction(() -> remoteCursor.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(120)
                                .start())
                        .start();
            }
        }, holdDurationMs);
    }

    private boolean dispatchRemoteKey(String key) {
        int keyCode;
        switch (key == null ? "" : key.toLowerCase(java.util.Locale.US)) {
            case "backspace":
                keyCode = KeyEvent.KEYCODE_DEL;
                break;
            case "delete":
                keyCode = KeyEvent.KEYCODE_FORWARD_DEL;
                break;
            case "enter":
                keyCode = KeyEvent.KEYCODE_ENTER;
                break;
            case "tab":
                keyCode = KeyEvent.KEYCODE_TAB;
                break;
            case "escape":
                keyCode = KeyEvent.KEYCODE_ESCAPE;
                break;
            case "arrowup":
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                break;
            case "arrowdown":
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            case "arrowleft":
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case "arrowright":
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            default:
                return false;
        }
        long time = SystemClock.uptimeMillis();
        webView.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0));
        webView.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0));
        return true;
    }

    private void insertRemoteText(String text) {
        String escapedValue = JSONObject.quote(text);
        String js = "(() => {" +
                "const el=document.activeElement;" +
                "if(!el||el.type==='password')return false;" +
                "const editable=el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable;" +
                "if(!editable)return false;" +
                "el.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,inputType:'insertText',data:"
                + escapedValue + "}));" +
                "if(el.isContentEditable){document.execCommand('insertText',false," + escapedValue + ");}" +
                "else{const start=el.selectionStart??el.value.length;const end=el.selectionEnd??start;" +
                "const next=el.value.slice(0,start)+" + escapedValue + "+el.value.slice(end);" +
                "const setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value')?.set;" +
                "if(setter)setter.call(el,next);else el.value=next;" +
                "const caret=start+" + text.length() + ";el.setSelectionRange(caret,caret);}" +
                "el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:"
                + escapedValue + "}));return true;})()";
        webView.evaluateJavascript(js, null);
    }

    private void sendRemoteAck(String commandId, boolean success, String message) {
        if (remoteClient == null || commandId == null || commandId.isEmpty()) {
            return;
        }
        try {
            JSONObject ack = new JSONObject();
            ack.put("commandId", commandId);
            ack.put("success", success);
            ack.put("message", message);
            remoteClient.sendEvent("ack", ack.toString());
        } catch (Exception error) {
            Log.w("MainActivity", "Failed to create remote acknowledgement", error);
        }
    }

    private void sendRemotePageStatus() {
        if (remoteClient == null || webView == null) {
            return;
        }
        try {
            JSONObject status = new JSONObject();
            status.put("url", webView.getUrl() == null ? "" : webView.getUrl());
            status.put("title", webView.getTitle() == null ? "" : webView.getTitle());
            status.put("loading", progressBar != null && progressBar.getVisibility() == View.VISIBLE);
            status.put("canGoBack", webView.canGoBack());
            status.put("canGoForward", webView.canGoForward());
            status.put("viewportWidth", webView.getWidth());
            status.put("viewportHeight", webView.getHeight());
            remoteClient.sendEvent("status", status.toString());
        } catch (Exception error) {
            Log.w("MainActivity", "Failed to create remote page status", error);
        }
    }
}
