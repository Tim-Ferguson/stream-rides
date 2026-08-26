package com.pelotonhack.ridestarter;

public final class SensorHealthTest {
    public static void main(String[] args) {
        assertState(SensorHealth.State.OVERLAY_DISABLED,
                SensorHealth.evaluate(false, true, true, true),
                "disabled overlay takes precedence");
        assertState(SensorHealth.State.READY,
                SensorHealth.evaluate(true, true, true, true),
                "fresh connected sample is ready");
        assertState(SensorHealth.State.CONNECTING,
                SensorHealth.evaluate(true, false, false, false),
                "disconnected service is connecting");
        assertState(SensorHealth.State.CONNECTING,
                SensorHealth.evaluate(true, false, true, true),
                "fresh cannot override a disconnected service");
        assertState(SensorHealth.State.WAITING,
                SensorHealth.evaluate(true, true, false, false),
                "connected service without a sample is waiting");
        assertState(SensorHealth.State.STALE,
                SensorHealth.evaluate(true, true, true, false),
                "old sample is stale");
        System.out.println("SensorHealthTest: PASS");
    }

    private static void assertState(SensorHealth.State expected,
                                    SensorHealth.State actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }
}
