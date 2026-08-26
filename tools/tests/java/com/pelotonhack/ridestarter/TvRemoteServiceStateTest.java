package com.pelotonhack.ridestarter;

public final class TvRemoteServiceStateTest {
    public static void main(String[] args) {
        reportsOffWhenDisabled();
        reportsReadyWhenConnected();
        reportsDeadEnabledServiceAfterTimeout();
        resetsAfterDisable();
        System.out.println("TvRemoteServiceStateTest: PASS");
    }

    private static void reportsOffWhenDisabled() {
        assertEquals(TvRemoteServiceState.State.OFF,
                new TvRemoteServiceState().evaluate(false, false, 1000), "disabled");
    }

    private static void reportsReadyWhenConnected() {
        assertEquals(TvRemoteServiceState.State.READY,
                new TvRemoteServiceState().evaluate(true, true, 1000), "connected");
    }

    private static void reportsDeadEnabledServiceAfterTimeout() {
        TvRemoteServiceState state = new TvRemoteServiceState();
        assertEquals(TvRemoteServiceState.State.STARTING,
                state.evaluate(true, false, 1000), "initial start");
        assertEquals(TvRemoteServiceState.State.NOT_RUNNING,
                state.evaluate(true, false,
                        1000 + TvRemoteServiceState.START_TIMEOUT_MS), "start timeout");
    }

    private static void resetsAfterDisable() {
        TvRemoteServiceState state = new TvRemoteServiceState();
        state.evaluate(true, false, 1000);
        state.evaluate(false, false, 2000);
        assertEquals(TvRemoteServiceState.State.STARTING,
                state.evaluate(true, false, 3000), "reenabled start");
    }

    private static void assertEquals(TvRemoteServiceState.State expected,
                                     TvRemoteServiceState.State actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }
}
