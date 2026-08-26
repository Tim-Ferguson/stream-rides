package com.pelotonhack.ridestarter;

public final class TvRemoteTargetKeyTest {
    public static void main(String[] args) {
        sameSnapshotIsStable();
        dynamicTargetChangesInvalidateKey();
        ancestorChangesInvalidateTarget();
        labelsAreNotStoredVerbatim();
        System.out.println("TvRemoteTargetKeyTest: PASS");
    }

    private static void sameSnapshotIsStable() {
        assertEquals(node("Sign In", 16, 100), node("Sign In", 16, 100),
                "same snapshot");
    }

    private static void dynamicTargetChangesInvalidateKey() {
        assertNotEquals(node("Sign In", 16, 100), node("Subscribe", 16, 100),
                "label change");
        assertNotEquals(node("Sign In", 16, 100), node("Sign In", 32, 100),
                "action change");
        assertNotEquals(node("Sign In", 16, 100), node("Sign In", 16, 101),
                "source change");
    }

    private static void ancestorChangesInvalidateTarget() {
        String focused = node("Sign In", 16, 100);
        String first = TvRemoteTargetKey.target(focused, node("Account", 16, 200));
        String second = TvRemoteTargetKey.target(focused, node("Subscribe", 16, 201));
        assertNotEquals(first, second, "clickable ancestor change");
    }

    private static void labelsAreNotStoredVerbatim() {
        String value = node("private account label", 16, 100);
        assertTrue(!value.contains("private account label"), "plaintext label excluded");
    }

    private static String node(String label, int actions, int sourceHash) {
        return TvRemoteTargetKey.node(8, sourceHash, "[1,2][3,4]",
                "android.widget.TextView", "provider:id/action", actions,
                true, true, true, true, true, label, null, null);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected equal keys");
        }
    }

    private static void assertNotEquals(String expected, String actual, String message) {
        if (expected.equals(actual)) {
            throw new AssertionError(message + ": expected different keys");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
