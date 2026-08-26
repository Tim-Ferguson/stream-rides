package com.pelotonhack.ridestarter;

final class ZwiftBlePayload {
    private static final long MAX_CADENCE_RPM = 250L;
    private static final long MAX_SAMPLE_GAP_MS = 5000L;

    private ZwiftBlePayload() {
    }

    static byte[] cyclingPowerMeasurement(float watts, boolean fresh) {
        int instantaneousPower = fresh ? Math.round(watts) : 0;
        instantaneousPower = clamp(instantaneousPower, 0, Short.MAX_VALUE);
        return new byte[] {
                0, 0,
                (byte) (instantaneousPower & 0xff),
                (byte) ((instantaneousPower >>> 8) & 0xff)
        };
    }

    static byte[] cyclingPowerFeature() {
        // Bits 20-21 = 0b01: this represents one complete, non-distributed sensor.
        return new byte[] {0, 0, 16, 0};
    }

    static byte[] cyclingPowerSensorLocation() {
        // 0x00 is the Bluetooth SIG "Other" location for a whole-bike source.
        return new byte[] {0};
    }

    static byte[] cscFeature() {
        // Bit 1 declares crank-revolution data support.
        return new byte[] {2, 0};
    }

    static final class CrankTracker {
        private long lastUpdateMs = -1L;
        private double partialRevolutions;
        private int cumulativeRevolutions;
        private int lastEventTime;

        synchronized byte[] measurement(long cadenceRpm, long nowMs, boolean fresh) {
            long boundedNow = Math.max(0L, nowMs);
            if (lastUpdateMs < 0L) {
                lastUpdateMs = boundedNow;
                lastEventTime = eventTicks(boundedNow);
            } else {
                long elapsedMs = Math.max(0L,
                        Math.min(MAX_SAMPLE_GAP_MS, boundedNow - lastUpdateMs));
                lastUpdateMs = boundedNow;
                long boundedCadence = fresh
                        ? Math.max(0L, Math.min(MAX_CADENCE_RPM, cadenceRpm)) : 0L;
                if (boundedCadence > 0L && elapsedMs > 0L) {
                    partialRevolutions += boundedCadence * elapsedMs / 60000.0;
                    int completed = (int) Math.floor(partialRevolutions);
                    if (completed > 0) {
                        cumulativeRevolutions = (cumulativeRevolutions + completed) & 0xffff;
                        partialRevolutions -= completed;
                        long sinceLastEventMs = Math.round(
                                partialRevolutions * 60000.0 / boundedCadence);
                        lastEventTime = eventTicks(Math.max(0L, boundedNow - sinceLastEventMs));
                    }
                }
            }

            return new byte[] {
                    2,
                    (byte) (cumulativeRevolutions & 0xff),
                    (byte) ((cumulativeRevolutions >>> 8) & 0xff),
                    (byte) (lastEventTime & 0xff),
                    (byte) ((lastEventTime >>> 8) & 0xff)
            };
        }

        private static int eventTicks(long elapsedRealtimeMs) {
            return (int) ((elapsedRealtimeMs * 1024L / 1000L) & 0xffffL);
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
