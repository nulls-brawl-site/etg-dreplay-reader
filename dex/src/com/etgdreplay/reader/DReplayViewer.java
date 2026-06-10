package com.etgdreplay.reader;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class DReplayViewer {
    private DReplayViewer() {
    }

    public static void show(final Activity activity, final String fileName, final DReplayParser.Replay replay) {
        if (activity == null || replay == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing()) {
                    return;
                }
                showDialog(activity, fileName, replay);
            }
        });
    }

    public static void showError(final Activity activity, final String message) {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing()) {
                    return;
                }
                final Dialog dialog = baseDialog(activity);
                LinearLayout root = rootLayout(activity);
                root.addView(titleView(activity, "DReplay Reader"));
                TextView text = bodyText(activity, message == null ? "Не удалось открыть файл." : message, false);
                root.addView(text, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                Button close = button(activity, "OK", true);
                close.setOnClickListener(v -> dialog.dismiss());
                LinearLayout buttons = new LinearLayout(activity);
                buttons.setGravity(Gravity.RIGHT);
                buttons.addView(close);
                root.addView(buttons);
                dialog.setContentView(root);
                showSized(dialog, activity);
            }
        });
    }

    private static void showDialog(final Activity activity, String fileName, final DReplayParser.Replay replay) {
        final Dialog dialog = baseDialog(activity);
        LinearLayout root = rootLayout(activity);
        root.addView(titleView(activity, replay.reportTitle()));

        String subtitle = replay.shortSummary();
        if (!TextUtils.isEmpty(fileName)) {
            subtitle = fileName + "\n" + subtitle;
        }
        root.addView(subtitleView(activity, subtitle));

        final String report = replay.buildReport();
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(bodyText(activity, report, true), new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        int maxHeight = Math.max(dp(activity, 260), (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.62f));
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxHeight));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, dp(activity, 10), 0, 0);

        Button copy = button(activity, "Копировать", false);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("dreplay", report));
                Toast.makeText(activity, "Скопировано", Toast.LENGTH_SHORT).show();
            }
        });
        buttons.addView(copy);

        Button close = button(activity, "Закрыть", true);
        close.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(close);
        root.addView(buttons);

        dialog.setContentView(root);
        showSized(dialog, activity);
    }

    private static Dialog baseDialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private static LinearLayout rootLayout(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 18);
        root.setPadding(pad, pad, pad, dp(context, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(themeColor("key_dialogBackground", themeColor("key_windowBackgroundWhite", Color.WHITE)));
        background.setCornerRadius(dp(context, 14));
        root.setBackground(background);
        return root;
    }

    private static TextView titleView(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(themeColor("key_windowBackgroundWhiteBlackText", Color.rgb(32, 32, 32)));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextSize(20);
        view.setGravity(Gravity.LEFT);
        view.setPadding(0, 0, 0, dp(context, 6));
        view.setSingleLine(false);
        return view;
    }

    private static TextView subtitleView(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(themeColor("key_windowBackgroundWhiteGrayText", Color.rgb(115, 115, 115)));
        view.setTextSize(13);
        view.setPadding(0, 0, 0, dp(context, 12));
        view.setSingleLine(false);
        return view;
    }

    private static TextView bodyText(Context context, String text, boolean monospace) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(themeColor("key_windowBackgroundWhiteBlackText", Color.rgb(32, 32, 32)));
        view.setTextSize(13);
        view.setLineSpacing(dp(context, 1), 1.0f);
        view.setTextIsSelectable(true);
        if (monospace) {
            view.setTypeface(Typeface.MONOSPACE);
        }
        int pad = dp(context, 10);
        GradientDrawable background = new GradientDrawable();
        background.setColor(themeColor("key_windowBackgroundGray", Color.rgb(245, 245, 245)));
        background.setCornerRadius(dp(context, 8));
        view.setBackground(background);
        view.setPadding(pad, pad, pad, pad);
        return view;
    }

    private static Button button(Context context, String text, boolean accent) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(accent ? themeColor("key_featuredStickers_buttonText", Color.WHITE) : themeColor("key_featuredStickers_addButton", Color.rgb(45, 136, 220)));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(context, 8));
        background.setColor(accent ? themeColor("key_featuredStickers_addButton", Color.rgb(45, 136, 220)) : Color.TRANSPARENT);
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, 42));
        params.leftMargin = dp(context, 8);
        button.setLayoutParams(params);
        button.setMinWidth(dp(context, 96));
        return button;
    }

    private static void showSized(Dialog dialog, Activity activity) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.dimAmount = 0.32f;
        window.setAttributes(attrs);
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        window.setLayout(Math.min((int) (metrics.widthPixels * 0.94f), dp(activity, 560)), WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int themeColor(String keyName, int fallback) {
        try {
            Class<?> theme = DReplayViewer.class.getClassLoader().loadClass("org.telegram.ui.ActionBar.Theme");
            Field keyField = theme.getField(keyName);
            int key = keyField.getInt(null);
            Method getColor = theme.getMethod("getColor", Integer.TYPE);
            Object value = getColor.invoke(null, key);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}
