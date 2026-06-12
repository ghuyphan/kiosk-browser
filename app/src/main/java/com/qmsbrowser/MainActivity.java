package com.qmsbrowser;

import android.Manifest;
import android.app.Activity;
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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.PopupMenu;
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
    private boolean autoHideToolbar = true;
    private boolean toolbarVisible = true;
    private boolean addressFocused;
    private boolean pullArmed;
    private boolean gestureStartedAtTop;
    private float gestureStartY = -1;
    private String mobileUserAgent;
    private String configuredStartUrl = BrowserPreferences.DEFAULT_START_URL;
    private boolean fullscreenMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildBrowser();
        applyPreferences(false);

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
        content.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        webView = new BrowserWebView(this);
        configureWebView();
        content.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        pullIndicator = new TextView(this);
        pullIndicator.setText("Pull to refresh");
        pullIndicator.setTextSize(13);
        pullIndicator.setTextColor(Color.rgb(60, 64, 67));
        pullIndicator.setGravity(Gravity.CENTER);
        pullIndicator.setBackgroundResource(R.drawable.bg_pull_indicator);
        pullIndicator.setElevation(dp(6));
        pullIndicator.setAlpha(0f);
        pullIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        indicatorParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        indicatorParams.topMargin = dp(10);
        root.addView(pullIndicator, indicatorParams);

        setContentView(root);
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(5), dp(6), dp(5));
        bar.setBackgroundColor(Color.WHITE);
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
        addressContainer.setBackgroundResource(R.drawable.bg_address_bar);

        securityIcon = new ImageView(this);
        securityIcon.setImageResource(R.drawable.ic_globe);
        securityIcon.setContentDescription("Site information");
        addressContainer.addView(securityIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(16);
        addressBar.setTextColor(Color.rgb(32, 33, 36));
        addressBar.setHintTextColor(Color.rgb(95, 99, 104));
        addressBar.setHint("Search or enter address");
        addressBar.setSelectAllOnFocus(true);
        addressBar.setBackgroundColor(Color.TRANSPARENT);
        addressBar.setPadding(dp(8), 0, dp(6), 0);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setOnFocusChangeListener((view, focused) -> setAddressFocused(focused));
        addressBar.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateFromAddressBar();
                return true;
            }
            return false;
        });
        addressContainer.addView(addressBar, new LinearLayout.LayoutParams(0, dp(42), 1));
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
        button.setImageTintList(getColorStateList(R.color.browser_icon_tint));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setBackgroundResource(R.drawable.bg_icon_button);
        button.setContentDescription(description);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return button;
    }

    private void setAddressFocused(boolean focused) {
        addressFocused = focused;
        backButton.setVisibility(focused ? View.GONE : View.VISIBLE);
        forwardButton.setVisibility(focused ? View.GONE : View.VISIBLE);
        reloadButton.setVisibility(focused ? View.GONE : View.VISIBLE);
        menuButton.setVisibility(focused ? View.GONE : View.VISIBLE);
        securityIcon.setVisibility(focused ? View.GONE : View.VISIBLE);

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

    private void updateNavigationButtons() {
        backButton.setEnabled(webView.canGoBack());
        forwardButton.setEnabled(webView.canGoForward());
    }

    private void updateAddressState(String url) {
        addressBar.setText(url);
        securityIcon.setImageResource(
                url != null && url.startsWith("https://")
                        ? R.drawable.ic_lock
                        : R.drawable.ic_globe
        );
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
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Home");
        popup.getMenu().add("Share page");
        popup.getMenu().add("Find in page");
        popup.getMenu().add(webView.getSettings().getUserAgentString().equals(mobileUserAgent)
                ? "Desktop site"
                : "Mobile site");
        popup.getMenu().add("Hide controls now");
        popup.getMenu().add("Settings");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "Home":
                    webView.loadUrl(BrowserPreferences.get(this).getString(
                            BrowserPreferences.START_URL,
                            BrowserPreferences.DEFAULT_START_URL
                    ));
                    return true;
                case "Share page":
                    shareCurrentPage();
                    return true;
                case "Find in page":
                    showFindDialog();
                    return true;
                case "Desktop site":
                    setDesktopMode(true);
                    return true;
                case "Mobile site":
                    setDesktopMode(false);
                    return true;
                case "Hide controls now":
                    hideToolbar(true);
                    return true;
                case "Settings":
                    startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS);
                    return true;
                default:
                    return false;
            }
        });
        popup.setOnDismissListener(menu -> scheduleToolbarHide());
        uiHandler.removeCallbacks(hideToolbarRunnable);
        popup.show();
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
        boolean previousAutoHide = autoHideToolbar;
        boolean previousDesktop = webView != null
                && !webView.getSettings().getUserAgentString().equals(mobileUserAgent);
        autoHideToolbar = preferences.getBoolean(
                BrowserPreferences.AUTO_HIDE_TOOLBAR,
                true
        );
        boolean fullscreen = preferences.getBoolean(BrowserPreferences.FULLSCREEN, false);
        boolean keepAwake = preferences.getBoolean(BrowserPreferences.KEEP_SCREEN_ON, false);
        boolean desktop = preferences.getBoolean(BrowserPreferences.DESKTOP_MODE, false);

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

        if (autoHideToolbar) {
            scheduleToolbarHide();
        } else if (previousAutoHide) {
            uiHandler.removeCallbacks(hideToolbarRunnable);
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
        if (!autoHideToolbar || !toolbarVisible || addressFocused || customView != null) {
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
        if (autoHideToolbar && !addressFocused && customView == null) {
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
        } else if (toolbarVisible && autoHideToolbar) {
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
                return false;
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
}
