package com.pelotonhack.ridestarter;

public final class ZwiftBlePayloadTest {
    public static void main(String[] args) {
        encodesPowerLittleEndian();
        clampsInvalidPower();
        declaresOnlyImplementedFeatures();
        encodesCadenceRevolutionsAndEventTime();
        ignoresStaleCadenceAndLargeGaps();
        System.out.println("ZwiftBlePayloadTest: PASS");
    }

    private static void encodesPowerLittleEndian() {
        assertBytes(new int[] {0, 0, 246, 0},
                ZwiftBlePayload.cyclingPowerMeasurement(245.6f, true), "power");
        assertBytes(new int[] {0, 0, 0, 0},
                ZwiftBlePayload.cyclingPowerMeasurement(245.6f, false), "stale power");
    }

    private static void clampsInvalidPower() {
        assertBytes(new int[] {0, 0, 0, 0},
                ZwiftBlePayload.cyclingPowerMeasurement(-20f, true), "negative power");
        assertBytes(new int[] {0, 0, 255, 127},
                ZwiftBlePayload.cyclingPowerMeasurement(50000f, true), "high power");
    }

    private static void declaresOnlyImplementedFeatures() {
        assertBytes(new int[] {0, 0, 16, 0},
                ZwiftBlePayload.cyclingPowerFeature(), "power feature");
        assertBytes(new int[] {0},
                ZwiftBlePayload.cyclingPowerSensorLocation(), "power sensor location");
        assertBytes(new int[] {2, 0},
                ZwiftBlePayload.cscFeature(), "cadence feature");
    }

    private static void encodesCadenceRevolutionsAndEventTime() {
        ZwiftBlePayload.CrankTracker tracker = new ZwiftBlePayload.CrankTracker();
        assertBytes(new int[] {2, 0, 0, 0, 4},
                tracker.measurement(60L, 1000L, true), "initial cadence");
        assertBytes(new int[] {2, 1, 0, 0, 8},
                tracker.measurement(60L, 2000L, true), "one revolution");
        assertBytes(new int[] {2, 1, 0, 0, 8},
                tracker.measurement(60L, 2500L, true), "partial revolution");
        assertBytes(new int[] {2, 2, 0, 0, 12},
                tracker.measurement(60L, 3000L, true), "second revolution");
    }

    private static void ignoresStaleCadenceAndLargeGaps() {
        ZwiftBlePayload.CrankTracker tracker = new ZwiftBlePayload.CrankTracker();
        tracker.measurement(120L, 0L, true);
        assertUnsignedShort(0, tracker.measurement(120L, 1000L, false), 1,
                "stale cadence");
        assertUnsignedShort(10, tracker.measurement(120L, 11000L, true), 1,
                "bounded gap");
    }

    private static void assertUnsignedShort(int expected, byte[] actual, int offset,
                                            String label) {
        int value = (actual[offset] & 0xff) | ((actual[offset + 1] & 0xff) << 8);
        if (value != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + value);
        }
    }

    private static void assertBytes(int[] expected, byte[] actual, String label) {
        if (actual.length != expected.length) {
            throw new AssertionError(label + ": expected length " + expected.length
                    + ", got " + actual.length);
        }
        for (int index = 0; index < expected.length; index++) {
            int value = actual[index] & 0xff;
            if (value != expected[index]) {
                throw new AssertionError(label + " byte " + index + ": expected "
                        + expected[index] + ", got " + value);
            }
        }
    }
}
