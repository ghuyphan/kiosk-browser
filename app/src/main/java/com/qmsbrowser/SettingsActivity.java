package com.qmsbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
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
    private CheckBox showToolbar;
    private CheckBox fullscreen;
    private CheckBox desktopMode;
    private CheckBox keepScreenOn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("QMS Browser settings");
        buildContent();
        loadPreferences();
    }

    private void buildContent() {
        int padding = dp(20);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding, padding, padding);

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
        form.addView(startUrl, matchWrap());

        showToolbar = checkbox("Show navigation and URL bar");
        fullscreen = checkbox("Use fullscreen mode");
        desktopMode = checkbox("Request desktop versions of websites");
        keepScreenOn = checkbox("Keep the screen awake");
        form.addView(showToolbar);
        form.addView(fullscreen);
        form.addView(desktopMode);
        form.addView(keepScreenOn);

        TextView hint = new TextView(this);
        hint.setText("When the toolbar is hidden, tap the small top chevron or swipe down from the top edge to reveal it.");
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, dp(8), 0, dp(20));
        form.addView(hint);

        Button save = new Button(this);
        save.setText("Save and open website");
        save.setOnClickListener(v -> savePreferences());
        form.addView(save, matchWrap());

        Button clear = new Button(this);
        clear.setText("Clear cookies and website data");
        clear.setOnClickListener(v -> confirmClearData());
        form.addView(clear, matchWrap());

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> finish());
        form.addView(cancel, matchWrap());

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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void loadPreferences() {
        SharedPreferences preferences = BrowserPreferences.get(this);
        startUrl.setText(preferences.getString(
                BrowserPreferences.START_URL,
                BrowserPreferences.DEFAULT_START_URL
        ));
        showToolbar.setChecked(preferences.getBoolean(BrowserPreferences.SHOW_TOOLBAR, true));
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
                .putBoolean(BrowserPreferences.SHOW_TOOLBAR, showToolbar.isChecked())
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
