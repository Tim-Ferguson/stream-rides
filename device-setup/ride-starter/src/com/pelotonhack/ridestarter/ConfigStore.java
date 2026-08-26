package com.pelotonhack.ridestarter;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.ErrnoException;
import android.system.Os;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class ConfigStore {
    static final int SCHEMA_VERSION = 2;
    static final String FILE_NAME = "saro-setup.json";
    static final String STATUS_FILE_NAME = "saro-setup.status";
    private static final String LEGACY_FILE_NAME = "peloton-setup.json";
    private static final long MAX_CONFIG_BYTES = 1024L * 1024L;

    private static final String OVERLAY_PREFS = "stats_overlay";
    private static final String OVERLAY_X = "x";
    private static final String OVERLAY_Y = "y";
    private static final String RIDE_START_X = "start_x";
    private static final String RIDE_START_Y = "start_y";

    private ConfigStore() {
    }

    static File exportToDefaultFile(Context context) throws IOException, JSONException {
        File file = defaultFile(context);
        File parent = file.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Cannot create configuration directory");
        }

        JSONObject launcher = new JSONObject();
        launcher.put("appOrder", MediaLauncher.exportOrder(context));
        launcher.put("hiddenPackages", MediaLauncher.exportHiddenPackages(context));
        launcher.put("installedPackages", MediaLauncher.exportInstalledPackages(context));
        launcher.put("desiredPackages", MediaLauncher.exportDesiredPackages(context));
        launcher.put("lastPackage", MediaLauncher.lastPackage(context));

        SharedPreferences overlayPreferences =
                context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE);
        JSONObject overlay = new JSONObject();
        if (overlayPreferences.contains(OVERLAY_X)) {
            overlay.put("x", overlayPreferences.getInt(OVERLAY_X, 0));
        }
        if (overlayPreferences.contains(OVERLAY_Y)) {
            overlay.put("y", overlayPreferences.getInt(OVERLAY_Y, 0));
        }
        JSONObject rideStart = new JSONObject();
        if (overlayPreferences.contains(RIDE_START_X)) {
            rideStart.put("x", overlayPreferences.getInt(RIDE_START_X, 0));
        }
        if (overlayPreferences.contains(RIDE_START_Y)) {
            rideStart.put("y", overlayPreferences.getInt(RIDE_START_Y, 0));
        }
        if (rideStart.length() > 0) {
            overlay.put("rideStart", rideStart);
        }
        overlay.put("subscriptionPromptAutomation",
                StatsOverlayManager.isSubscriptionPromptAutomationEnabled(context));
        overlay.put("zwiftCompanionBridge", ZwiftBleBridge.isEnabled(context));

        SharedPreferences tvRemotePreferences = context.getSharedPreferences(
                TvRemoteOverlay.PREFS, Context.MODE_PRIVATE);
        JSONObject tvRemote = new JSONObject();
        if (tvRemotePreferences.contains(TvRemoteOverlay.PREF_X)) {
            tvRemote.put("x", tvRemotePreferences.getInt(TvRemoteOverlay.PREF_X, 0));
        }
        if (tvRemotePreferences.contains(TvRemoteOverlay.PREF_Y)) {
            tvRemote.put("y", tvRemotePreferences.getInt(TvRemoteOverlay.PREF_Y, 0));
        }
        if (tvRemote.length() > 0) {
            overlay.put("tvRemote", tvRemote);
        }

        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("launcher", launcher);
        root.put("overlay", overlay);
        root.put("games", new GameProgressStore(context).exportConfiguration());
        root.put("lastLocalRide", RideSessionStore.exportLastRide(context));

        writeAtomically(file, root.toString(2) + "\n");
        return file;
    }

    static void importFromDefaultFile(Context context) throws IOException, JSONException {
        File file = defaultFile(context);
        if (!file.isFile()) {
            File legacyFile = new File(file.getParentFile(), LEGACY_FILE_NAME);
            if (legacyFile.isFile()) {
                file = legacyFile;
            }
        }
        if (!file.isFile()) {
            throw new IOException("No exported configuration found");
        }
        if (file.length() > MAX_CONFIG_BYTES) {
            throw new IOException("Configuration exceeds 1 MiB");
        }

        StringBuilder json = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line).append('\n');
            }
        } finally {
            reader.close();
        }

        JSONObject root = new JSONObject(json.toString());
        int schemaVersion = root.optInt("schemaVersion", -1);
        if (schemaVersion != 1 && schemaVersion != SCHEMA_VERSION) {
            throw new JSONException("Unsupported schema version " + schemaVersion);
        }

        JSONObject launcher = root.getJSONObject("launcher");
        MediaLauncher.importConfiguration(context,
                launcher.optJSONArray("appOrder"),
                launcher.optJSONArray("hiddenPackages"),
                launcher.optJSONArray("installedPackages"),
                launcher.optJSONArray("desiredPackages"),
                launcher.optString("lastPackage", ""));

        JSONObject overlay = root.optJSONObject("overlay");
        boolean zwiftBridgeEnabled = false;
        SharedPreferences.Editor editor = context
                .getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
                .edit().clear();
        if (overlay != null) {
            if (overlay.has("x")) {
                editor.putInt(OVERLAY_X, clamp(overlay.optInt("x", 0), -10000, 10000));
            }
            if (overlay.has("y")) {
                editor.putInt(OVERLAY_Y, clamp(overlay.optInt("y", 0), -10000, 10000));
            }
            boolean promptAutomation = overlay.has("subscriptionPromptAutomation")
                    ? overlay.optBoolean("subscriptionPromptAutomation", false)
                    : overlay.optBoolean("pelotonAutomation", false);
            editor.putBoolean("automation_enabled", promptAutomation);
            zwiftBridgeEnabled = overlay.optBoolean("zwiftCompanionBridge", false);
            editor.putBoolean("zwift_bridge_enabled", zwiftBridgeEnabled);
            JSONObject rideStart = overlay.optJSONObject("rideStart");
            if (rideStart != null) {
                if (rideStart.has("x")) {
                    editor.putInt(RIDE_START_X,
                            clamp(rideStart.optInt("x", 0), -10000, 10000));
                }
                if (rideStart.has("y")) {
                    editor.putInt(RIDE_START_Y,
                            clamp(rideStart.optInt("y", 0), -10000, 10000));
                }
            }
        }
        editor.commit();
        RideStarterAccessibilityService.reloadOverlayPositions();

        SharedPreferences.Editor tvRemoteEditor = context.getSharedPreferences(
                TvRemoteOverlay.PREFS, Context.MODE_PRIVATE).edit().clear();
        JSONObject tvRemote = overlay == null ? null : overlay.optJSONObject("tvRemote");
        if (tvRemote != null) {
            if (tvRemote.has("x")) {
                tvRemoteEditor.putInt(TvRemoteOverlay.PREF_X,
                        clamp(tvRemote.optInt("x", 0), -10000, 10000));
            }
            if (tvRemote.has("y")) {
                tvRemoteEditor.putInt(TvRemoteOverlay.PREF_Y,
                        clamp(tvRemote.optInt("y", 0), -10000, 10000));
            }
        }
        tvRemoteEditor.commit();
        TvRemoteAccessibilityService.reloadPosition();
        RideStarterAccessibilityService.setZwiftBridgeEnabled(
                context, zwiftBridgeEnabled);
        new GameProgressStore(context).importConfiguration(root.optJSONObject("games"));
        RideSessionStore.importLastRide(context, root.optJSONObject("lastLocalRide"));
    }

    static File defaultFile(Context context) throws IOException {
        File directory = context.getExternalFilesDir(null);
        if (directory == null) {
            throw new IOException("External storage is unavailable");
        }
        return new File(directory, FILE_NAME);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static void writeTransferStatus(Context context, String requestId, String action,
                                    boolean success, String message) throws IOException, JSONException {
        File configuration = defaultFile(context);
        File status = new File(configuration.getParentFile(), STATUS_FILE_NAME);
        JSONObject result = new JSONObject();
        result.put("requestId", requestId == null ? "" : requestId);
        result.put("action", action == null ? "" : action);
        result.put("ok", success);
        result.put("message", message == null ? "" : message);
        writeAtomically(status, result.toString() + "\n");
    }

    private static void writeAtomically(File target, String contents) throws IOException {
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Cannot create output directory");
        }
        File temporary = new File(parent, target.getName() + ".tmp");
        FileOutputStream output = new FileOutputStream(temporary, false);
        boolean complete = false;
        try {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
            output.close();
            try {
                Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            } catch (ErrnoException exception) {
                throw new IOException("Cannot replace " + target.getName(), exception);
            }
            complete = true;
        } finally {
            try {
                output.close();
            } catch (IOException ignored) {
                // Preserve the original write or rename failure.
            }
            if (!complete && temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }
}
