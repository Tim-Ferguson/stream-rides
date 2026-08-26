package com.pelotonhack.ridestarter;

final class SensorHealth {
    enum State {
        OVERLAY_DISABLED,
        READY,
        CONNECTING,
        WAITING,
        STALE
    }

    private SensorHealth() {
    }

    static State evaluate(boolean overlayReady, boolean connected,
                          boolean hasSample, boolean fresh) {
        if (!overlayReady) {
            return State.OVERLAY_DISABLED;
        }
        if (connected && fresh) {
            return State.READY;
        }
        if (!connected) {
            return State.CONNECTING;
        }
        return hasSample ? State.STALE : State.WAITING;
    }
}
