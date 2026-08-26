package com.pelotonhack.ridestarter;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

final class GameProgressStore {
    enum Upgrade {
        ENGINE("Engine"),
        GRIP("Grip"),
        AERO("Aero");

        final String label;

        Upgrade(String label) {
            this.label = label;
        }
    }

    private static final String PREFS = "saro_games_progress_v1";
    private static final String CREDITS = "credits";
    private static final String SESSIONS = "sessions";
    private static final int MAX_UPGRADE_LEVEL = 5;

    private final SharedPreferences preferences;

    GameProgressStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    int credits() {
        return preferences.getInt(CREDITS, 0);
    }

    int sessions() {
        return preferences.getInt(SESSIONS, 0);
    }

    float best(GameMode mode) {
        return preferences.getFloat("best_" + mode.preferenceKey(), Float.NaN);
    }

    float last(GameMode mode) {
        return preferences.getFloat("last_" + mode.preferenceKey(), Float.NaN);
    }

    boolean recordResult(GameMode mode, float score, int rewardCredits) {
        if (Float.isNaN(score) || Float.isInfinite(score) || score < 0f) {
            return false;
        }
        float best = best(mode);
        boolean improved = Float.isNaN(best)
                || (mode.lowerScoreIsBetter ? score < best : score > best);
        SharedPreferences.Editor editor = preferences.edit()
                .putFloat("last_" + mode.preferenceKey(), score)
                .putInt(CREDITS, Math.max(0, credits() + Math.max(0, rewardCredits)))
                .putInt(SESSIONS, sessions() + 1);
        if (improved) {
            editor.putFloat("best_" + mode.preferenceKey(), score);
        }
        editor.apply();
        return improved;
    }

    int upgradeLevel(Upgrade upgrade) {
        return preferences.getInt("upgrade_" + upgrade.name().toLowerCase(Locale.US), 0);
    }

    int upgradeCost(Upgrade upgrade) {
        int level = upgradeLevel(upgrade);
        return level >= MAX_UPGRADE_LEVEL ? 0 : 35 + level * 30;
    }

    boolean buyUpgrade(Upgrade upgrade) {
        int level = upgradeLevel(upgrade);
        int cost = upgradeCost(upgrade);
        if (level >= MAX_UPGRADE_LEVEL || credits() < cost) {
            return false;
        }
        preferences.edit()
                .putInt(CREDITS, credits() - cost)
                .putInt("upgrade_" + upgrade.name().toLowerCase(Locale.US), level + 1)
                .apply();
        return true;
    }

    boolean isMaxed(Upgrade upgrade) {
        return upgradeLevel(upgrade) >= MAX_UPGRADE_LEVEL;
    }

    JSONObject exportConfiguration() throws JSONException {
        JSONObject result = new JSONObject();
        result.put(CREDITS, credits());
        result.put(SESSIONS, sessions());
        JSONObject scores = new JSONObject();
        for (GameMode mode : GameMode.values()) {
            float best = best(mode);
            float last = last(mode);
            JSONObject modeScores = new JSONObject();
            if (!Float.isNaN(best)) {
                modeScores.put("best", best);
            }
            if (!Float.isNaN(last)) {
                modeScores.put("last", last);
            }
            scores.put(mode.preferenceKey(), modeScores);
        }
        result.put("scores", scores);

        JSONObject upgrades = new JSONObject();
        for (Upgrade upgrade : Upgrade.values()) {
            upgrades.put(upgrade.name().toLowerCase(Locale.US), upgradeLevel(upgrade));
        }
        result.put("upgrades", upgrades);
        return result;
    }

    void importConfiguration(JSONObject configuration) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        if (configuration == null) {
            editor.commit();
            return;
        }
        editor.putInt(CREDITS, clamp(configuration.optInt(CREDITS, 0), 0, 1000000))
                .putInt(SESSIONS, clamp(configuration.optInt(SESSIONS, 0), 0, 1000000));

        JSONObject scores = configuration.optJSONObject("scores");
        if (scores != null) {
            for (GameMode mode : GameMode.values()) {
                JSONObject modeScores = scores.optJSONObject(mode.preferenceKey());
                if (modeScores == null) {
                    continue;
                }
                putScore(editor, "best_" + mode.preferenceKey(), modeScores, "best");
                putScore(editor, "last_" + mode.preferenceKey(), modeScores, "last");
            }
        }

        JSONObject upgrades = configuration.optJSONObject("upgrades");
        if (upgrades != null) {
            for (Upgrade upgrade : Upgrade.values()) {
                String key = upgrade.name().toLowerCase(Locale.US);
                editor.putInt("upgrade_" + key,
                        clamp(upgrades.optInt(key, 0), 0, MAX_UPGRADE_LEVEL));
            }
        }
        editor.commit();
    }

    private static void putScore(SharedPreferences.Editor editor, String preferenceKey,
                                 JSONObject source, String sourceKey) {
        double value = source.optDouble(sourceKey, Double.NaN);
        if (!Double.isNaN(value) && !Double.isInfinite(value)
                && value >= 0d && value <= 100000000d) {
            editor.putFloat(preferenceKey, (float) value);
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
