package com.pelotonhack.ridestarter;

final class RideAccumulator {
    private static final long MAX_INTEGRATION_GAP_MS = 5000L;

    private long sessionId;
    private long lastTickAt;
    private float outputKilojoules;
    private float distanceMiles;

    void reset(long nextSessionId, long now, float output, float distance) {
        sessionId = nextSessionId;
        lastTickAt = now;
        outputKilojoules = Math.max(0f, output);
        distanceMiles = Math.max(0f, distance);
    }

    void tick(long now, boolean paused, boolean fresh, float watts, float speedMph) {
        long deltaMs = Math.max(0L, now - lastTickAt);
        if (!paused && fresh && deltaMs > 0L && deltaMs <= MAX_INTEGRATION_GAP_MS) {
            outputKilojoules += Math.max(0f, watts) * deltaMs / 1000000f;
            distanceMiles += Math.max(0f, speedMph) * deltaMs / 3600000f;
        }
        lastTickAt = now;
    }

    void clear() {
        reset(0L, 0L, 0f, 0f);
    }

    long sessionId() {
        return sessionId;
    }

    float outputKilojoules() {
        return outputKilojoules;
    }

    float distanceMiles() {
        return distanceMiles;
    }
}
