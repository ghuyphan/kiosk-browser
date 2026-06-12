package com.qmsbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText startUrl;
    private Switch fullscreen;
    private Switch desktopMode;
    private Switch keepScreenOn;
    private Switch screenPinning;
    private Switch restrictToStartHost;
    private Switch blockExternalApps;
    private Switch preventScreenshots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Kiosk Browser settings");
        buildContent();
        loadPreferences();
    }

    private void buildContent() {
        int padding = dp(20);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding, padding, padding);
        form.setBackgroundColor(Color.rgb(248, 249, 250));

        TextView title = new TextView(this);
        title.setText("Browser settings");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(25, 33, 47));
        title.setPadding(0, 0, 0, dp(18));
        form.addView(title);

        LinearLayout websiteCard = card();
        websiteCard.addView(cardHeader(R.drawable.ic_globe, "Startup website"));

        startUrl = new EditText(this);
        startUrl.setSingleLine(true);
        startUrl.setHint("https://qms.example.com/");
        startUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        startUrl.setBackground(roundedBackground(Color.WHITE, Color.rgb(218, 220, 224), 16));
        startUrl.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        urlParams.setMargins(0, dp(8), 0, dp(12));
        websiteCard.addView(startUrl, urlParams);
        form.addView(websiteCard, cardParams());

        LinearLayout browserCard = card();
        browserCard.addView(cardHeader(R.drawable.ic_desktop, "Display"));
        fullscreen = switchControl("Fullscreen mode");
        desktopMode = switchControl("Request desktop websites");
        keepScreenOn = switchControl("Keep screen awake");
        browserCard.addView(fullscreen);
        browserCard.addView(desktopMode);
        browserCard.addView(keepScreenOn);
        form.addView(browserCard, cardParams());

        LinearLayout kioskCard = card();
        kioskCard.addView(cardHeader(R.drawable.ic_lock, "Kiosk controls"));
        screenPinning = switchControl("Android screen pinning");
        restrictToStartHost = switchControl("Stay on startup website");
        blockExternalApps = switchControl("Block links to other apps");
        preventScreenshots = switchControl("Protect screen content");
        kioskCard.addView(screenPinning);
        kioskCard.addView(restrictToStartHost);
        kioskCard.addView(blockExternalApps);
        kioskCard.addView(preventScreenshots);

        form.addView(kioskCard, cardParams());

        Button save = actionButton("Save settings", true);
        save.setOnClickListener(v -> savePreferences());
        form.addView(save, actionParams());

        Button clear = actionButton("Clear cookies and website data", false);
        clear.setOnClickListener(v -> confirmClearData());
        form.addView(clear, actionParams());

        Button cancel = actionButton("Cancel", false);
        cancel.setOnClickListener(v -> finish());
        form.addView(cancel, actionParams());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            form.setPadding(
                    padding,
                    padding + insets.getSystemWindowInsetTop(),
                    padding,
                    padding + insets.getSystemWindowInsetBottom()
            );
            return insets;
        });
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    private LinearLayout cardHeader(int iconResource, String label) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(iconResource);
        icon.setImageTintList(ColorStateList.valueOf(Color.rgb(26, 115, 232)));
        header.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(18);
        title.setTextColor(Color.rgb(25, 33, 47));
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return header;
    }

    private Switch switchControl(String label) {
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextSize(16);
        control.setTextColor(Color.rgb(32, 33, 36));
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(0, dp(5), 0, dp(5));
        control.setShowText(false);
        return control;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBackground(Color.WHITE, Color.rgb(232, 234, 237), 20));
        card.setElevation(dp(1));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        int fill = primary ? Color.rgb(26, 115, 232) : Color.WHITE;
        int stroke = primary ? Color.rgb(26, 115, 232) : Color.rgb(218, 220, 224);
        int ripple = primary ? Color.argb(50, 255, 255, 255) : Color.argb(35, 32, 33, 36);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(32, 33, 36));
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(ripple),
                roundedBackground(fill, stroke, 16),
                null
        ));
        return button;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private void loadPreferences() {
        SharedPreferences preferences = BrowserPreferences.get(this);
        startUrl.setText(preferences.getString(
                BrowserPreferences.START_URL,
                BrowserPreferences.DEFAULT_START_URL
        ));
        fullscreen.setChecked(preferences.getBoolean(BrowserPreferences.FULLSCREEN, false));
        desktopMode.setChecked(preferences.getBoolean(BrowserPreferences.DESKTOP_MODE, false));
        keepScreenOn.setChecked(preferences.getBoolean(BrowserPreferences.KEEP_SCREEN_ON, false));
        screenPinning.setChecked(preferences.getBoolean(
                BrowserPreferences.SCREEN_PINNING,
                false
        ));
        restrictToStartHost.setChecked(preferences.getBoolean(
                BrowserPreferences.RESTRICT_TO_START_HOST,
                false
        ));
        blockExternalApps.setChecked(preferences.getBoolean(
                BrowserPreferences.BLOCK_EXTERNAL_APPS,
                false
        ));
        preventScreenshots.setChecked(preferences.getBoolean(
                BrowserPreferences.PREVENT_SCREENSHOTS,
                false
        ));
    }

    private void savePreferences() {
        String normalizedUrl = MainActivity.normalizeAddress(startUrl.getText().toString());
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            startUrl.setError("Enter an HTTP or HTTPS website");
            return;
        }

        BrowserPreferences.get(this).edit()
                .putString(BrowserPreferences.START_URL, normalizedUrl)
                .putBoolean(BrowserPreferences.FULLSCREEN, fullscreen.isChecked())
                .putBoolean(BrowserPreferences.DESKTOP_MODE, desktopMode.isChecked())
                .putBoolean(BrowserPreferences.KEEP_SCREEN_ON, keepScreenOn.isChecked())
                .putBoolean(BrowserPreferences.SCREEN_PINNING, screenPinning.isChecked())
                .putBoolean(
                        BrowserPreferences.RESTRICT_TO_START_HOST,
                        restrictToStartHost.isChecked()
                )
                .putBoolean(
                        BrowserPreferences.BLOCK_EXTERNAL_APPS,
                        blockExternalApps.isChecked()
                )
                .putBoolean(
                        BrowserPreferences.PREVENT_SCREENSHOTS,
                        preventScreenshots.isChecked()
                )
                .apply();

        setResult(RESULT_OK);
        finish();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle("Clear browser data?")
                .setMessage("This signs you out of websites and clears their local storage and cache.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    WebView webView = new WebView(this);
                    webView.clearCache(true);
                    webView.clearHistory();
                    webView.destroy();
                    Toast.makeText(this, "Browser data cleared", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
