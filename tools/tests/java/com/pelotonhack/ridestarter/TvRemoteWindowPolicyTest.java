package com.pelotonhack.ridestarter;

public final class TvRemoteWindowPolicyTest {
    public static void main(String[] args) {
        rejectsInactiveWindows();
        higherSystemLayerBeatsActiveApp();
        higherLayerBreaksTies();
        rejectsForegroundSystemWindow();
        System.out.println("TvRemoteWindowPolicyTest: PASS");
    }

    private static void rejectsInactiveWindows() {
        assertTrue(!TvRemoteWindowPolicy.shouldReplace(-1, -1,
                TvRemoteWindowPolicy.rank(false, false), 10), "inactive window");
    }

    private static void higherSystemLayerBeatsActiveApp() {
        int focused = TvRemoteWindowPolicy.rank(false, true);
        int active = TvRemoteWindowPolicy.rank(true, false);
        assertTrue(TvRemoteWindowPolicy.shouldReplace(active, 1, focused, 100),
                "higher focused system layer");
        assertTrue(!TvRemoteWindowPolicy.shouldReplace(focused, 100, active, 1),
                "lower active app blocked");
    }

    private static void higherLayerBreaksTies() {
        int active = TvRemoteWindowPolicy.rank(true, false);
        assertTrue(TvRemoteWindowPolicy.shouldReplace(active, 3, active, 4),
                "higher layer");
    }

    private static void rejectsForegroundSystemWindow() {
        assertTrue(!TvRemoteWindowPolicy.isSupportedForegroundType(3), "system window");
        assertTrue(TvRemoteWindowPolicy.isSupportedForegroundType(
                TvRemoteWindowPolicy.TYPE_APPLICATION), "application window");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
