package com.pelotonhack.ridestarter;

final class TvRemoteServiceState {
    static final long START_TIMEOUT_MS = 10000;

    private long enabledSinceMs = -1;

    State evaluate(boolean enabled, boolean connected, long nowMs) {
        if (connected) {
            enabledSinceMs = -1;
            return State.READY;
        }
        if (!enabled) {
            enabledSinceMs = -1;
            return State.OFF;
        }
        if (enabledSinceMs < 0 || nowMs < enabledSinceMs) {
            enabledSinceMs = nowMs;
        }
        return nowMs - enabledSinceMs >= START_TIMEOUT_MS
                ? State.NOT_RUNNING : State.STARTING;
    }

    enum State {
        OFF,
        STARTING,
        READY,
        NOT_RUNNING
    }
}
