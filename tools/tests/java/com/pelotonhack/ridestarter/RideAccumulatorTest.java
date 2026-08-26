package com.pelotonhack.ridestarter;

public final class RideAccumulatorTest {
    public static void main(String[] args) {
        integratesAcrossIndependentHeartbeats();
        excludesPausedIntervals();
        dropsUnboundedGaps();
        System.out.println("RideAccumulatorTest: PASS");
    }

    private static void integratesAcrossIndependentHeartbeats() {
        RideAccumulator accumulator = new RideAccumulator();
        accumulator.reset(7L, 0L, 0f, 0f);
        for (int second = 1; second <= 10; second++) {
            accumulator.tick(second * 1000L, false, true, 200f, 18f);
        }
        assertNear(2f, accumulator.outputKilojoules(), 0.0001f, "output");
        assertNear(0.05f, accumulator.distanceMiles(), 0.0001f, "distance");
    }

    private static void excludesPausedIntervals() {
        RideAccumulator accumulator = new RideAccumulator();
        accumulator.reset(8L, 0L, 0f, 0f);
        accumulator.tick(1000L, false, true, 150f, 12f);
        accumulator.tick(2000L, true, true, 150f, 12f);
        accumulator.tick(3000L, true, true, 150f, 12f);
        accumulator.tick(4000L, false, true, 150f, 12f);
        assertNear(0.3f, accumulator.outputKilojoules(), 0.0001f, "paused output");
    }

    private static void dropsUnboundedGaps() {
        RideAccumulator accumulator = new RideAccumulator();
        accumulator.reset(9L, 0L, 1f, 0.1f);
        accumulator.tick(6000L, false, true, 300f, 20f);
        assertNear(1f, accumulator.outputKilojoules(), 0.0001f, "gap output");
        accumulator.tick(7000L, false, false, 300f, 20f);
        assertNear(1f, accumulator.outputKilojoules(), 0.0001f, "stale output");
    }

    private static void assertNear(float expected, float actual, float tolerance,
                                   String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
