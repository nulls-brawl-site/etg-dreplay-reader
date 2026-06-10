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
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
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
                showBoardDialog(activity, fileName, replay);
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
                TextView close = controlButton(activity, "OK", true, 96);
                close.setOnClickListener(v -> dialog.dismiss());
                LinearLayout buttons = buttonRow(activity);
                buttons.addView(close);
                root.addView(buttons);
                dialog.setContentView(root);
                showSized(dialog, activity, 0.94f);
            }
        });
    }

    private static void showBoardDialog(final Activity activity, String fileName, final DReplayParser.Replay replay) {
        final Dialog dialog = baseDialog(activity);
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] playing = new boolean[]{false};

        LinearLayout root = rootLayout(activity);
        root.addView(titleView(activity, replay.reportTitle()));

        String subtitle = replay.shortSummary();
        if (!TextUtils.isEmpty(fileName)) {
            subtitle = fileName + "\n" + subtitle;
        }
        root.addView(subtitleView(activity, subtitle));

        final DReplayBoardView board = new DReplayBoardView(activity, replay);
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        int boardHeight = Math.min(dp(activity, 530), Math.max(dp(activity, 390), (int) (screenH * 0.58f)));
        root.addView(board, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, boardHeight));

        final TextView stepText = subtitleView(activity, board.getStepText());
        stepText.setPadding(0, dp(activity, 8), 0, dp(activity, 2));
        root.addView(stepText);
        board.setStepListener(view -> stepText.setText(view.getStepText()));

        LinearLayout controls = buttonRow(activity);
        controls.setGravity(Gravity.CENTER);
        final TextView previous = controlButton(activity, "‹", false, 44);
        final TextView play = controlButton(activity, "▶", true, 54);
        final TextView next = controlButton(activity, "›", false, 44);
        final TextView text = controlButton(activity, "Текст", false, 74);
        final TextView copy = controlButton(activity, "Копия", false, 74);
        final TextView close = controlButton(activity, "Закрыть", true, 90);

        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            @Override
            public void run() {
                if (!playing[0]) {
                    return;
                }
                if (board.getStep() >= board.getMaxStep()) {
                    playing[0] = false;
                    play.setText("▶");
                    return;
                }
                board.next();
                handler.postDelayed(this, 700);
            }
        };

        previous.setOnClickListener(v -> {
            playing[0] = false;
            play.setText("▶");
            board.previous();
        });
        next.setOnClickListener(v -> {
            playing[0] = false;
            play.setText("▶");
            board.next();
        });
        play.setOnClickListener(v -> {
            if (playing[0]) {
                playing[0] = false;
                play.setText("▶");
                return;
            }
            if (board.getStep() >= board.getMaxStep()) {
                board.setStep(0);
            }
            playing[0] = true;
            play.setText("Ⅱ");
            handler.post(tick[0]);
        });
        text.setOnClickListener(v -> showReportDialog(activity, replay.buildReport()));
        copy.setOnClickListener(v -> copyReport(activity, replay.buildReport()));
        close.setOnClickListener(v -> dialog.dismiss());

        controls.addView(previous);
        controls.addView(play);
        controls.addView(next);
        root.addView(controls);

        LinearLayout actions = buttonRow(activity);
        actions.addView(text);
        actions.addView(copy);
        actions.addView(close);
        root.addView(actions);

        dialog.setOnDismissListener(d -> {
            playing[0] = false;
            handler.removeCallbacksAndMessages(null);
        });
        dialog.setContentView(root);
        showSized(dialog, activity, 0.97f);
    }

    private static void showReportDialog(final Activity activity, final String report) {
        final Dialog dialog = baseDialog(activity);
        LinearLayout root = rootLayout(activity);
        root.addView(titleView(activity, "Текст replay"));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(bodyText(activity, report, true), new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        int maxHeight = Math.max(dp(activity, 280), (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.60f));
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxHeight));

        LinearLayout buttons = buttonRow(activity);
        TextView copy = controlButton(activity, "Копировать", false, 104);
        copy.setOnClickListener(v -> copyReport(activity, report));
        TextView close = controlButton(activity, "Закрыть", true, 96);
        close.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(copy);
        buttons.addView(close);
        root.addView(buttons);

        dialog.setContentView(root);
        showSized(dialog, activity, 0.94f);
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
        int pad = dp(context, 14);
        root.setPadding(pad, pad, pad, dp(context, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(themeColor("key_dialogBackground", themeColor("key_windowBackgroundWhite", Color.WHITE)));
        background.setCornerRadius(dp(context, 16));
        root.setBackground(background);
        return root;
    }

    private static TextView titleView(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(themeColor("key_windowBackgroundWhiteBlackText", Color.rgb(32, 32, 32)));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextSize(19);
        view.setGravity(Gravity.LEFT);
        view.setPadding(0, 0, 0, dp(context, 5));
        view.setSingleLine(false);
        return view;
    }

    private static TextView subtitleView(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(themeColor("key_windowBackgroundWhiteGrayText", Color.rgb(115, 115, 115)));
        view.setTextSize(12);
        view.setPadding(0, 0, 0, dp(context, 10));
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
        background.setCornerRadius(dp(context, 9));
        view.setBackground(background);
        view.setPadding(pad, pad, pad, pad);
        return view;
    }

    private static LinearLayout buttonRow(Context context) {
        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(context, 8), 0, 0);
        return buttons;
    }

    private static TextView controlButton(Context context, String text, boolean accent, int minWidthDp) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(14);
        button.setTextColor(accent ? themeColor("key_featuredStickers_buttonText", Color.WHITE) : themeColor("key_featuredStickers_addButton", Color.rgb(45, 136, 220)));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(context, 10));
        background.setColor(accent ? themeColor("key_featuredStickers_addButton", Color.rgb(45, 136, 220)) : Color.argb(22, 45, 136, 220));
        button.setBackground(background);
        int hPad = dp(context, 10);
        button.setPadding(hPad, 0, hPad, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Math.max(dp(context, minWidthDp), LinearLayout.LayoutParams.WRAP_CONTENT), dp(context, 42));
        params.leftMargin = dp(context, 6);
        button.setLayoutParams(params);
        return button;
    }

    private static void copyReport(Activity activity, String report) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("dreplay", report));
            Toast.makeText(activity, "Скопировано", Toast.LENGTH_SHORT).show();
        }
    }

    private static void showSized(Dialog dialog, Activity activity, float widthRatio) {
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
        window.setLayout(Math.min((int) (metrics.widthPixels * widthRatio), dp(activity, 620)), WindowManager.LayoutParams.WRAP_CONTENT);
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int themeColor(String keyName, int fallback) {
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
