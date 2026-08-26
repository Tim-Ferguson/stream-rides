package com.pelotonhack.ridestarter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MediaLauncher {
    static final String EXTRA_RETURN_TO_MEDIA_TASK =
            "com.pelotonhack.ridestarter.extra.RETURN_TO_MEDIA_TASK";
    private static final String DISALLOW_PIP_WHILE_LAUNCHING =
            "android:activity.disallowEnterPictureInPictureWhileLaunching";
    private static final String PREFS = "media_launcher";
    private static final String LAST_PACKAGE = "last_package";
    private static final String APP_ORDER = "app_order";
    private static final String HIDDEN_PACKAGES = "hidden_packages";
    private static final String DESIRED_PACKAGES = "desired_packages";
    private static final String BIKE_APP_PACKAGE = "com.peloton.activity";
    private static final String APP_STORE_PACKAGE = "com.aurora.store";
    private static final String ZWIFT_PACKAGE = "com.zwift.zwiftgame";
    private static final long RIDE_START_OVERLAY_WINDOW_MS = 4L * 60L * 60L * 1000L;
    private static final RideStartOverlayArm RIDE_START_OVERLAY_ARM =
            new RideStartOverlayArm(RIDE_START_OVERLAY_WINDOW_MS);
    private static final ComponentName ZWIFT_MAIN = new ComponentName(
            ZWIFT_PACKAGE, "com.zwift.zwiftgame.ZwiftMainActivity");

    private static final MediaApp[] APPS = {
            new MediaApp("Netflix", "com.netflix.mediaclient"),
            new MediaApp("Hulu", "com.hulu.plus"),
            new MediaApp("Disney+", "com.disney.disneyplus"),
            new MediaApp("Max", MediaPackageAliases.MAX_MOBILE),
            new MediaApp("Prime Video", MediaPackageAliases.PRIME_MOBILE),
            new MediaApp("Apple TV", "com.apple.atve.androidtv.appletv"),
            new MediaApp("Peacock", "com.peacocktv.peacockandroid"),
            new MediaApp("Paramount+", MediaPackageAliases.PARAMOUNT_MOBILE),
            new MediaApp("MGM+", "com.epix.epix.now"),
            new MediaApp("YouTube", "com.google.android.youtube.tv"),
            new MediaApp("YouTube TV", "com.google.android.youtube.tvunplugged"),
            new MediaApp("Plex", "com.plexapp.android"),
            new MediaApp("Tubi", "com.tubitv"),
            new MediaApp("Pluto TV", "tv.pluto.android"),
            new MediaApp("Crunchyroll", "com.crunchyroll.crunchyroid"),
            new MediaApp("ESPN", "com.espn.score_center"),
            new MediaApp("Sling", "com.sling"),
            new MediaApp("Zwift", ZWIFT_PACKAGE),
            new MediaApp("Zoom", "us.zoom.videomeetings"),
            new MediaApp("ChatGPT", "com.openai.chatgpt"),
            new MediaApp("Termux", "com.termux"),
            new MediaApp("Audible", "com.audible.application"),
            new MediaApp("Kindle", "com.amazon.kindle"),
            new MediaApp("AirScreen", "com.ionitech.airscreen"),
            new MediaApp("AirReceiver Lite", "com.softmedia.receiver.lite"),
            new MediaApp("Kodi", "org.xbmc.kodi"),
            new MediaApp("Firefox", "org.mozilla.firefox"),
    };

    private MediaLauncher() {
    }

    static List<MediaApp> installedApps(Context context) {
        return orderedInstalledApps(context);
    }

    static List<MediaApp> catalogApps() {
        List<MediaApp> apps = new ArrayList<MediaApp>();
        Collections.addAll(apps, APPS);
        return Collections.unmodifiableList(apps);
    }

    static List<MediaApp> missingApps(Context context) {
        List<MediaApp> missing = new ArrayList<MediaApp>();
        Set<String> desired = desiredPackages(context);
        for (MediaApp app : APPS) {
            if (desired.contains(app.packageName) && !isInstalled(context, app.packageName)) {
                missing.add(app);
            }
        }
        for (MediaApp app : APPS) {
            if (!desired.contains(app.packageName) && !isInstalled(context, app.packageName)) {
                missing.add(app);
            }
        }
        return Collections.unmodifiableList(missing);
    }

    static boolean isInstalled(Context context, String packageName) {
        return launchIntent(context.getPackageManager(), canonicalPackageName(packageName)) != null;
    }

    static String installedPackageName(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        for (String candidate : MediaPackageAliases.launchCandidates(packageName)) {
            if (directLaunchIntent(packageManager, candidate) != null) {
                return candidate;
            }
        }
        return canonicalPackageName(packageName);
    }

    static List<MediaApp> visibleInstalledApps(Context context) {
        Set<String> hidden = hiddenPackages(context);
        List<MediaApp> visible = new ArrayList<MediaApp>();
        for (MediaApp app : orderedInstalledApps(context)) {
            if (!hidden.contains(app.packageName)) {
                visible.add(app);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    static List<MediaApp> orderedInstalledApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<MediaApp> available = new ArrayList<MediaApp>();
        for (MediaApp app : APPS) {
            if (launchIntent(packageManager, app.packageName) != null) {
                available.add(app);
            }
        }

        List<MediaApp> ordered = new ArrayList<MediaApp>();
        Set<String> added = new HashSet<String>();
        String savedOrder = prefs(context).getString(APP_ORDER, "");
        if (!savedOrder.isEmpty()) {
            try {
                JSONArray array = new JSONArray(savedOrder);
                for (int index = 0; index < array.length(); index++) {
                    String packageName = canonicalPackageName(array.optString(index, ""));
                    MediaApp app = findByPackage(available, packageName);
                    if (app != null && added.add(app.packageName)) {
                        ordered.add(app);
                    }
                }
            } catch (JSONException ignored) {
                // A malformed preference falls back to the built-in order.
            }
        }
        for (MediaApp app : available) {
            if (added.add(app.packageName)) {
                ordered.add(app);
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    static boolean isHidden(Context context, String packageName) {
        return hiddenPackages(context).contains(canonicalPackageName(packageName));
    }

    static void setHidden(Context context, String packageName, boolean hidden) {
        packageName = canonicalPackageName(packageName);
        Set<String> packages = hiddenPackages(context);
        if (hidden) {
            packages.add(packageName);
        } else {
            packages.remove(packageName);
        }
        prefs(context).edit().putStringSet(HIDDEN_PACKAGES, packages).apply();
    }

    static void move(Context context, String packageName, int offset) {
        packageName = canonicalPackageName(packageName);
        List<MediaApp> apps = new ArrayList<MediaApp>(orderedInstalledApps(context));
        int from = -1;
        for (int index = 0; index < apps.size(); index++) {
            if (apps.get(index).packageName.equals(packageName)) {
                from = index;
                break;
            }
        }
        int to = from + offset;
        if (from < 0 || to < 0 || to >= apps.size()) {
            return;
        }
        Collections.swap(apps, from, to);
        JSONArray array = new JSONArray();
        for (MediaApp app : apps) {
            array.put(app.packageName);
        }
        prefs(context).edit().putString(APP_ORDER, array.toString()).apply();
    }

    static boolean isMediaPackage(String packageName) {
        packageName = canonicalPackageName(packageName);
        if (packageName.isEmpty()) {
            return false;
        }
        for (MediaApp app : APPS) {
            if (app.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    static void rememberIfMedia(Context context, CharSequence packageName) {
        if (packageName == null) {
            return;
        }
        String value = canonicalPackageName(packageName.toString());
        if (isMediaPackage(value)) {
            remember(context, value);
        }
    }

    static boolean launch(Context context, String packageName) {
        packageName = canonicalPackageName(packageName);
        if (!isMediaPackage(packageName)) {
            return false;
        }
        Intent intent = launchIntent(context.getPackageManager(), packageName);
        if (intent == null) {
            return false;
        }
        remember(context, packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            context.startActivity(intent);
            if (RideSessionStore.read(context).active) {
                disarmRideStartOverlay();
            } else {
                armRideStartOverlay();
            }
            RideStarterAccessibilityService.refreshOverlay();
            return true;
        } catch (RuntimeException ignored) {
            disarmRideStartOverlay();
            return false;
        }
    }

    static boolean isRideStartOverlayArmed() {
        return RIDE_START_OVERLAY_ARM.isArmed(SystemClock.elapsedRealtime());
    }

    static void disarmRideStartOverlay() {
        RIDE_START_OVERLAY_ARM.disarm();
    }

    static void resetTransientOverlayState() {
        disarmRideStartOverlay();
    }

    private static void armRideStartOverlay() {
        RIDE_START_OVERLAY_ARM.arm(SystemClock.elapsedRealtime());
    }

    static boolean launchLast(Context context) {
        String packageName = lastPackage(context);
        return !packageName.isEmpty() && launch(context, packageName);
    }

    static String lastPackage(Context context) {
        return canonicalPackageName(prefs(context).getString(LAST_PACKAGE, ""));
    }

    static String displayNameForPackage(String packageName) {
        packageName = canonicalPackageName(packageName);
        for (MediaApp app : APPS) {
            if (app.packageName.equals(packageName)) {
                return app.displayName;
            }
        }
        return "Streaming app";
    }

    static void openHub(Context context) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context, MainActivity.class));
        intent.putExtra(EXTRA_RETURN_TO_MEDIA_TASK, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Bundle options = new Bundle();
        options.putBoolean(DISALLOW_PIP_WHILE_LAUNCHING, true);
        context.startActivity(intent, options);
    }

    static boolean launchBikeHome(Context context) {
        Intent intent = launchIntent(context.getPackageManager(), BIKE_APP_PACKAGE);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean launchAppStore(Context context) {
        Intent intent = launchIntent(context.getPackageManager(), APP_STORE_PACKAGE);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean launchStoreListing(Context context, String packageName) {
        packageName = canonicalPackageName(packageName);
        if (!isMediaPackage(packageName)) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + Uri.encode(packageName)));
        if (launchIntent(context.getPackageManager(), APP_STORE_PACKAGE) != null) {
            intent.setPackage(APP_STORE_PACKAGE);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return launchAppStore(context);
        }
    }

    static JSONArray exportOrder(Context context) {
        JSONArray order = new JSONArray();
        Set<String> added = new HashSet<String>();
        String savedOrder = prefs(context).getString(APP_ORDER, "");
        if (!savedOrder.isEmpty()) {
            try {
                JSONArray saved = new JSONArray(savedOrder);
                for (int index = 0; index < saved.length(); index++) {
                    String packageName = canonicalPackageName(saved.optString(index, ""));
                    if (isMediaPackage(packageName) && added.add(packageName)) {
                        order.put(packageName);
                    }
                }
            } catch (JSONException ignored) {
                // Append the built-in catalog below.
            }
        }
        for (MediaApp app : APPS) {
            if (added.add(app.packageName)) {
                order.put(app.packageName);
            }
        }
        return order;
    }

    static JSONArray exportHiddenPackages(Context context) {
        JSONArray hidden = new JSONArray();
        Set<String> packages = hiddenPackages(context);
        for (MediaApp app : APPS) {
            if (packages.contains(app.packageName)) {
                hidden.put(app.packageName);
            }
        }
        return hidden;
    }

    static JSONArray exportInstalledPackages(Context context) {
        JSONArray installed = new JSONArray();
        for (MediaApp app : APPS) {
            if (isInstalled(context, app.packageName)) {
                installed.put(app.packageName);
            }
        }
        return installed;
    }

    static JSONArray exportDesiredPackages(Context context) {
        JSONArray desired = new JSONArray();
        for (String packageName : desiredPackages(context)) {
            desired.put(packageName);
        }
        return desired;
    }

    static void importConfiguration(Context context, JSONArray order, JSONArray hidden,
                                    JSONArray installed, JSONArray desired,
                                    String lastPackage) {
        JSONArray validOrder = validatedPackages(order);
        Set<String> validHidden = validatedPackageSet(hidden);
        Set<String> validDesired = validatedPackageSet(desired != null ? desired : installed);
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(APP_ORDER, validOrder.toString())
                .putStringSet(HIDDEN_PACKAGES, validHidden)
                .putStringSet(DESIRED_PACKAGES, validDesired);
        String validLastPackage = canonicalPackageName(lastPackage);
        if (isMediaPackage(validLastPackage)) {
            editor.putString(LAST_PACKAGE, validLastPackage);
        } else {
            editor.remove(LAST_PACKAGE);
        }
        editor.commit();
    }

    private static void remember(Context context, String packageName) {
        prefs(context).edit().putString(
                LAST_PACKAGE, canonicalPackageName(packageName)).apply();
    }

    private static Intent launchIntent(PackageManager packageManager, String packageName) {
        for (String candidate : MediaPackageAliases.launchCandidates(packageName)) {
            Intent intent = directLaunchIntent(packageManager, candidate);
            if (intent != null) {
                return intent;
            }
        }
        return null;
    }

    private static Intent directLaunchIntent(PackageManager packageManager, String packageName) {
        if (ZWIFT_PACKAGE.equals(packageName)) {
            try {
                packageManager.getActivityInfo(ZWIFT_MAIN, 0);
                return new Intent().setComponent(ZWIFT_MAIN);
            } catch (PackageManager.NameNotFoundException ignored) {
                // A later Zwift release may replace this activity and restore a usable launcher intent.
            }
        }
        Intent intent = packageManager.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            intent = packageManager.getLeanbackLaunchIntentForPackage(packageName);
        }
        return intent;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> hiddenPackages(Context context) {
        return canonicalPackageSet(prefs(context).getStringSet(
                HIDDEN_PACKAGES, Collections.<String>emptySet()));
    }

    private static Set<String> desiredPackages(Context context) {
        Set<String> stored = prefs(context).getStringSet(DESIRED_PACKAGES, null);
        if (stored != null) {
            return canonicalPackageSet(stored);
        }
        Set<String> installed = new HashSet<String>();
        for (MediaApp app : APPS) {
            if (isInstalled(context, app.packageName)) {
                installed.add(app.packageName);
            }
        }
        return installed;
    }

    private static JSONArray validatedPackages(JSONArray packages) {
        JSONArray valid = new JSONArray();
        Set<String> added = new HashSet<String>();
        if (packages == null) {
            return valid;
        }
        for (int index = 0; index < packages.length(); index++) {
            String packageName = canonicalPackageName(packages.optString(index, ""));
            if (isMediaPackage(packageName) && added.add(packageName)) {
                valid.put(packageName);
            }
        }
        return valid;
    }

    private static Set<String> validatedPackageSet(JSONArray packages) {
        Set<String> valid = new HashSet<String>();
        if (packages == null) {
            return valid;
        }
        for (int index = 0; index < packages.length(); index++) {
            String packageName = canonicalPackageName(packages.optString(index, ""));
            if (isMediaPackage(packageName)) {
                valid.add(packageName);
            }
        }
        return valid;
    }

    private static MediaApp findByPackage(List<MediaApp> apps, String packageName) {
        packageName = canonicalPackageName(packageName);
        for (MediaApp app : apps) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    private static Set<String> canonicalPackageSet(Set<String> packages) {
        Set<String> canonical = new HashSet<String>();
        for (String packageName : packages) {
            String value = canonicalPackageName(packageName);
            if (isMediaPackage(value)) {
                canonical.add(value);
            }
        }
        return canonical;
    }

    private static String canonicalPackageName(String packageName) {
        return MediaPackageAliases.canonical(packageName);
    }

    static final class MediaApp {
        final String displayName;
        final String packageName;

        MediaApp(String displayName, String packageName) {
            this.displayName = displayName;
            this.packageName = packageName;
        }
    }
}
