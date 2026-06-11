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
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
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

    private FrameLayout root;
    private LinearLayout toolbar;
    private TextView revealHandle;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageView securityIcon;
    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest webPermissionRequest;
    private GeolocationPermissions.Callback geolocationCallback;
    private String geolocationOrigin;
    private PendingDownload pendingDownload;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean toolbarTemporarilyRevealed;
    private float edgeTouchStartY = -1;
    private String mobileUserAgent;

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

    private void buildBrowser() {
        root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    0,
                    insets.getSystemWindowInsetTop(),
                    0,
                    insets.getSystemWindowInsetBottom()
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

        webView = new WebView(this);
        configureWebView();
        content.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        revealHandle = new TextView(this);
        revealHandle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, R.drawable.ic_expand_more);
        revealHandle.setGravity(Gravity.CENTER);
        revealHandle.setBackgroundResource(R.drawable.bg_reveal_handle);
        revealHandle.setElevation(dp(3));
        revealHandle.setContentDescription("Reveal toolbar");
        revealHandle.setOnClickListener(v -> revealToolbar());
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(48), dp(32));
        handleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        root.addView(revealHandle, handleParams);

        setContentView(root);
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
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

        ImageButton reload = toolbarButton(R.drawable.ic_refresh, "Reload");
        reload.setOnClickListener(v -> webView.reload());
        bar.addView(reload);

        LinearLayout addressContainer = new LinearLayout(this);
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
        addressBar.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateFromAddressBar();
                return true;
            }
            return false;
        });
        addressContainer.addView(addressBar, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        addressParams.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(addressContainer, addressParams);

        ImageButton menu = toolbarButton(R.drawable.ic_more_vert, "Menu");
        menu.setOnClickListener(this::showMenu);
        bar.addView(menu);
        return bar;
    }

    private ImageButton toolbarButton(int iconResource, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconResource);
        button.setImageTintList(getColorStateList(R.color.browser_icon_tint));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setBackgroundResource(R.drawable.bg_icon_button);
        button.setContentDescription(description);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
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
    }

    private void navigateFromAddressBar() {
        webView.loadUrl(normalizeAddress(addressBar.getText().toString()));
        addressBar.clearFocus();
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
        popup.getMenu().add("Hide toolbar");
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
                case "Hide toolbar":
                    toolbarTemporarilyRevealed = false;
                    toolbar.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    revealHandle.setVisibility(View.VISIBLE);
                    return true;
                case "Settings":
                    startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS);
                    return true;
                default:
                    return false;
            }
        });
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

    private void applyPreferences(boolean reloadHome) {
        SharedPreferences preferences = BrowserPreferences.get(this);
        boolean showToolbar = preferences.getBoolean(BrowserPreferences.SHOW_TOOLBAR, true);
        boolean fullscreen = preferences.getBoolean(BrowserPreferences.FULLSCREEN, false);
        boolean keepAwake = preferences.getBoolean(BrowserPreferences.KEEP_SCREEN_ON, false);
        boolean desktop = preferences.getBoolean(BrowserPreferences.DESKTOP_MODE, false);

        toolbarTemporarilyRevealed = false;
        toolbar.setVisibility(showToolbar ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(showToolbar ? View.VISIBLE : View.GONE);
        revealHandle.setVisibility(showToolbar ? View.GONE : View.VISIBLE);

        if (fullscreen) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        root.requestApplyInsets();

        if (keepAwake) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        applyDesktopMode(desktop);
        if (reloadHome) {
            webView.loadUrl(preferences.getString(
                    BrowserPreferences.START_URL,
                    BrowserPreferences.DEFAULT_START_URL
            ));
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (toolbar.getVisibility() != View.VISIBLE) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && event.getY() <= dp(64)) {
                edgeTouchStartY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    && edgeTouchStartY >= 0
                    && event.getY() - edgeTouchStartY >= dp(72)) {
                toolbarTemporarilyRevealed = true;
                revealToolbar();
                edgeTouchStartY = -1;
                Toast.makeText(this, "Toolbar revealed", Toast.LENGTH_SHORT).show();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                edgeTouchStartY = -1;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void revealToolbar() {
        toolbarTemporarilyRevealed = true;
        toolbar.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        revealHandle.setVisibility(View.GONE);
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
        } else if (toolbarTemporarilyRevealed
                && !BrowserPreferences.get(this)
                .getBoolean(BrowserPreferences.SHOW_TOOLBAR, true)) {
            toolbarTemporarilyRevealed = false;
            toolbar.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            revealHandle.setVisibility(View.VISIBLE);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
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
        toolbar.setVisibility(
                BrowserPreferences.get(this).getBoolean(BrowserPreferences.SHOW_TOOLBAR, true)
                        ? View.VISIBLE
                        : View.GONE
        );
        revealHandle.setVisibility(
                BrowserPreferences.get(this).getBoolean(BrowserPreferences.SHOW_TOOLBAR, true)
                        ? View.GONE
                        : View.VISIBLE
        );
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
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            updateAddressState(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            updateAddressState(url);
            progressBar.setVisibility(View.GONE);
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
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            setTitle(title == null || title.isEmpty() ? "QMS Browser" : title);
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
