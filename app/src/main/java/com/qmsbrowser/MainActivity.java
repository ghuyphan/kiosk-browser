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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_SETTINGS = 100;
    private static final int REQUEST_FILE = 101;
    private static final int REQUEST_WEB_PERMISSIONS = 102;
    private static final int REQUEST_LOCATION = 103;
    private static final int REQUEST_DOWNLOAD = 104;
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
    private TextView pullIndicator;
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
    private boolean gestureStartedAtTop;
    private float gestureStartY = -1;
    private String mobileUserAgent;
    private String configuredStartUrl = BrowserPreferences.DEFAULT_START_URL;
    private boolean fullscreenMode;
    private boolean screenPinning;
    private boolean restrictToStartHost;
    private boolean blockExternalApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        
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
        if (BrowserPreferences.get(this).getBoolean(
                BrowserPreferences.TOOLBAR_HIDDEN,
                false
        )) {
            hideToolbar(false);
        }

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

    @Override
    protected void onResume() {
        super.onResume();
        applyScreenPinning();
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
        content.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        FrameLayout webViewContainer = new FrameLayout(this);
        webView = new BrowserWebView(this);
        configureWebView();
        webViewContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        pullIndicator = new TextView(this);
        pullIndicator.setText("Pull to refresh");
        pullIndicator.setTextSize(13);
        pullIndicator.setTextColor(Color.rgb(244, 63, 94)); // rose pink matching app icon
        pullIndicator.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pullIndicator.setGravity(Gravity.CENTER);
        pullIndicator.setBackground(roundedBackground(Color.rgb(27, 29, 53), Color.rgb(45, 48, 86), 20));
        pullIndicator.setElevation(dp(6));
        pullIndicator.setAlpha(0f);
        pullIndicator.setVisibility(View.GONE);
        pullIndicator.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
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
        button.setScaleType(ImageView.ScaleType.CENTER);
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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        mobileUserAgent = settings.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new BrowserClient());
        webView.setWebChromeClient(new BrowserChromeClient());
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
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Text to find");
        int padding = dp(20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, 0, padding, 0);
        container.addView(query);

        new AlertDialog.Builder(this)
                .setTitle("Find in page")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Find", (dialog, which) ->
                        webView.findAllAsync(query.getText().toString()))
                .show();
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
        screenPinning = preferences.getBoolean(BrowserPreferences.SCREEN_PINNING, false);
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

        if (fromSettings) {
            if (!newStartUrl.equals(configuredStartUrl)) {
                webView.loadUrl(newStartUrl);
            } else if (desktop != previousDesktop) {
                webView.reload();
            }
        }
        configuredStartUrl = newStartUrl;

        scheduleToolbarHide();
        applyScreenPinning();
    }

    private void applyScreenPinning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) {
            return;
        }
        int state = manager.getLockTaskModeState();
        try {
            if (screenPinning && state == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask();
            } else if (!screenPinning && state == ActivityManager.LOCK_TASK_MODE_PINNED) {
                stopLockTask();
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            Toast.makeText(
                    this,
                    "Screen pinning is not available on this device",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void handleWebTouch(MotionEvent event) {
        float y = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureStartY = y;
                gestureStartedAtTop = !webView.canScrollVertically(-1);
                pullArmed = false;
                break;
            case MotionEvent.ACTION_MOVE:
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
                if (pullArmed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    pullIndicator.setText("Refreshing…");
                    webView.reload();
                }
                hidePullIndicator();
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
        float threshold = dp(92);
        float progress = Math.min(1f, distance / threshold);
        pullArmed = distance >= threshold;
        pullIndicator.setText(pullArmed ? "Release to refresh" : "Pull to refresh");
        pullIndicator.setVisibility(View.VISIBLE);
        pullIndicator.setAlpha(progress);
        pullIndicator.setTranslationY(Math.min(dp(34), distance * 0.25f));
    }

    private void hidePullIndicator() {
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
            uiHandler.postDelayed(hideToolbarRunnable, AUTO_HIDE_DELAY_MS);
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
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
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

        private boolean isAllowedKioskHost(Uri uri) {
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
                @Override
                public boolean shouldOverrideUrlLoading(
                        WebView popupView,
                        WebResourceRequest request
                ) {
                    webView.loadUrl(request.getUrl().toString());
                    popupView.destroy();
                    return true;
                }

                @Override
                public void onPageStarted(WebView popupView, String url, Bitmap favicon) {
                    if (url != null && !url.equals("about:blank")) {
                        webView.loadUrl(url);
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
}
