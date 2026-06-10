package com.etgdreplay.reader;

import android.app.Activity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class DReplayBridge {
    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024;

    private DReplayBridge() {
    }

    public static String version() {
        return "1.2.0";
    }

    public static boolean isDReplayName(String value) {
        return value != null && value.toLowerCase().endsWith(".dreplay");
    }

    public static boolean openFile(Object activityObject, String path, String displayName) {
        Activity activity = activityObject instanceof Activity ? (Activity) activityObject : null;
        if (!isDReplayName(path) && !isDReplayName(displayName)) {
            return false;
        }
        if (activity == null) {
            return true;
        }
        try {
            File file = new File(path == null ? "" : path);
            if (!file.exists() || !file.isFile()) {
                DReplayViewer.showError(activity, "Файл не найден: " + displayName);
                return true;
            }
            if (file.length() > MAX_FILE_SIZE) {
                DReplayViewer.showError(activity, "Файл слишком большой для replay: " + file.length() + " байт");
                return true;
            }
            String text = readUtf8(file);
            DReplayParser.Replay replay = DReplayParser.parse(text);
            DReplayViewer.show(activity, safeName(displayName, file.getName()), replay);
            return true;
        } catch (Throwable error) {
            DReplayViewer.showError(activity, "Ошибка чтения .dreplay:\n" + error.getClass().getSimpleName() + ": " + error.getMessage());
            return true;
        }
    }

    public static String summarizeText(String text) {
        return DReplayParser.parse(text).buildReport();
    }

    private static String readUtf8(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(512, (int) Math.min(file.length(), MAX_FILE_SIZE)));
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_FILE_SIZE) {
                    throw new IllegalArgumentException("too large");
                }
            }
        } finally {
            in.close();
        }
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            return text.substring(1);
        }
        return text;
    }

    private static String safeName(String displayName, String fallback) {
        if (displayName != null && displayName.trim().length() > 0) {
            return displayName.trim();
        }
        return fallback == null ? "replay.dreplay" : fallback;
    }
}
