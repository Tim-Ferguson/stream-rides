package com.pelotonhack.ridestarter;

public final class RideStartOverlayArmTest {
    public static void main(String[] args) {
        expiresAtWindowBoundary();
        teardownStyleResetDisarmsImmediately();
        System.out.println("RideStartOverlayArmTest: PASS");
    }

    private static void expiresAtWindowBoundary() {
        RideStartOverlayArm arm = new RideStartOverlayArm(100);
        arm.arm(1000);
        assertTrue(arm.isArmed(1100), "deadline is inclusive");
        assertTrue(!arm.isArmed(1101), "arm expires after deadline");
        assertTrue(!arm.isArmed(1050), "expired state remains cleared");
    }

    private static void teardownStyleResetDisarmsImmediately() {
        RideStartOverlayArm arm = new RideStartOverlayArm(100);
        arm.arm(1000);
        arm.disarm();
        assertTrue(!arm.isArmed(1001), "explicit reset disarms");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
