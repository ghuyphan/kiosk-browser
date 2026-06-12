package com.qmsbrowser;

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
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Gravity;
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

public class MainActivity extends Activity {
    private static final int REQUEST_SETTINGS = 100;
    private static final int REQUEST_FILE = 101;
    private static final int REQUEST_WEB_PERMISSIONS = 102;
    private static final int REQUEST_LOCATION = 103;
    private static final int REQUEST_DOWNLOAD = 104;
    private static final int REQUEST_SCAN_QR = 105;
    private static final long AUTO_HIDE_DELAY_MS = 2200;

    private FrameLayout root;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        
        SharedPreferences prefs = BrowserPreferences.get(this);
        remoteControlTopic = prefs.getString("remote_control_topic", null);
        if (remoteControlTopic == null) {
            remoteControlTopic = "qms-kiosk-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString("remote_control_topic", remoteControlTopic).apply();
        }
        boolean remoteEnabled = prefs.getBoolean("remote_control_enabled", false);
        if (remoteEnabled) {
            remoteClient = new RemoteControlClient(remoteControlTopic, (action, value) -> {
                runOnUiThread(() -> handleRemoteCommand(action, value));
            });
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

        buildBrowser();
        applyPreferences(false);
        showToolbar(false);

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            Uri launchUri = getIntent().getData();
            String startUrl = launchUri != null
                    ? launchUri.toString()
                    : BrowserPreferences.get(this).getString(
                            BrowserPreferences.START_URL,
                            BrowserPreferences.DEFAULT_START_URL
                    );
            webView.loadUrl(startUrl);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreenMode) {
            applyFullscreenMode();
        }
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
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.rgb(21, 94, 239))); // neon blue
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

        FrameLayout webViewContainer = new FrameLayout(this);
        webView = new BrowserWebView(this);
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

        content.addView(webViewContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
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
        LinearLayout.LayoutParams securityParams = new LinearLayout.LayoutParams(dp(16), dp(16));
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
                addressContainer.setBackground(roundedBackground(Color.rgb(13, 14, 30), Color.rgb(21, 94, 239), 21)); // Blue focus border
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
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
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
        
        // Entrance scale animation
        securityIcon.setScaleX(0f);
        securityIcon.setScaleY(0f);
        securityIcon.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        updateNavigationButtons();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        mobileUserAgent = settings.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

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
        webView.setDownloadListener(createDownloadListener());
        webView.setOnHoverListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE
                    && event.getY() <= dp(24)) {
                showToolbar(true);
            }
            return false;
        });
    }

    private void navigateFromAddressBar() {
        webView.loadUrl(normalizeAddress(addressBar.getText().toString()));
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
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return value;
        }
        if (value.contains(" ")) {
            return "https://www.google.com/search?q=" + Uri.encode(value);
        }
        return "https://" + value;
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

        addMenuRow(menu, R.drawable.ic_home, "Home", () -> webView.loadUrl(
                BrowserPreferences.get(this).getString(
                        BrowserPreferences.START_URL,
                        BrowserPreferences.DEFAULT_START_URL
                )
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
        addMenuRow(
                menu,
                R.drawable.ic_visibility_off,
                "Hide controls",
                () -> hideToolbar(true),
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
        if (uri != null && uri.getPath() != null && uri.getPath().endsWith("remote.html")) {
            return true;
        }
        if (!restrictToStartHost) {
            return true;
        }
        Uri startUri = Uri.parse(configuredStartUrl);
        String allowedHost = startUri.getHost();
        String requestedHost = uri.getHost();
        if (allowedHost == null || requestedHost == null) {
            return false;
        }
        return requestedHost.equalsIgnoreCase(allowedHost)
                || requestedHost.toLowerCase().endsWith(
                        "." + allowedHost.toLowerCase()
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
        showToolbar(false);

        boolean savePasswords = preferences.getBoolean(BrowserPreferences.SAVE_PASSWORDS, true);
        if (webView != null) {
            webView.getSettings().setSavePassword(savePasswords);
            webView.getSettings().setSaveFormData(savePasswords);
        }

        if (fromSettings) {
            if (!newStartUrl.equals(configuredStartUrl)) {
                webView.loadUrl(newStartUrl);
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
                if (gestureStartedAtTop
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
        BrowserPreferences.get(this).edit()
                .putBoolean(BrowserPreferences.TOOLBAR_HIDDEN, true)
                .apply();
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
            // uiHandler.postDelayed(hideToolbarRunnable, AUTO_HIDE_DELAY_MS);
        }
    }

    private DownloadListener createDownloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            PendingDownload download = new PendingDownload(
                    url, userAgent, contentDisposition, mimeType
            );
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = download;
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_DOWNLOAD
                );
            } else {
                enqueueDownload(download);
            }
        };
    }

    private void enqueueDownload(PendingDownload download) {
        try {
            String fileName = URLUtil.guessFileName(
                    download.url,
                    download.contentDisposition,
                    download.mimeType
            );
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(download.url));
            request.setTitle(fileName);
            request.setDescription("Downloading from " + Uri.parse(download.url).getHost());
            request.setMimeType(download.mimeType);
            request.addRequestHeader("User-Agent", download.userAgent);
            String cookies = CookieManager.getInstance().getCookie(download.url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Downloading " + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "Download failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestWebPermissions(PermissionRequest request) {
        List<String> missing = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.CAMERA);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.RECORD_AUDIO);
            }
        }

        if (missing.isEmpty()) {
            request.grant(request.getResources());
        } else {
            webPermissionRequest = request;
            requestPermissions(missing.toArray(new String[0]), REQUEST_WEB_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted(int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WEB_PERMISSIONS && webPermissionRequest != null) {
            if (allPermissionsGranted(grantResults)) {
                webPermissionRequest.grant(webPermissionRequest.getResources());
            } else {
                webPermissionRequest.deny();
            }
            webPermissionRequest = null;
        } else if (requestCode == REQUEST_LOCATION && geolocationCallback != null) {
            geolocationCallback.invoke(
                    geolocationOrigin,
                    hasLocationPermission(),
                    false
            );
            geolocationCallback = null;
            geolocationOrigin = null;
        } else if (requestCode == REQUEST_DOWNLOAD && pendingDownload != null) {
            if (allPermissionsGranted(grantResults)) {
                enqueueDownload(pendingDownload);
            } else {
                Toast.makeText(this, "Storage permission is needed to download", Toast.LENGTH_LONG)
                        .show();
            }
            pendingDownload = null;
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
                if (scanResult != null && (scanResult.startsWith("http://") || scanResult.startsWith("https://"))) {
                    webView.loadUrl(scanResult);
                    Toast.makeText(this, "Connecting to: " + scanResult, Toast.LENGTH_SHORT).show();
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

    private final class BrowserClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return openExternalIfNeeded(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return openExternalIfNeeded(Uri.parse(url));
        }

        private boolean openExternalIfNeeded(Uri uri) {
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                if (!isAllowedKioskHost(uri)) {
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



        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            updateAddressState(url);
            if (toolbarVisible) {
                progressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            updateAddressState(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            updateAddressState(url);
            progressBar.setVisibility(View.GONE);
            scheduleToolbarHide();
            if (isRefreshing) {
                hidePullIndicator();
                isRefreshing = false;
            }
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            if (request.isForMainFrame()) {
                Toast.makeText(
                        MainActivity.this,
                        "Page failed to load: " + error.getDescription(),
                        Toast.LENGTH_LONG
                ).show();
                if (isRefreshing) {
                    hidePullIndicator();
                    isRefreshing = false;
                }
            }
        }

        @Override
        public void onReceivedSslError(
                WebView view,
                SslErrorHandler handler,
                android.net.http.SslError error
        ) {
            handler.cancel();
            Toast.makeText(
                    MainActivity.this,
                    "Blocked an invalid HTTPS certificate",
                    Toast.LENGTH_LONG
            ).show();
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
            runOnUiThread(() -> requestWebPermissions(request));
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(
                String origin,
                GeolocationPermissions.Callback callback
        ) {
            if (hasLocationPermission()) {
                callback.invoke(origin, true, false);
            } else {
                geolocationOrigin = origin;
                geolocationCallback = callback;
                requestPermissions(
                        new String[]{
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        REQUEST_LOCATION
                );
            }
        }

        @Override
        public boolean onCreateWindow(
                WebView view,
                boolean isDialog,
                boolean isUserGesture,
                android.os.Message resultMsg
        ) {
            WebView popup = new WebView(MainActivity.this);
            popup.setWebViewClient(new WebViewClient() {
                private boolean checkAndLoad(String url) {
                    if (url == null || url.equals("about:blank")) {
                        return false;
                    }
                    Uri uri = Uri.parse(url);
                    if (!isAllowedKioskHost(uri)) {
                        Toast.makeText(
                                MainActivity.this,
                                "Navigation is limited to the startup website",
                                Toast.LENGTH_SHORT
                        ).show();
                        return false;
                    }
                    webView.loadUrl(url);
                    return true;
                }

                @Override
                public boolean shouldOverrideUrlLoading(
                        WebView popupView,
                        WebResourceRequest request
                ) {
                    checkAndLoad(request.getUrl().toString());
                    popupView.destroy();
                    return true;
                }

                @Override
                public boolean shouldOverrideUrlLoading(
                        WebView popupView,
                        String url
                ) {
                    checkAndLoad(url);
                    popupView.destroy();
                    return true;
                }

                @Override
                public void onPageStarted(WebView popupView, String url, Bitmap favicon) {
                    if (checkAndLoad(url)) {
                        popupView.stopLoading();
                        popupView.destroy();
                    }
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
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
        icon.setImageTintList(ColorStateList.valueOf(Color.rgb(21, 94, 239))); // neon blue
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
        final String controllerUrl = "https://raw.githack.com/ghuyphan/kiosk-browser/main/remote.html?topic=" + remoteControlTopic;
        urlValue.setText(controllerUrl);
        urlValue.setTextSize(12);
        urlValue.setTextColor(Color.rgb(21, 94, 239)); // neon blue
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
        GradientDrawable normalBg = roundedBackground(Color.rgb(21, 94, 239), Color.rgb(21, 94, 239), 12);
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

        scanBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startQrScanner();
        });
        container.addView(scanBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
                    remoteClient = new RemoteControlClient(remoteControlTopic, (action, value) -> {
                        runOnUiThread(() -> handleRemoteCommand(action, value));
                    });
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

    private void startQrScanner() {
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
            String[] qrProviders = new String[]{
                "https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=%s",
                "https://quickchart.io/qr?size=400&text=%s",
                "https://image-charts.com/chart?cht=qr&chs=400x400&chl=%s"
            };
            Bitmap bitmap = null;
            Exception lastException = null;

            for (String provider : qrProviders) {
                try {
                    String encoded = java.net.URLEncoder.encode(url, "UTF-8");
                    java.net.URL qrUrl = new java.net.URL(String.format(provider, encoded));
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) qrUrl.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setDoInput(true);
                    conn.connect();
                    if (conn.getResponseCode() == 200) {
                        try (java.io.InputStream input = conn.getInputStream()) {
                            bitmap = android.graphics.BitmapFactory.decodeStream(input);
                            if (bitmap != null) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    lastException = e;
                    Log.w("MainActivity", "Failed to load QR code from: " + provider, e);
                }
            }

            final Bitmap finalBitmap = bitmap;
            final Exception finalException = lastException;
            uiHandler.post(() -> {
                if (!isFinishing()) {
                    progress.setVisibility(View.GONE);
                    if (finalBitmap != null) {
                        imageView.setImageBitmap(finalBitmap);
                        imageView.setVisibility(View.VISIBLE);
                    } else {
                        Log.e("MainActivity", "Failed to load QR code from all providers", finalException);
                        Toast.makeText(MainActivity.this, "Failed to load QR code. Please check your connection.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }).start();
    }

    private void handleRemoteCommand(String action, String value) {
        if (webView == null || action == null) return;
        Log.d("MainActivity", "handleRemoteCommand: action=" + action + ", value=" + value);
        switch (action) {
            case "back":
                if (webView.canGoBack()) {
                    webView.goBack();
                }
                break;
            case "forward":
                if (webView.canGoForward()) {
                    webView.goForward();
                }
                break;
            case "reload":
                webView.reload();
                break;
            case "home":
                webView.loadUrl(BrowserPreferences.get(this).getString(
                        BrowserPreferences.START_URL,
                        BrowserPreferences.DEFAULT_START_URL
                ));
                break;
            case "url":
                if (value != null && !value.isEmpty()) {
                    webView.loadUrl(value);
                }
                break;
            case "type":
                if (value != null) {
                    // Escape single quotes and backslashes in the typed text
                    String escapedValue = value.replace("\\", "\\\\").replace("'", "\\'");
                    String js = "var el = document.activeElement; " +
                            "if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.contentEditable === 'true')) { " +
                            "    if (el.contentEditable === 'true') { " +
                            "        el.innerText = '" + escapedValue + "'; " +
                            "    } else { " +
                            "        el.value = '" + escapedValue + "'; " +
                            "    } " +
                            "    var evt = document.createEvent('HTMLEvents'); " +
                            "    evt.initEvent('input', true, true); " +
                            "    el.dispatchEvent(evt); " +
                            "    var evt2 = document.createEvent('HTMLEvents'); " +
                            "    evt2.initEvent('change', true, true); " +
                            "    el.dispatchEvent(evt2); " +
                            "}";
                    webView.evaluateJavascript(js, null);
                }
                break;
        }
    }
}
