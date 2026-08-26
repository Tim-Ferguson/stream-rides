package com.pelotonhack.ridestarter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;

final class RideSessionStore {
    static final String BACKEND_LOCAL = "saro_local";

    private static final String PREFS = "local_ride_session";
    private static final String ACTIVE = "active";
    private static final String PAUSED = "paused";
    private static final String BACKEND = "backend";
    private static final String SESSION_ID = "session_id";
    private static final String RUNNING_STARTED_WALL = "running_started_wall";
    private static final String RUNNING_STARTED_ELAPSED = "running_started_elapsed";
    private static final String RUNNING_BOOT_COUNT = "running_boot_count";
    private static final String LAST_CHECKPOINT_WALL = "last_checkpoint_wall";
    private static final String ELAPSED_BASE_MS = "elapsed_base_ms";
    private static final String OUTPUT_KJ = "output_kj";
    private static final String DISTANCE_MI = "distance_mi";
    private static final String LAST_DURATION_MS = "last_duration_ms";
    private static final String LAST_OUTPUT_KJ = "last_output_kj";
    private static final String LAST_DISTANCE_MI = "last_distance_mi";
    private static final String LAST_ENDED_AT = "last_ended_at";
    private static final long MAX_STALE_RESUME_GAP_MS = 4L * 60L * 60L * 1000L;

    private RideSessionStore() {
    }

    static Session startIfNeeded(Context context) {
        Session current = read(context);
        if (current.active) {
            return current;
        }
        long wallNow = System.currentTimeMillis();
        long elapsedNow = SystemClock.elapsedRealtime();
        prefs(context).edit()
                .putBoolean(ACTIVE, true)
                .putBoolean(PAUSED, false)
                .putString(BACKEND, BACKEND_LOCAL)
                .putLong(SESSION_ID, wallNow)
                .putLong(RUNNING_STARTED_WALL, wallNow)
                .putLong(RUNNING_STARTED_ELAPSED, elapsedNow)
                .putInt(RUNNING_BOOT_COUNT, bootCount(context))
                .putLong(LAST_CHECKPOINT_WALL, wallNow)
                .putLong(ELAPSED_BASE_MS, 0L)
                .putFloat(OUTPUT_KJ, 0f)
                .putFloat(DISTANCE_MI, 0f)
                .commit();
        return read(context);
    }

    static Session read(Context context) {
        SharedPreferences preferences = prefs(context);
        boolean active = preferences.getBoolean(ACTIVE, false);
        boolean paused = preferences.getBoolean(PAUSED, false);
        long elapsed = Math.max(0L, preferences.getLong(ELAPSED_BASE_MS, 0L));
        if (active && !paused) {
            long delta;
            int storedBoot = preferences.getInt(RUNNING_BOOT_COUNT, -1);
            long elapsedStarted = preferences.getLong(
                    RUNNING_STARTED_ELAPSED, SystemClock.elapsedRealtime());
            if (storedBoot == bootCount(context)
                    && SystemClock.elapsedRealtime() >= elapsedStarted) {
                delta = SystemClock.elapsedRealtime() - elapsedStarted;
            } else {
                long wallStarted = preferences.getLong(
                        RUNNING_STARTED_WALL, System.currentTimeMillis());
                delta = Math.max(0L, System.currentTimeMillis() - wallStarted);
            }
            elapsed += Math.max(0L, Math.min(MAX_STALE_RESUME_GAP_MS, delta));
        }
        return new Session(active, paused,
                preferences.getString(BACKEND, BACKEND_LOCAL),
                preferences.getLong(SESSION_ID, 0L), elapsed,
                Math.max(0f, preferences.getFloat(OUTPUT_KJ, 0f)),
                Math.max(0f, preferences.getFloat(DISTANCE_MI, 0f)));
    }

    static void pauseIfStale(Context context) {
        SharedPreferences preferences = prefs(context);
        if (!preferences.getBoolean(ACTIVE, false)
                || preferences.getBoolean(PAUSED, false)) {
            return;
        }
        long checkpointAt = preferences.getLong(
                LAST_CHECKPOINT_WALL, System.currentTimeMillis());
        long gap = System.currentTimeMillis() - checkpointAt;
        if (gap <= MAX_STALE_RESUME_GAP_MS && gap >= 0L) {
            return;
        }
        preferences.edit()
                .putBoolean(PAUSED, true)
                .putLong(RUNNING_STARTED_WALL, System.currentTimeMillis())
                .putLong(RUNNING_STARTED_ELAPSED, SystemClock.elapsedRealtime())
                .putInt(RUNNING_BOOT_COUNT, bootCount(context))
                .putLong(LAST_CHECKPOINT_WALL, System.currentTimeMillis())
                .commit();
    }

