package com.qmsbrowser;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class BrowserErrorView extends LinearLayout {
    private final TextView message;
    private final Button retry;

    BrowserErrorView(Context context, Runnable retryAction) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(32), dp(32), dp(32), dp(32));
        setBackgroundColor(Color.rgb(16, 17, 36));

        TextView title = new TextView(context);
        title.setText("Page unavailable");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        addView(title);

        message = new TextView(context);
        message.setTextColor(Color.rgb(156, 163, 175));
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(10), 0, dp(22));
        addView(message);

        retry = new Button(context);
        retry.setText("Try again");
        retry.setAllCaps(false);
        retry.setTextColor(Color.WHITE);
        retry.setTextSize(14);
        retry.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(124, 58, 237));
        background.setCornerRadius(dp(12));
        retry.setBackground(background);
        retry.setOnClickListener(view -> retryAction.run());
        addView(retry, new LayoutParams(dp(160), dp(48)));

        setVisibility(View.GONE);
    }

    void showError(String detail, boolean canRetry) {
        message.setText(detail);
        retry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        setVisibility(View.VISIBLE);
        bringToFront();
    }

    void hideError() {
        setVisibility(View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
