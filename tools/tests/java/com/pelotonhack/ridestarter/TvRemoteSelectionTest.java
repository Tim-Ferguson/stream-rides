package com.pelotonhack.ridestarter;

public final class TvRemoteSelectionTest {
    public static void main(String[] args) {
        rejectsSelectionBeforeDirection();
        acceptsMatchingSelectionOnceArmed();
        rejectsDifferentProvider();
        rejectsDifferentWindow();
        rejectsExpiredSelection();
        requiresSecondConfirmationForOpaqueTarget();
        expiresOpaqueConfirmationQuickly();
        clearDisarmsSelection();
        System.out.println("TvRemoteSelectionTest: PASS");
    }

    private static void rejectsSelectionBeforeDirection() {
        TvRemoteSelection selection = new TvRemoteSelection();
        assertDecision(TvRemoteSelection.Decision.REJECTED,
                selection.consume(TvRemotePackages.PARAMOUNT, 7, 1000),
                "unarmed selection");
    }

    private static void acceptsMatchingSelectionOnceArmed() {
        TvRemoteSelection selection = new TvRemoteSelection();
        selection.arm(TvRemotePackages.PARAMOUNT, 7, "target", false, 1000);
        TvRemoteSelection.Result result = selection.consume(
                TvRemotePackages.PARAMOUNT, 7, 1001);
        assertDecision(TvRemoteSelection.Decision.ALLOWED, result, "matching provider");
        assertEquals("target", result.targetKey, "target key");
        assertDecision(TvRemoteSelection.Decision.REJECTED,
                selection.consume(TvRemotePackages.PARAMOUNT, 7, 1002),
                "selection is one-shot");
    }

    private static void rejectsDifferentProvider() {
        TvRemoteSelection selection = new TvRemoteSelection();
        selection.arm(TvRemotePackages.PARAMOUNT, 7, "target", false, 1000);
        assertDecision(TvRemoteSelection.Decision.REJECTED,
                selection.consume(TvRemotePackages.MGM, 7, 1001),
                "different provider");
    }

    private static void rejectsDifferentWindow() {
        TvRemoteSelection selection = new TvRemoteSelection();
        selection.arm(TvRemotePackages.PARAMOUNT, 7, "target", false, 1000);
        assertDecision(TvRemoteSelection.Decision.REJECTED,
                selection.consume(TvRemotePackages.PARAMOUNT, 8, 1001),
                "different window");
    }

    private static void rejectsExpiredSelection() {
        TvRemoteSelection selection = new TvRemoteSelection();
        selection.arm(TvRemotePackages.PARAMOUNT, 7, "target", false, 1000);
        assertDecision(TvRemoteSelection.Decision.REJECTED,
                selection.consume(TvRemotePackages.PARAMOUNT, 7,
                        1000 + TvRemoteSelection.ARM_TIMEOUT_MS + 1),
                "expired selection");
    }

    private static void requiresSecondConfirmationForOpaqueTarget() {
        TvRemoteSelection selection = new TvRemoteSelection();
        selection.arm(TvRemotePackages.PEACOCK, 7, null, true, 1000);
        assertDecision(TvRemoteSelection.Decision.CONFIRM_REQUIRED,
                selection.consume(TvRemotePackages.PEACOCK, 7, 1001),
                "first opaque selection");
        assertDecision(TvRemoteSelection.Decision.ALLOWED,
                selection.consume(TvRemotePackages.PEACOCK, 7, 1002),
                "second opaque selection");
        assertDecision(TvRemoteSelection.Decision.REJECTED,
                selection.consume(TvRemotePackages.PEACOCK, 7, 1003),
                "opaque selection is one-shot");
    }

    private static void expiresOpaqueConfirmationQuickly() {
        TvRemoteSelection selection = new TvRemoteSelection();
        selection.arm(TvRemotePackages.PEACOCK, 7, null, true, 1000);
        selection.consume(TvRemotePackages.PEACOCK, 7, 1001);
        assertDecision(TvRemoteSelection.Decision.REJECTED,
                selection.consume(TvRemotePackages.PEACOCK, 7,
                        1001 + TvRemoteSelection.CONFIRM_TIMEOUT_MS + 1),
                "expired opaque confirmation");
    }

    private static void clearDisarmsSelection() {
        TvRemoteSelection selection = new TvRemoteSelection();
        selection.arm(TvRemotePackages.PARAMOUNT, 7, "target", false, 1000);
        selection.clear();
        assertDecision(TvRemoteSelection.Decision.REJECTED,
                selection.consume(TvRemotePackages.PARAMOUNT, 7, 1001),
                "cleared selection");
    }

    private static void assertDecision(TvRemoteSelection.Decision expected,
                                       TvRemoteSelection.Result result, String message) {
        if (expected != result.decision) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + result.decision);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

}
