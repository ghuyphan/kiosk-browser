package com.qmsbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText startUrl;
    private CustomSwitch fullscreen;
    private CustomSwitch desktopMode;
    private CustomSwitch keepScreenOn;
    private CustomSwitch restrictToStartHost;
    private CustomSwitch blockExternalApps;
    private CustomSwitch preventScreenshots;
    private CustomSwitch autofillEnabled;
    private CustomSwitch thirdPartyCookies;
    private EditText idpAllowlist;

    // Phase 3: Session policies
    private String selectedPolicy = "never";
    private int selectedInactivityMins = 15;
    private TextView policyDesc;
    private TextView inactivityDesc;
    private LinearLayout inactivityRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Kiosk Browser settings");
        
        // Apply dark theme colors to status and navigation bars
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
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

        buildContent();
        loadPreferences();
    }

    private void buildContent() {
        int padding = dp(16);
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.rgb(16, 17, 36)); // Midnight background (#101124)

        LinearLayout customToolbar = buildToolbar();
        rootLayout.addView(customToolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
        ));

        ScrollView scroll = new ScrollView(this);
        rootLayout.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding, padding, padding);
        form.setClipToPadding(false);
        scroll.addView(form);

        // Card 1: Startup website (Accent Violet)
        int colorViolet = Color.rgb(124, 58, 237);
        LinearLayout websiteCard = card();
        websiteCard.addView(cardHeader(R.drawable.ic_globe, "Startup website", colorViolet));
        
        TextView urlLabel = new TextView(this);
        urlLabel.setText("Configure the default home and launch URL:");
        urlLabel.setTextSize(13);
        urlLabel.setTextColor(Color.rgb(142, 146, 178)); // secondary text
        urlLabel.setPadding(0, dp(4), 0, dp(8));
        websiteCard.addView(urlLabel);

        // input field layout container
        LinearLayout urlInputContainer = new LinearLayout(this);
        urlInputContainer.setOrientation(LinearLayout.HORIZONTAL);
        urlInputContainer.setGravity(Gravity.CENTER_VERTICAL);
        urlInputContainer.setPadding(dp(14), 0, dp(8), 0);
        urlInputContainer.setBackground(roundedBackground(Color.rgb(12, 13, 26), Color.rgb(39, 42, 78), 14));
        
        ImageView leftGlobe = new ImageView(this);
        leftGlobe.setImageResource(R.drawable.ic_globe);
        leftGlobe.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175)));
        urlInputContainer.addView(leftGlobe, new LinearLayout.LayoutParams(dp(22), dp(22)));

        startUrl = new EditText(this);
        startUrl.setSingleLine(true);
        startUrl.setHint("https://qms.example.com/");
        startUrl.setHintTextColor(Color.rgb(95, 100, 138));
        startUrl.setTextColor(Color.WHITE);
        startUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        startUrl.setTextSize(15);
        startUrl.setPadding(dp(10), dp(12), dp(10), dp(12));
        startUrl.setBackground(null); // transparent
        
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        urlInputContainer.addView(startUrl, editParams);

        ImageView clearBtn = new ImageView(this);
        clearBtn.setImageResource(R.drawable.ic_close);
        clearBtn.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175)));
        clearBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        clearBtn.setBackground(getToolbarButtonBackground());
        clearBtn.setClickable(true);
        clearBtn.setFocusable(true);
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> startUrl.setText(""));
        urlInputContainer.addView(clearBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));

        startUrl.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Focus effect
        startUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                urlInputContainer.setBackground(roundedBackground(Color.rgb(12, 13, 26), colorViolet, 14));
                leftGlobe.setImageTintList(ColorStateList.valueOf(colorViolet));
            } else {
                urlInputContainer.setBackground(roundedBackground(Color.rgb(12, 13, 26), Color.rgb(39, 42, 78), 14));
                leftGlobe.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175)));
            }
        });

        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        urlParams.setMargins(0, dp(4), 0, dp(4));
        websiteCard.addView(urlInputContainer, urlParams);
        form.addView(websiteCard, cardParams());

        // Card 2: Display settings (Accent Violet)
        LinearLayout browserCard = card();
        browserCard.addView(cardHeader(R.drawable.ic_desktop, "Display settings", colorViolet));
        
        fullscreen = new CustomSwitch(this, colorViolet);
        desktopMode = new CustomSwitch(this, colorViolet);
        keepScreenOn = new CustomSwitch(this, colorViolet);
        
        browserCard.addView(settingRow(R.drawable.ic_fullscreen, colorViolet, "Fullscreen mode", "Hides the status bar and navigation bar", fullscreen));
        browserCard.addView(divider());
        browserCard.addView(settingRow(R.drawable.ic_desktop, colorViolet, "Request desktop websites", "Loads desktop version of web pages", desktopMode));
        browserCard.addView(divider());
        browserCard.addView(settingRow(R.drawable.ic_sun, colorViolet, "Keep screen awake", "Prevents device screen from turning off", keepScreenOn));
        form.addView(browserCard, cardParams());

        // Card 3: Kiosk controls (Accent Violet)
        LinearLayout kioskCard = card();
        kioskCard.addView(cardHeader(R.drawable.ic_lock, "Kiosk controls", colorViolet));
        
        restrictToStartHost = new CustomSwitch(this, colorViolet);
        blockExternalApps = new CustomSwitch(this, colorViolet);
        preventScreenshots = new CustomSwitch(this, colorViolet);
        
        kioskCard.addView(settingRow(R.drawable.ic_home_lock, colorViolet, "Stay on startup website", "Restricts browsing to start domain only", restrictToStartHost));
        kioskCard.addView(divider());
        kioskCard.addView(settingRow(R.drawable.ic_block, colorViolet, "Block links to other apps", "Blocks links from opening external apps", blockExternalApps));
        kioskCard.addView(divider());
        kioskCard.addView(settingRow(R.drawable.ic_shield, colorViolet, "Protect screen content", "Prevents screenshots and screen recordings", preventScreenshots));
        kioskCard.addView(divider());
        
        autofillEnabled = new CustomSwitch(this, colorViolet);
        kioskCard.addView(settingRow(R.drawable.ic_key, colorViolet, "Autofill credentials", "Allows autofill of saved login credentials & form data", autofillEnabled));
        kioskCard.addView(divider());

        thirdPartyCookies = new CustomSwitch(this, colorViolet);
        kioskCard.addView(settingRow(R.drawable.ic_globe, colorViolet, "Allow third-party cookies", "Required for some single sign-on (SSO) login systems", thirdPartyCookies));
        kioskCard.addView(divider());

        // IDP Allowlist Input
        TextView idpLabel = new TextView(this);
        idpLabel.setText("Identity Provider Allowlist");
        idpLabel.setTextSize(14);
        idpLabel.setTextColor(Color.WHITE);
        idpLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        idpLabel.setPadding(0, dp(12), 0, dp(4));
        kioskCard.addView(idpLabel);

        TextView idpDesc = new TextView(this);
        idpDesc.setText("SSO login domains allowed to navigate (comma-separated)");
        idpDesc.setTextSize(12);
        idpDesc.setTextColor(Color.rgb(142, 146, 178));
        idpDesc.setPadding(0, 0, 0, dp(8));
        kioskCard.addView(idpDesc);

        LinearLayout idpInputContainer = new LinearLayout(this);
        idpInputContainer.setOrientation(LinearLayout.HORIZONTAL);
        idpInputContainer.setGravity(Gravity.CENTER_VERTICAL);
        idpInputContainer.setPadding(dp(14), 0, dp(8), 0);
        idpInputContainer.setBackground(roundedBackground(Color.rgb(12, 13, 26), Color.rgb(39, 42, 78), 14));

        idpAllowlist = new EditText(this);
        idpAllowlist.setSingleLine(true);
        idpAllowlist.setHint("login.microsoftonline.com, accounts.google.com");
        idpAllowlist.setHintTextColor(Color.rgb(95, 100, 138));
        idpAllowlist.setTextColor(Color.WHITE);
        idpAllowlist.setTextSize(14);
        idpAllowlist.setPadding(dp(0), dp(12), dp(0), dp(12));
        idpAllowlist.setBackground(null);
        
        idpInputContainer.addView(idpAllowlist, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        idpAllowlist.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                idpInputContainer.setBackground(roundedBackground(Color.rgb(12, 13, 26), colorViolet, 14));
            } else {
                idpInputContainer.setBackground(roundedBackground(Color.rgb(12, 13, 26), Color.rgb(39, 42, 78), 14));
            }
        });

        LinearLayout.LayoutParams idpParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        idpParams.setMargins(0, dp(4), 0, dp(4));
        kioskCard.addView(idpInputContainer, idpParams);

        form.addView(kioskCard, cardParams());

        // Card 4: Session Policies
        LinearLayout sessionCard = card();
        sessionCard.addView(cardHeader(R.drawable.ic_trash, "Session policy", colorViolet));
        
        LinearLayout policyRow = new LinearLayout(this);
        policyRow.setOrientation(LinearLayout.HORIZONTAL);
        policyRow.setGravity(Gravity.CENTER_VERTICAL);
        policyRow.setPadding(0, dp(12), 0, dp(12));
        policyRow.setClickable(true);
        policyRow.setBackground(getMenuRowBackground());
        policyRow.setOnClickListener(v -> showPolicyDialog());

        policyRow.addView(styledIcon(R.drawable.ic_settings, colorViolet));

        LinearLayout policyText = new LinearLayout(this);
        policyText.setOrientation(LinearLayout.VERTICAL);
        policyText.setPadding(dp(12), 0, 0, 0);

        TextView policyTitle = new TextView(this);
        policyTitle.setText("Session clearing policy");
        policyTitle.setTextSize(15);
        policyTitle.setTextColor(Color.rgb(229, 231, 235));
        policyText.addView(policyTitle);

        policyDesc = new TextView(this);
        policyDesc.setText("Never");
        policyDesc.setTextSize(12);
        policyDesc.setTextColor(Color.rgb(142, 146, 178));
        policyText.addView(policyDesc);

        policyRow.addView(policyText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        ImageView chevron1 = new ImageView(this);
        chevron1.setImageResource(R.drawable.ic_arrow_forward);
        chevron1.setImageTintList(ColorStateList.valueOf(Color.rgb(142, 146, 178)));
        policyRow.addView(chevron1, new LinearLayout.LayoutParams(dp(18), dp(18)));
        sessionCard.addView(policyRow);
        
        sessionCard.addView(divider());

        inactivityRow = new LinearLayout(this);
        inactivityRow.setOrientation(LinearLayout.HORIZONTAL);
        inactivityRow.setGravity(Gravity.CENTER_VERTICAL);
        inactivityRow.setPadding(0, dp(12), 0, dp(12));
        inactivityRow.setClickable(true);
        inactivityRow.setBackground(getMenuRowBackground());
        inactivityRow.setOnClickListener(v -> showInactivityDialog());

        inactivityRow.addView(styledIcon(R.drawable.ic_sun, colorViolet));

        LinearLayout inactivityText = new LinearLayout(this);
        inactivityText.setOrientation(LinearLayout.VERTICAL);
        inactivityText.setPadding(dp(12), 0, 0, 0);

        TextView inactivityTitle = new TextView(this);
        inactivityTitle.setText("Inactivity threshold");
        inactivityTitle.setTextSize(15);
        inactivityTitle.setTextColor(Color.rgb(229, 231, 235));
        inactivityText.addView(inactivityTitle);

        inactivityDesc = new TextView(this);
        inactivityDesc.setText("15 minutes");
        inactivityDesc.setTextSize(12);
        inactivityDesc.setTextColor(Color.rgb(142, 146, 178));
        inactivityText.addView(inactivityDesc);

        inactivityRow.addView(inactivityText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        ImageView chevron2 = new ImageView(this);
        chevron2.setImageResource(R.drawable.ic_arrow_forward);
        chevron2.setImageTintList(ColorStateList.valueOf(Color.rgb(142, 146, 178)));
        inactivityRow.addView(chevron2, new LinearLayout.LayoutParams(dp(18), dp(18)));
        sessionCard.addView(inactivityRow);

        form.addView(sessionCard, cardParams());

        // Card 5: Reset actions (Accent Violet)
        LinearLayout actionCard = card();
        actionCard.addView(cardHeader(R.drawable.ic_close, "Reset options", colorViolet));
        actionCard.addView(actionRow(R.drawable.ic_trash, colorViolet, "Clear browser data", "Clears cache, history, cookies, and storage", () -> confirmClearData(), Color.rgb(229, 231, 235)));
        form.addView(actionCard, cardParams());

        // Sticky Bottom Bar
        LinearLayout bottomStickyBar = new LinearLayout(this);
        bottomStickyBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomStickyBar.setPadding(dp(20), dp(12), dp(20), dp(12));
        bottomStickyBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomStickyBar.setBackgroundColor(Color.rgb(16, 17, 36)); // Solid match
        
        // Add a top border separator line
        View bottomDivider = new View(this);
        bottomDivider.setBackgroundColor(Color.argb(20, 255, 255, 255));
        
        rootLayout.addView(bottomDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));
        
        rootLayout.addView(bottomStickyBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button cancel = actionButton("Cancel", false);
        cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        cancelParams.setMargins(0, 0, dp(12), 0);
        bottomStickyBar.addView(cancel, cancelParams);
        
        Button save = actionButton("Save settings", true);
        save.setOnClickListener(v -> savePreferences());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        bottomStickyBar.addView(save, saveParams);

        // Adjust ScrollView and system insets
        scroll.setClipToPadding(false);
        rootLayout.setOnApplyWindowInsetsListener((view, insets) -> {
            customToolbar.setPadding(
                    dp(8),
                    insets.getSystemWindowInsetTop(),
                    dp(16),
                    0
            );
            ViewGroup.LayoutParams lp = customToolbar.getLayoutParams();
            lp.height = dp(56) + insets.getSystemWindowInsetTop();
            customToolbar.setLayoutParams(lp);

            // Give bottom padding to the sticky bar, raising it above the system navigation bar insets
            bottomStickyBar.setPadding(
                    dp(20),
                    dp(12),
                    dp(20),
                    dp(12) + insets.getSystemWindowInsetBottom()
            );

            form.setPadding(
                    padding,
                    padding,
                    padding,
                    padding
            );
            return insets;
        });
        
        setContentView(rootLayout);
        rootLayout.requestApplyInsets();
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(16), 0);
        bar.setBackgroundColor(Color.rgb(16, 17, 36));
        bar.setElevation(dp(2));

        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(R.drawable.ic_arrow_back);
        backBtn.setImageTintList(ColorStateList.valueOf(Color.rgb(156, 163, 175))); // Gray 400
        backBtn.setScaleType(ImageView.ScaleType.CENTER);
        backBtn.setBackground(getToolbarButtonBackground());
        backBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        backBtn.setOnClickListener(v -> finish());
        bar.addView(backBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(12), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return bar;
    }

    private View styledIcon(int iconResource, int accentColor) {
        FrameLayout container = new FrameLayout(this);
        
        // Background with 15% opacity of accentColor
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(38, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        bg.setCornerRadius(dp(10));
        container.setBackground(bg);
        
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setImageTintList(ColorStateList.valueOf(accentColor));
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(18), dp(18));
        params.gravity = Gravity.CENTER;
        container.addView(icon, params);
        
        container.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        return container;
    }

    private LinearLayout cardHeader(int iconResource, String label, int iconTint) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(14));

        View iconContainer = styledIcon(iconResource, iconTint);
        header.addView(iconContainer);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(16);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return header;
    }

    private LinearLayout settingRow(int iconResource, int badgeColor, String label, String description, CustomSwitch toggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setClickable(true);
        row.setBackground(getMenuRowBackground());
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked(), true));

        View iconContainer = styledIcon(iconResource, badgeColor);
        row.addView(iconContainer);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(15);
        title.setTextColor(Color.rgb(229, 231, 235)); // Gray 200
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textLayout.addView(title);

        if (description != null && !description.isEmpty()) {
            TextView desc = new TextView(this);
            desc.setText(description);
            desc.setTextSize(12);
            desc.setTextColor(Color.rgb(142, 146, 178));
            desc.setPadding(0, dp(2), 0, 0);
            textLayout.addView(desc);
        }

        row.addView(textLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        toggle.setClickable(false);
        toggle.setFocusable(false);
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private LinearLayout actionRow(int iconResource, int badgeColor, String label, String description, Runnable action, int textColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setClickable(true);
        row.setBackground(getMenuRowBackground());
        row.setOnClickListener(v -> action.run());

        View iconContainer = styledIcon(iconResource, badgeColor);
        row.addView(iconContainer);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setPadding(dp(12), 0, 0, 0);
        
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(15);
        title.setTextColor(textColor);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textLayout.addView(title);

        if (description != null && !description.isEmpty()) {
            TextView desc = new TextView(this);
            desc.setText(description);
            desc.setTextSize(12);
            desc.setTextColor(Color.rgb(142, 146, 178));
            desc.setPadding(0, dp(2), 0, 0);
            textLayout.addView(desc);
        }

        row.addView(textLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_arrow_forward);
        chevron.setImageTintList(ColorStateList.valueOf(Color.rgb(142, 146, 178)));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));

        return row;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(15, 255, 255, 255)); // Faint divider
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        divider.setLayoutParams(params);
        return divider;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundedBackground(Color.rgb(21, 23, 44), Color.rgb(39, 42, 78), 24));
        card.setElevation(dp(2));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(16));
        return params;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        int fill, stroke, ripple, textColor;
        if (primary) {
            fill = Color.rgb(124, 58, 237);    // brand violet
            stroke = Color.rgb(124, 58, 237);
            ripple = Color.argb(40, 255, 255, 255);
            textColor = Color.WHITE;
        } else {
            fill = Color.rgb(16, 17, 36);      // midnight blue
            stroke = Color.rgb(45, 48, 86);    // card border
            ripple = Color.argb(20, 255, 255, 255);
            textColor = Color.rgb(229, 231, 235); // gray 200
        }
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        
        Drawable bgDrawable;
        if (primary) {
            // Gradient background for primary button
            GradientDrawable grad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(124, 58, 237), Color.rgb(167, 139, 250) }
            );
            grad.setCornerRadius(dp(14));
            bgDrawable = grad;
        } else {
            bgDrawable = roundedBackground(fill, stroke, 14);
        }

        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(ripple),
                bgDrawable,
                null
        ));
        return button;
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), stroke);
        return background;
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

    private void loadPreferences() {
        SharedPreferences preferences = BrowserPreferences.get(this);
        startUrl.setText(preferences.getString(
                BrowserPreferences.START_URL,
                BrowserPreferences.DEFAULT_START_URL
        ));
        fullscreen.setChecked(preferences.getBoolean(BrowserPreferences.FULLSCREEN, false));
        desktopMode.setChecked(preferences.getBoolean(BrowserPreferences.DESKTOP_MODE, false));
        keepScreenOn.setChecked(preferences.getBoolean(BrowserPreferences.KEEP_SCREEN_ON, false));
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
        autofillEnabled.setChecked(preferences.getBoolean(
                BrowserPreferences.AUTOFILL_ENABLED,
                true
        ));
        thirdPartyCookies.setChecked(preferences.getBoolean(
                BrowserPreferences.THIRD_PARTY_COOKIES_ENABLED,
                false
        ));
        idpAllowlist.setText(preferences.getString(
                BrowserPreferences.IDP_ALLOWLIST,
                ""
        ));
        selectedPolicy = preferences.getString(BrowserPreferences.SESSION_CLEAR_POLICY, "never");
        selectedInactivityMins = preferences.getInt(BrowserPreferences.SESSION_INACTIVITY_DURATION_MINS, 15);
        updatePolicyViews();
    }

    private void savePreferences() {
        String normalizedUrl = MainActivity.normalizeAddress(startUrl.getText().toString());
        if (!SecurityPolicy.isAllowedWebUrl(normalizedUrl)) {
            startUrl.setError("Enter an HTTPS website");
            return;
        }

        BrowserPreferences.get(this).edit()
                .putString(BrowserPreferences.START_URL, normalizedUrl)
                .putBoolean(BrowserPreferences.FULLSCREEN, fullscreen.isChecked())
                .putBoolean(BrowserPreferences.DESKTOP_MODE, desktopMode.isChecked())
                .putBoolean(BrowserPreferences.KEEP_SCREEN_ON, keepScreenOn.isChecked())
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
                .putBoolean(
                        BrowserPreferences.AUTOFILL_ENABLED,
                        autofillEnabled.isChecked()
                )
                .putBoolean(
                        BrowserPreferences.THIRD_PARTY_COOKIES_ENABLED,
                        thirdPartyCookies.isChecked()
                )
                .putString(
                        BrowserPreferences.IDP_ALLOWLIST,
                        idpAllowlist.getText().toString().trim()
                )
                .putString(
                        BrowserPreferences.SESSION_CLEAR_POLICY,
                        selectedPolicy
                )
                .putInt(
                        BrowserPreferences.SESSION_INACTIVITY_DURATION_MINS,
                        selectedInactivityMins
                )
                .apply();
                
        BrowserSessionManager.getInstance().applySessionPolicy(this);

        setResult(RESULT_OK);
        finish();
    }

    private void confirmClearData() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(24), dp(24), dp(24));
        container.setBackground(roundedBackground(Color.rgb(21, 23, 44), Color.rgb(45, 48, 86), 20));
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_trash);
        icon.setImageTintList(ColorStateList.valueOf(Color.rgb(124, 58, 237)));
        
        FrameLayout iconBadge = new FrameLayout(this);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.argb(30, 124, 58, 237));
        badgeBg.setCornerRadius(dp(12));
        iconBadge.setBackground(badgeBg);
        
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(24), dp(24));
        iconParams.gravity = Gravity.CENTER;
        iconBadge.addView(icon, iconParams);

        container.addView(iconBadge, new LinearLayout.LayoutParams(dp(48), dp(48)));

        View space1 = new View(this);
        container.addView(space1, new LinearLayout.LayoutParams(1, dp(16)));

        TextView title = new TextView(this);
        title.setText("Clear browser data?");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        container.addView(title);

        View space2 = new View(this);
        container.addView(space2, new LinearLayout.LayoutParams(1, dp(8)));

        TextView msg = new TextView(this);
        msg.setText("This signs you out of websites and clears all cache, history, cookies, and local storage.");
        msg.setTextColor(Color.rgb(142, 146, 178));
        msg.setTextSize(14);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(0f, 1.25f);
        container.addView(msg);

        View space3 = new View(this);
        container.addView(space3, new LinearLayout.LayoutParams(1, dp(24)));

        LinearLayout dialogButtons = new LinearLayout(this);
        dialogButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCancel = actionButton("Cancel", false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        cancelLp.setMargins(0, 0, dp(12), 0);
        dialogButtons.addView(btnCancel, cancelLp);

        Button btnClear = new Button(this);
        btnClear.setText("Clear data");
        btnClear.setAllCaps(false);
        btnClear.setTextSize(14);
        btnClear.setTextColor(Color.WHITE);
        btnClear.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        
        GradientDrawable clearBg = new GradientDrawable();
        clearBg.setColor(Color.rgb(124, 58, 237));
        clearBg.setCornerRadius(dp(12));
        btnClear.setBackground(new RippleDrawable(
            ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
            clearBg,
            null
        ));
        btnClear.setOnClickListener(v -> {
            BrowserSessionManager.getInstance().clearSession(this, null, () -> {
                Toast.makeText(SettingsActivity.this, "Browser data cleared", Toast.LENGTH_SHORT).show();
            });
            dialog.dismiss();
        });
        dialogButtons.addView(btnClear, new LinearLayout.LayoutParams(0, dp(44), 1));

        container.addView(dialogButtons, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        dialog.setContentView(container);

        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.show();
        dialog.getWindow().setAttributes(lp);
    }

    private void showPolicyDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(24), dp(24), dp(24));
        container.setBackground(roundedBackground(Color.rgb(21, 23, 44), Color.rgb(45, 48, 86), 20));

        // Dialog Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(16));

        int colorViolet = Color.rgb(124, 58, 237);
        View iconContainer = styledIcon(R.drawable.ic_trash, colorViolet);
        header.addView(iconContainer);

        TextView title = new TextView(this);
        title.setText("Session clearing policy");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);

        container.addView(header);

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("Select when the browser should automatically clear all session data, cookies, and local storage.");
        subtitle.setTextColor(Color.rgb(142, 146, 178));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 0, 0, dp(16));
        container.addView(subtitle);

        // Options
        String[] options = {"Never", "On app start", "After inactivity", "Daily"};
        final String[] keys = {"never", "app_start", "inactivity", "daily"};

        for (int i = 0; i < options.length; i++) {
            final String key = keys[i];
            final String label = options[i];
            final boolean isSelected = key.equals(selectedPolicy);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setClickable(true);
            row.setFocusable(true);

            if (isSelected) {
                row.setBackground(roundedBackground(Color.rgb(33, 31, 62), Color.rgb(124, 58, 237), 12));
            } else {
                row.setBackground(roundedBackground(Color.rgb(16, 17, 36), Color.rgb(39, 42, 78), 12));
            }

            TextView labelView = new TextView(this);
            labelView.setText(label);
            labelView.setTextSize(15);
            labelView.setTextColor(isSelected ? Color.WHITE : Color.rgb(197, 200, 222));
            labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            if (isSelected) {
                ImageView checkIcon = new ImageView(this);
                checkIcon.setImageResource(R.drawable.ic_check);
                checkIcon.setImageTintList(ColorStateList.valueOf(colorViolet));
                row.addView(checkIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
            }

            row.setOnClickListener(v -> {
                selectedPolicy = key;
                updatePolicyViews();
                dialog.dismiss();
            });

            container.addView(row);

            if (i < options.length - 1) {
                View spacer = new View(this);
                container.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
            }
        }

        View spacer = new View(this);
        container.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        Button btnCancel = actionButton("Cancel", false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        container.addView(btnCancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        dialog.setContentView(container);

        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        if (dialog.getWindow() != null) {
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.show();
            dialog.getWindow().setAttributes(lp);
        }
    }

    private void showInactivityDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(24), dp(24), dp(24));
        container.setBackground(roundedBackground(Color.rgb(21, 23, 44), Color.rgb(45, 48, 86), 20));

        // Dialog Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(16));

        int colorViolet = Color.rgb(124, 58, 237);
        View iconContainer = styledIcon(R.drawable.ic_sun, colorViolet);
        header.addView(iconContainer);

        TextView title = new TextView(this);
        title.setText("Inactivity threshold");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);

        container.addView(header);

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("Select how long the browser should wait without user interaction before clearing the session.");
        subtitle.setTextColor(Color.rgb(142, 146, 178));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 0, 0, dp(16));
        container.addView(subtitle);

        // Options
        String[] options = {"15 minutes", "30 minutes", "1 hour", "2 hours"};
        final int[] values = {15, 30, 60, 120};

        for (int i = 0; i < options.length; i++) {
            final int val = values[i];
            final String label = options[i];
            final boolean isSelected = (val == selectedInactivityMins);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setClickable(true);
            row.setFocusable(true);

            if (isSelected) {
                row.setBackground(roundedBackground(Color.rgb(33, 31, 62), Color.rgb(124, 58, 237), 12));
            } else {
                row.setBackground(roundedBackground(Color.rgb(16, 17, 36), Color.rgb(39, 42, 78), 12));
            }

            TextView labelView = new TextView(this);
            labelView.setText(label);
            labelView.setTextSize(15);
            labelView.setTextColor(isSelected ? Color.WHITE : Color.rgb(197, 200, 222));
            labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            if (isSelected) {
                ImageView checkIcon = new ImageView(this);
                checkIcon.setImageResource(R.drawable.ic_check);
                checkIcon.setImageTintList(ColorStateList.valueOf(colorViolet));
                row.addView(checkIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
            }

            row.setOnClickListener(v -> {
                selectedInactivityMins = val;
                updatePolicyViews();
                dialog.dismiss();
            });

            container.addView(row);

            if (i < options.length - 1) {
                View spacer = new View(this);
                container.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
            }
        }

        View spacer = new View(this);
        container.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        Button btnCancel = actionButton("Cancel", false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        container.addView(btnCancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        dialog.setContentView(container);

        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        if (dialog.getWindow() != null) {
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.show();
            dialog.getWindow().setAttributes(lp);
        }
    }

    private void updatePolicyViews() {
        if (policyDesc == null || inactivityRow == null || inactivityDesc == null) {
            return;
        }
        String policyText = "Never";
        if ("app_start".equals(selectedPolicy)) {
            policyText = "On app start";
        } else if ("inactivity".equals(selectedPolicy)) {
            policyText = "After inactivity";
        } else if ("daily".equals(selectedPolicy)) {
            policyText = "Daily";
        }
        policyDesc.setText(policyText);
        
        inactivityRow.setVisibility("inactivity".equals(selectedPolicy) ? View.VISIBLE : View.GONE);
        
        String timeText = selectedInactivityMins + " minutes";
        if (selectedInactivityMins >= 60) {
            timeText = (selectedInactivityMins / 60) + " hour" + (selectedInactivityMins > 60 ? "s" : "");
        }
        inactivityDesc.setText(timeText);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class CustomSwitch extends FrameLayout {
        private boolean checked;
        private final View track;
        private final View thumb;
        private final int accentColor;
        private final int density;

        public CustomSwitch(Context context, int accentColor) {
            super(context);
            this.accentColor = accentColor;
            this.density = Math.round(getResources().getDisplayMetrics().density);

            // Container size: 46dp x 26dp
            setLayoutParams(new ViewGroup.LayoutParams(dp(46), dp(26)));

            // Track View
            track = new View(context);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setColor(Color.rgb(39, 41, 61)); // #27293D
            trackBg.setCornerRadius(dp(13));
            trackBg.setStroke(dp(1), Color.rgb(63, 66, 97)); // #3F4261
            track.setBackground(trackBg);
            addView(track, new FrameLayout.LayoutParams(dp(46), dp(26)));

            // Thumb View
            thumb = new View(context);
            GradientDrawable thumbBg = new GradientDrawable();
            thumbBg.setColor(Color.rgb(156, 163, 175)); // #9CA3AF
            thumbBg.setCornerRadius(dp(10));
            thumb.setBackground(thumbBg);

            FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(dp(20), dp(20));
            thumbParams.gravity = Gravity.CENTER_VERTICAL;
            thumbParams.leftMargin = dp(3);
            addView(thumb, thumbParams);

            setOnClickListener(v -> setChecked(!checked, true));
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            setChecked(checked, false);
        }

        public void setChecked(boolean checked, boolean animate) {
            this.checked = checked;
            
            int targetTrackColor = checked ? accentColor : Color.rgb(39, 41, 61);
            int targetStrokeColor = checked ? accentColor : Color.rgb(63, 66, 97);
            int targetThumbColor = checked ? Color.WHITE : Color.rgb(156, 163, 175);
            float targetTranslationX = checked ? dp(20) : 0;

            GradientDrawable trackBg = (GradientDrawable) track.getBackground();
            GradientDrawable thumbBg = (GradientDrawable) thumb.getBackground();

            if (animate) {
                // Animate thumb translation
                thumb.animate()
                        .translationX(targetTranslationX)
                        .setDuration(180)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();

                // Animate track color using ValueAnimator
                android.animation.ValueAnimator colorAnimator = android.animation.ValueAnimator.ofArgb(
                        checked ? Color.rgb(39, 41, 61) : accentColor,
                        checked ? accentColor : Color.rgb(39, 41, 61)
                );
                colorAnimator.setDuration(180);
                colorAnimator.addUpdateListener(animator -> {
                    int val = (int) animator.getAnimatedValue();
                    trackBg.setColor(val);
                });
                colorAnimator.start();

                // Animate stroke color
                android.animation.ValueAnimator strokeAnimator = android.animation.ValueAnimator.ofArgb(
                        checked ? Color.rgb(63, 66, 97) : accentColor,
                        checked ? accentColor : Color.rgb(63, 66, 97)
                );
                strokeAnimator.setDuration(180);
                strokeAnimator.addUpdateListener(animator -> {
                    int val = (int) animator.getAnimatedValue();
                    trackBg.setStroke(dp(1), val);
                });
                strokeAnimator.start();

                thumbBg.setColor(targetThumbColor);
            } else {
                thumb.setTranslationX(targetTranslationX);
                trackBg.setColor(targetTrackColor);
                trackBg.setStroke(dp(1), targetStrokeColor);
                thumbBg.setColor(targetThumbColor);
            }
        }

        private int dp(int value) {
            return Math.round(value * density);
        }
    }
}
