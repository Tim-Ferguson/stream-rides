package com.pelotonhack.ridestarter;

import java.util.Locale;

enum GameMode {
    CADENCE_DRAG(
            "Cadence Drag",
            "Launch hard. Cadence and watts drive a 400 m sprint.",
            "400 M TIME",
            true),
    CADENCE_FLYER(
            "Cadence Flyer",
            "Cadence surges add lift. Thread the gates for 45 seconds.",
            "FLIGHT SCORE",
            false),
    RESISTANCE_RIDGE(
            "Resistance Ridge",
            "Match the terrain's resistance while powering over the ridge.",
            "CLIMB SCORE",
            false),
    POWER_REACTOR(
            "Power Reactor",
            "Hold changing watt targets to keep the reactor stable.",
            "REACTOR SCORE",
            false),
    RHYTHM_RUNNER(
            "Rhythm Runner",
            "Match the cadence lane and build a consistency combo.",
            "RHYTHM SCORE",
            false),
    ORBITAL_COURIER(
            "Orbital Courier",
            "Cadence drives the ship; resistance selects a safe orbit.",
            "DELIVERY SCORE",
            false);

    final String title;
    final String subtitle;
    final String scoreLabel;
    final boolean lowerScoreIsBetter;

    GameMode(String title, String subtitle, String scoreLabel,
             boolean lowerScoreIsBetter) {
        this.title = title;
        this.subtitle = subtitle;
        this.scoreLabel = scoreLabel;
        this.lowerScoreIsBetter = lowerScoreIsBetter;
    }

    String preferenceKey() {
        return name().toLowerCase(Locale.US);
    }

    String formatScore(float score) {
        if (Float.isNaN(score)) {
            return "--";
        }
        if (lowerScoreIsBetter) {
            return String.format(Locale.US, "%.2f s", score);
        }
        return String.format(Locale.US, "%d", Math.round(score));
    }
}