    static Session togglePause(Context context) {
        Session current = read(context);
        if (!current.active) {
            return current;
        }
        long wallNow = System.currentTimeMillis();
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(PAUSED, !current.paused)
                .putLong(ELAPSED_BASE_MS, current.elapsedMs)
                .putLong(RUNNING_STARTED_WALL, wallNow)
                .putLong(RUNNING_STARTED_ELAPSED, SystemClock.elapsedRealtime())
                .putInt(RUNNING_BOOT_COUNT, bootCount(context))
                .putLong(LAST_CHECKPOINT_WALL, wallNow);
        editor.commit();
        return read(context);
    }

    static void checkpoint(Context context, long elapsedMs, float outputKilojoules,
                           float distanceMiles, boolean synchronous) {
        long wallNow = System.currentTimeMillis();
        SharedPreferences.Editor editor = prefs(context).edit()
                .putLong(ELAPSED_BASE_MS, Math.max(0L, elapsedMs))
                .putLong(RUNNING_STARTED_WALL, wallNow)
                .putLong(RUNNING_STARTED_ELAPSED, SystemClock.elapsedRealtime())
                .putInt(RUNNING_BOOT_COUNT, bootCount(context))
                .putLong(LAST_CHECKPOINT_WALL, wallNow)
                .putFloat(OUTPUT_KJ, Math.max(0f, outputKilojoules))
                .putFloat(DISTANCE_MI, Math.max(0f, distanceMiles));
        if (synchronous) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    static Session end(Context context) {
        Session current = read(context);
        long endedAt = System.currentTimeMillis();
        prefs(context).edit()
                .putBoolean(ACTIVE, false)
                .putBoolean(PAUSED, false)
                .putLong(LAST_DURATION_MS, current.elapsedMs)
                .putFloat(LAST_OUTPUT_KJ, current.outputKilojoules)
                .putFloat(LAST_DISTANCE_MI, current.distanceMiles)
                .putLong(LAST_ENDED_AT, endedAt)
                .commit();
        return current;
    }

    static LastRide lastRide(Context context) {
        SharedPreferences preferences = prefs(context);
        long endedAt = preferences.getLong(LAST_ENDED_AT, 0L);
        return new LastRide(endedAt > 0L, endedAt,
                preferences.getLong(LAST_DURATION_MS, 0L),
                Math.max(0f, preferences.getFloat(LAST_OUTPUT_KJ, 0f)),
                Math.max(0f, preferences.getFloat(LAST_DISTANCE_MI, 0f)));
    }

    static JSONObject exportLastRide(Context context) throws JSONException {
        LastRide ride = lastRide(context);
        JSONObject result = new JSONObject();
        result.put("available", ride.available);
        if (ride.available) {
            result.put("endedAt", ride.endedAt);
            result.put("elapsedMs", ride.elapsedMs);
            result.put("outputKilojoules", ride.outputKilojoules);
            result.put("distanceMiles", ride.distanceMiles);
        }
        return result;
    }

    static void importLastRide(Context context, JSONObject source) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .remove(LAST_ENDED_AT)
                .remove(LAST_DURATION_MS)
                .remove(LAST_OUTPUT_KJ)
                .remove(LAST_DISTANCE_MI);
        if (source != null && source.optBoolean("available", false)) {
            long endedAt = Math.max(0L, source.optLong("endedAt", 0L));
            long elapsed = Math.max(0L, Math.min(
                    24L * 60L * 60L * 1000L, source.optLong("elapsedMs", 0L)));
            double output = source.optDouble("outputKilojoules", 0d);
            double distance = source.optDouble("distanceMiles", 0d);
            if (endedAt > 0L && !Double.isNaN(output) && !Double.isInfinite(output)
                    && !Double.isNaN(distance) && !Double.isInfinite(distance)) {
                editor.putLong(LAST_ENDED_AT, endedAt)
                        .putLong(LAST_DURATION_MS, elapsed)
                        .putFloat(LAST_OUTPUT_KJ, (float) Math.max(0d, Math.min(10000d, output)))
                        .putFloat(LAST_DISTANCE_MI,
                                (float) Math.max(0d, Math.min(1000d, distance)));
            }
        }
        editor.commit();
    }

    private static int bootCount(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT);
        } catch (Settings.SettingNotFoundException exception) {
            return -1;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static final class Session {
        final boolean active;
        final boolean paused;
        final String backend;
        final long id;
        final long elapsedMs;
        final float outputKilojoules;
        final float distanceMiles;

        Session(boolean active, boolean paused, String backend, long id, long elapsedMs,
                float outputKilojoules, float distanceMiles) {
            this.active = active;
            this.paused = paused;
            this.backend = backend;
            this.id = id;
            this.elapsedMs = elapsedMs;
            this.outputKilojoules = outputKilojoules;
            this.distanceMiles = distanceMiles;
        }
    }

    static final class LastRide {
        final boolean available;
        final long endedAt;
        final long elapsedMs;
        final float outputKilojoules;
        final float distanceMiles;

        LastRide(boolean available, long endedAt, long elapsedMs,
                 float outputKilojoules, float distanceMiles) {
            this.available = available;
            this.endedAt = endedAt;
            this.elapsedMs = elapsedMs;
            this.outputKilojoules = outputKilojoules;
            this.distanceMiles = distanceMiles;
        }
    }
}
