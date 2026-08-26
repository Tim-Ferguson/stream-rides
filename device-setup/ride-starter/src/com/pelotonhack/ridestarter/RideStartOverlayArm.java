package com.pelotonhack.ridestarter;

final class RideStartOverlayArm {
    private final long windowMs;
    private volatile long armedUntilMs;

    RideStartOverlayArm(long windowMs) {
        if (windowMs < 1L) {
            throw new IllegalArgumentException("Arm window must be positive");
        }
        this.windowMs = windowMs;
    }

    void arm(long nowMs) {
        armedUntilMs = nowMs + windowMs;
    }

    boolean isArmed(long nowMs) {
        long currentDeadline = armedUntilMs;
        if (currentDeadline != 0L && nowMs <= currentDeadline) {
            return true;
        }
        armedUntilMs = 0L;
        return false;
    }

    void disarm() {
        armedUntilMs = 0L;
    }
}
