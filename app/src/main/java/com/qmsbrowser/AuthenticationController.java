package com.qmsbrowser;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class AuthenticationController {
    private static final String TAG = "AuthenticationController";

    private final Context context;
    private final CredentialStore credentialStore;
    private String lastAttemptedKey;

    public AuthenticationController(Context context) {
        this.context = context.getApplicationContext();
        credentialStore = new CredentialStore(this.context);
    }

    public void handleHttpAuthRequest(final WebView view, final HttpAuthHandler handler, final String host, final String realm) {
        final String pageUrl = view.getUrl();
        
        // 1. Restrict Basic Auth to HTTPS
        if (pageUrl == null || !pageUrl.startsWith("https://")) {
            Log.w(TAG, "Blocked Basic Auth request: Not HTTPS");
            handler.cancel();
            Toast.makeText(context, "Authentication is restricted to secure HTTPS sites only", Toast.LENGTH_LONG).show();
            return;
        }

        final String authKey = host + ":" + realm;
        
        // 2. Check if we've already tried this key and failed
        if (authKey.equals(lastAttemptedKey)) {
            Log.d(TAG, "Last authentication attempt failed. Clearing stored credentials and showing prompt.");
            clearCredentials(authKey);
            lastAttemptedKey = null;
        }

        // 3. Attempt automatic login if credentials are saved
        String savedUser = credentialStore.username(authKey);
        String savedPass = credentialStore.password(authKey);
        
        if (savedUser != null && savedPass != null && lastAttemptedKey == null) {
            Log.d(TAG, "Using saved credentials for " + authKey);
            lastAttemptedKey = authKey;
            handler.proceed(savedUser, savedPass);
            return;
        }

        // 4. Show custom styled credentials dialog
        showAuthDialog(view.getContext(), handler, authKey, host, realm);
    }

    private void showAuthDialog(final Context dialogContext, final HttpAuthHandler handler, final String authKey, final String host, final String realm) {
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext);
        
        // Root container matching premium dark theme
        LinearLayout container = new LinearLayout(dialogContext);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(dialogContext, 24);
        container.setPadding(padding, padding, padding, padding);
        container.setBackgroundColor(Color.rgb(21, 23, 44)); // midnight blue (#15172C)

        // Title
        TextView title = new TextView(dialogContext);
        title.setText("Authentication Required");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, 0, 0, dp(dialogContext, 8));
        container.addView(title);

        // Subtitle
        TextView subtitle = new TextView(dialogContext);
        subtitle.setText("The site at https://" + host + " (Realm: " + realm + ") requires a username and password.");
        subtitle.setTextColor(Color.rgb(142, 146, 178));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 0, 0, dp(dialogContext, 20));
        container.addView(subtitle);

        // Username Input
        final EditText usernameInput = new EditText(dialogContext);
        usernameInput.setHint("Username");
        usernameInput.setHintTextColor(Color.rgb(95, 100, 138));
        usernameInput.setTextColor(Color.WHITE);
        usernameInput.setSingleLine(true);
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        styleEditText(dialogContext, usernameInput);
        container.addView(usernameInput, editParams(dialogContext));

        // Password Input
        final EditText passwordInput = new EditText(dialogContext);
        passwordInput.setHint("Password");
        passwordInput.setHintTextColor(Color.rgb(95, 100, 138));
        passwordInput.setTextColor(Color.WHITE);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleEditText(dialogContext, passwordInput);
        container.addView(passwordInput, editParams(dialogContext));

        // Remember Credentials Checkbox
        final CheckBox rememberCheck = new CheckBox(dialogContext);
        rememberCheck.setText("Remember credentials");
        rememberCheck.setTextColor(Color.rgb(229, 231, 235));
        rememberCheck.setButtonTintList(ColorStateList.valueOf(Color.rgb(124, 58, 237)));
        rememberCheck.setTextSize(14);
        rememberCheck.setPadding(dp(dialogContext, 4), 0, 0, 0);
        rememberCheck.setEnabled(credentialStore.isAvailable());
        if (!credentialStore.isAvailable()) {
            rememberCheck.setText("Secure credential storage unavailable");
        }
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        checkParams.setMargins(0, dp(dialogContext, 8), 0, dp(dialogContext, 24));
        container.addView(rememberCheck, checkParams);

        // Buttons Row
        LinearLayout buttonRow = new LinearLayout(dialogContext);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCancel = actionButton(dialogContext, "Cancel", false);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(dialogContext, 44), 1);
        cancelParams.rightMargin = dp(dialogContext, 12);
        buttonRow.addView(btnCancel, cancelParams);

        Button btnLogin = actionButton(dialogContext, "Sign In", true);
        buttonRow.addView(btnLogin, new LinearLayout.LayoutParams(0, dp(dialogContext, 44), 1));

        container.addView(buttonRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        builder.setView(container);
        builder.setCancelable(false);
        final AlertDialog dialog = builder.create();
        
        btnCancel.setOnClickListener(v -> {
            handler.cancel();
            lastAttemptedKey = null;
            dialog.dismiss();
        });

        btnLogin.setOnClickListener(v -> {
            String user = usernameInput.getText().toString().trim();
            String pass = passwordInput.getText().toString();
            
            if (user.isEmpty()) {
                usernameInput.setError("Username is required");
                return;
            }
            
            if (rememberCheck.isChecked()) {
                if (!credentialStore.save(authKey, user, pass)) {
                    Toast.makeText(context, "Credentials could not be saved securely", Toast.LENGTH_LONG).show();
                }
            } else {
                clearCredentials(authKey);
            }
            
            lastAttemptedKey = authKey;
            handler.proceed(user, pass);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void clearCredentials(String authKey) {
        credentialStore.clear(authKey);
    }

    private int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private void styleEditText(Context ctx, EditText editText) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(12, 13, 26)); // input field dark bg (#0C0D1A)
        bg.setCornerRadius(dp(ctx, 12));
        bg.setStroke(dp(ctx, 1), Color.rgb(39, 42, 78)); // border (#272A4E)
        editText.setBackground(bg);
        editText.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        editText.setTextSize(14);
        
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                bg.setStroke(dp(ctx, 1), Color.rgb(124, 58, 237)); // violet accent
            } else {
                bg.setStroke(dp(ctx, 1), Color.rgb(39, 42, 78));
            }
        });
    }

    private LinearLayout.LayoutParams editParams(Context ctx) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(ctx, 12);
        return params;
    }

    private Button actionButton(Context ctx, String label, boolean primary) {
        Button button = new Button(ctx);
        int fill, stroke, ripple, textColor;
        if (primary) {
            fill = Color.rgb(124, 58, 237);
            stroke = Color.rgb(124, 58, 237);
            ripple = Color.argb(40, 255, 255, 255);
            textColor = Color.WHITE;
        } else {
            fill = Color.rgb(21, 23, 44);
            stroke = Color.rgb(45, 48, 86);
            ripple = Color.argb(20, 255, 255, 255);
            textColor = Color.rgb(229, 231, 235);
        }
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(ctx, 12));
        bg.setStroke(dp(ctx, 1), stroke);

        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(ripple),
                bg,
                null
        ));
        return button;
    }
}
