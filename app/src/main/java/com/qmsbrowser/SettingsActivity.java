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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText startUrl;
    private CheckBox fullscreen;
    private CheckBox desktopMode;
    private CheckBox keepScreenOn;

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
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25, 33, 47));
        title.setPadding(0, 0, 0, dp(16));
        form.addView(title);

        TextView urlLabel = new TextView(this);
        urlLabel.setText("Website opened when the app launches");
        urlLabel.setTextSize(16);
        form.addView(urlLabel);

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
        form.addView(startUrl, urlParams);

        fullscreen = checkbox("Use fullscreen mode");
        desktopMode = checkbox("Request desktop versions of websites");
        keepScreenOn = checkbox("Keep the screen awake");
        form.addView(fullscreen);
        form.addView(desktopMode);
        form.addView(keepScreenOn);

        TextView hint = new TextView(this);
        hint.setText("Pull down at the top of a page to reveal the controls. Pull farther and release to refresh.");
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, dp(8), 0, dp(20));
        form.addView(hint);

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
        setContentView(scroll);
    }

    private CheckBox checkbox(String label) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(label);
        checkBox.setTextSize(16);
        checkBox.setGravity(Gravity.CENTER_VERTICAL);
        checkBox.setPadding(0, dp(8), 0, dp(8));
        return checkBox;
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
