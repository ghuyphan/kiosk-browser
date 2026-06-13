package com.qmsbrowser;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class RemoteControllerActivity extends Activity {
    public static final String EXTRA_CONTROLLER_URL = "controller_url";
    private static final long MOVEMENT_INTERVAL_MS = 800;
    private static final int BACKGROUND = Color.rgb(15, 23, 31);
    private static final int SURFACE = Color.rgb(37, 75, 105);
    private static final int SURFACE_RAISED = Color.rgb(29, 42, 54);
    private static final int BORDER = Color.rgb(57, 74, 88);
    private static final int MUTED = Color.rgb(145, 160, 173);
    private static final int ICON = Color.rgb(203, 213, 225);
    private static final int ACCENT = Color.rgb(65, 139, 184);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private RemoteCommandSender sender;
    private TextView connectionStatus;
    private FrameLayout modeContent;
    private View mousePanel;
    private View keyboardPanel;
    private ImageButton mouseModeButton;
    private ImageButton keyboardModeButton;
    private float accumulatedX;
    private float accumulatedY;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private long downAt;
    private boolean scrolling;
    private boolean movementScheduled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        String controllerUrl = getIntent().getStringExtra(EXTRA_CONTROLLER_URL);
        String[] credentials = parseCredentials(controllerUrl);
        if (credentials == null) {
            Toast.makeText(this, "Invalid remote controller link", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        sender = new RemoteCommandSender(credentials[0], credentials[1]);
        buildUi();
        send("hello", "{\"name\":\"Android controller\"}", false);
    }

    @Override
    protected void onDestroy() {
        if (sender != null) {
            send("disconnect", "", false);
        }
        super.onDestroy();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom()
            );
            return insets;
        });

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60)
        ));

        modeContent = new FrameLayout(this);
        mousePanel = buildMousePanel();
        keyboardPanel = buildKeyboardPanel();
        modeContent.addView(mousePanel, matchFrame());
        modeContent.addView(keyboardPanel, matchFrame());
        root.addView(modeContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        root.addView(buildModeBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60)
        ));

        setContentView(root);
        root.requestApplyInsets();
        showMouseMode();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(2), dp(8), dp(2));

        ImageButton close = plainIconButton(R.drawable.ic_arrow_back, "Close remote control");
        close.setOnClickListener(ignored -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Remote control", 18, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labels.addView(title);
        connectionStatus = text("Connecting", 13, MUTED);
        labels.addView(connectionStatus);
        header.addView(labels, new LinearLayout.LayoutParams(0, dp(52), 1));

        ImageButton more = plainIconButton(R.drawable.ic_more_vert, "More controls");
        more.setOnClickListener(this::showControlMenu);
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private View buildMousePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(6), dp(12), dp(10));

        FrameLayout trackpad = new FrameLayout(this);
        trackpad.setBackground(roundedBackground(SURFACE, SURFACE, 18));
        trackpad.setOnTouchListener(this::handleTrackpadTouch);

        ImageView mouseGlyph = new ImageView(this);
        mouseGlyph.setImageResource(R.drawable.ic_mouse);
        mouseGlyph.setImageTintList(ColorStateList.valueOf(Color.argb(110, 255, 255, 255)));
        FrameLayout.LayoutParams glyphParams = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER);
        trackpad.addView(mouseGlyph, glyphParams);

        TextView scrollRail = text("⌃\n│\n│\n⌄", 18, Color.argb(100, 255, 255, 255));
        scrollRail.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(dp(34), dp(154), Gravity.END | Gravity.CENTER_VERTICAL);
        railParams.rightMargin = dp(10);
        trackpad.addView(scrollRail, railParams);

        panel.addView(trackpad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout mouseButtons = new LinearLayout(this);
        mouseButtons.setPadding(0, dp(8), 0, 0);
        View leftClick = mouseButton("Left", "Left click", () -> send("tap", "", true));
        View rightClick = mouseButton("Right", "Right click", () -> send("long_press", "", true));
        LinearLayout.LayoutParams clickParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        clickParams.rightMargin = dp(4);
        mouseButtons.addView(leftClick, clickParams);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        rightParams.leftMargin = dp(4);
        mouseButtons.addView(rightClick, rightParams);
        panel.addView(mouseButtons);
        return panel;
    }

    private View buildKeyboardPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(4), dp(12), dp(10));

        EditText keyboardInput = input(
                "Type here",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        keyboardInput.setGravity(Gravity.TOP);
        keyboardInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        panel.addView(keyboardInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        TextView sendButton = actionButton("Send text", () -> sendKeyboardText(keyboardInput));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        sendParams.topMargin = dp(8);
        panel.addView(sendButton, sendParams);

        LinearLayout keys = new LinearLayout(this);
        keys.setPadding(0, dp(8), 0, 0);
        keys.addView(actionButton("⌫", () -> send("key", "backspace", true)), equalButtonParams());
        keys.addView(actionButton("Tab", () -> send("key", "tab", true)), equalButtonParams());
        keys.addView(actionButton("Esc", () -> send("key", "escape", true)), equalButtonParams());
        keys.addView(actionButton("Enter", () -> send("key", "enter", true)), equalButtonParams());
        panel.addView(keys);

        keyboardInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendKeyboardText(keyboardInput);
                return true;
            }
            return false;
        });
        return panel;
    }

    private View buildModeBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(12), dp(4), dp(12), dp(4));

        mouseModeButton = modeButton(R.drawable.ic_mouse, "Mouse mode", view -> showMouseMode());
        keyboardModeButton = modeButton(R.drawable.ic_keyboard, "Keyboard mode", view -> showKeyboardMode());
        bar.addView(mouseModeButton, equalModeParams());
        bar.addView(keyboardModeButton, equalModeParams());
        return bar;
    }

    private void showMouseMode() {
        mousePanel.setVisibility(View.VISIBLE);
        keyboardPanel.setVisibility(View.GONE);
        setModeSelected(mouseModeButton, true);
        setModeSelected(keyboardModeButton, false);
        hideKeyboard();
    }

    private void showKeyboardMode() {
        mousePanel.setVisibility(View.GONE);
        keyboardPanel.setVisibility(View.VISIBLE);
        setModeSelected(mouseModeButton, false);
        setModeSelected(keyboardModeButton, true);
    }

    private void showControlMenu(View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackground(roundedBackground(SURFACE_RAISED, BORDER, 18));
        menu.setElevation(dp(12));

        PopupWindow popup = new PopupWindow(
                menu,
                dp(220),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(12));
        popup.setOutsideTouchable(true);

        addMenuRow(menu, R.drawable.ic_arrow_back, "Back", () -> send("back", "", true), popup);
        addMenuRow(menu, R.drawable.ic_arrow_forward, "Forward", () -> send("forward", "", true), popup);
        addMenuRow(menu, R.drawable.ic_refresh, "Reload", () -> send("reload", "", true), popup);
        addMenuRow(menu, R.drawable.ic_home, "Home", () -> send("home", "", true), popup);
        addMenuRow(menu, R.drawable.ic_globe, "Open website", this::showUrlDialog, popup);

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        popup.showAtLocation(
                getWindow().getDecorView(),
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
        
        int rippleColor = Color.argb(25, 255, 255, 255);
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        RippleDrawable rowBg = new RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            null,
            null
        );
        row.setBackground(rowBg);
        
        row.setOnClickListener(view -> {
            popup.dismiss();
            action.run();
        });

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setImageTintList(ColorStateList.valueOf(MUTED));
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

    private void showUrlDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(18));
        card.setBackground(roundedBackground(SURFACE_RAISED, BORDER, 22));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView globe = new ImageView(this);
        globe.setImageResource(R.drawable.ic_globe);
        globe.setImageTintList(ColorStateList.valueOf(Color.rgb(196, 181, 253)));
        heading.addView(globe, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView title = text("Open website", 20, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        titleParams.leftMargin = dp(12);
        heading.addView(title, titleParams);
        ImageButton close = plainIconButton(R.drawable.ic_close, "Close");
        close.setOnClickListener(view -> dialog.dismiss());
        heading.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        card.addView(heading);

        TextView hint = text("Enter the HTTPS address to open on the kiosk.", 13, MUTED);
        hint.setPadding(0, dp(8), 0, dp(14));
        card.addView(hint);

        EditText urlInput = input(
                "example.com",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
        );
        urlInput.setSingleLine(true);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        card.addView(urlInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        ));

        TextView open = actionButton("Open on kiosk", () -> {
            if (sendUrl(urlInput)) {
                dialog.dismiss();
            }
        });
        open.setBackground(roundedBackground(ACCENT, ACCENT, 14));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        openParams.topMargin = dp(12);
        card.addView(open, openParams);

        urlInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO && sendUrl(urlInput)) {
                dialog.dismiss();
                return true;
            }
            return false;
        });

        dialog.setContentView(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.gravity = Gravity.CENTER;
            attributes.dimAmount = 0.72f;
            window.setAttributes(attributes);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(
                    getResources().getDisplayMetrics().widthPixels - dp(32),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private boolean handleTrackpadTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                downAt = System.currentTimeMillis();
                accumulatedX = 0;
                accumulatedY = 0;
                scrolling = false;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                scrolling = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                accumulatedX += x - lastX;
                accumulatedY += y - lastY;
                lastX = x;
                lastY = y;
                scheduleMovement();
                return true;
            case MotionEvent.ACTION_UP:
                float distance = Math.abs(event.getX() - downX) + Math.abs(event.getY() - downY);
                if (distance < dp(10)) {
                    send(System.currentTimeMillis() - downAt > 550 ? "long_press" : "tap", "", true);
                } else {
                    flushMovement();
                }
                scrolling = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                scrolling = false;
                return true;
            default:
                return true;
        }
    }

    private void scheduleMovement() {
        if (movementScheduled || sender.isRateLimited()) {
            return;
        }
        movementScheduled = true;
        handler.postDelayed(() -> {
            movementScheduled = false;
            flushMovement();
        }, MOVEMENT_INTERVAL_MS);
    }

    private void flushMovement() {
        if (Math.abs(accumulatedX) < 1 && Math.abs(accumulatedY) < 1) {
            return;
        }
        try {
            JSONObject value = new JSONObject();
            value.put("dx", Math.round(scrolling ? -accumulatedX * 2 : accumulatedX));
            value.put("dy", Math.round(scrolling ? -accumulatedY * 2 : accumulatedY));
            if (!scrolling) {
                value.put("sensitivity", 1.25);
            }
            accumulatedX = 0;
            accumulatedY = 0;
            send(scrolling ? "scroll" : "pointer_move", value.toString(), false);
        } catch (Exception ignored) {
        }
    }

    private void sendKeyboardText(EditText input) {
        String value = input.getText().toString();
        if (!value.isEmpty()) {
            send("type", value, true);
            input.setText("");
        }
    }

    private boolean sendUrl(EditText input) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            return false;
        }
        if (!value.toLowerCase(java.util.Locale.US).startsWith("https://")) {
            value = "https://" + value.replaceFirst("^[a-zA-Z]+://", "");
        }
        send("url", value, true);
        input.clearFocus();
        return true;
    }

    private void send(String action, String value, boolean showResult) {
        sender.send(action, value, (success, responseCode) -> {
            if (responseCode == 429) {
                connectionStatus.setText("Cooling down");
                connectionStatus.setTextColor(Color.rgb(251, 113, 133));
                if (showResult) {
                    Toast.makeText(this, "Remote service rate limited. Try again shortly.", Toast.LENGTH_SHORT).show();
                }
            } else if (success) {
                connectionStatus.setText("Kiosk connected");
                connectionStatus.setTextColor(Color.rgb(52, 211, 153));
            } else {
                connectionStatus.setText("Kiosk offline");
                connectionStatus.setTextColor(Color.rgb(251, 113, 133));
                if (showResult) {
                    Toast.makeText(this, "Command could not be sent", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String[] parseCredentials(String controllerUrl) {
        if (controllerUrl == null) {
            return null;
        }
        String fragment = Uri.parse(controllerUrl).getFragment();
        if (fragment == null) {
            return null;
        }
        UrlQuerySanitizer sanitizer = new UrlQuerySanitizer();
        sanitizer.setAllowUnregisteredParamaters(true);
        sanitizer.parseQuery(fragment);
        String topic = sanitizer.getValue("topic");
        String secret = sanitizer.getValue("secret");
        return topic == null || secret == null ? null : new String[]{topic, secret};
    }

    private EditText input(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(Color.WHITE);
        input.setTextSize(15);
        input.setInputType(inputType);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setBackground(roundedBackground(SURFACE, BORDER, 14));
        return input;
    }

    private ImageButton plainIconButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(ICON));
        button.setContentDescription(description);
        button.setPadding(dp(13), dp(13), dp(13), dp(13));
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private ImageButton modeButton(int icon, String description, View.OnClickListener action) {
        ImageButton button = plainIconButton(icon, description);
        button.setOnClickListener(action);
        return button;
    }

    private void setModeSelected(ImageButton button, boolean selected) {
        button.setBackground(selected
                ? roundedBackground(SURFACE_RAISED, SURFACE_RAISED, 12)
                : roundedBackground(Color.TRANSPARENT, Color.TRANSPARENT, 12));
        button.setImageTintList(ColorStateList.valueOf(selected ? Color.WHITE : MUTED));
    }

    private View mouseButton(String label, String description, Runnable action) {
        TextView button = text(label, 13, Color.rgb(203, 213, 225));
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(roundedBackground(SURFACE_RAISED, SURFACE_RAISED, 12));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView actionButton(String label, Runnable action) {
        TextView button = text(label, 14, Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setBackground(roundedBackground(SURFACE_RAISED, BORDER, 14));
        button.setOnClickListener(ignored -> action.run());
        return button;
    }

    private LinearLayout.LayoutParams equalButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams equalModeParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
