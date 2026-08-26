package com.pelotonhack.ridestarter;

import java.util.ArrayList;
import java.util.List;

public final class TvFocusNavigatorTest {
    public static void main(String[] args) {
        choosesNearestTargetInDirection();
        prefersAlignedTarget();
        rejectsOppositeDirection();
        limitsSupportedPackages();
        System.out.println("TvFocusNavigatorTest: PASS");
    }

    private static void choosesNearestTargetInDirection() {
        TvFocusNavigator.Bounds source = bounds(100, 100, 200, 200);
        List<TvFocusNavigator.Bounds> candidates = new ArrayList<TvFocusNavigator.Bounds>();
        candidates.add(bounds(220, 100, 320, 200));
        candidates.add(bounds(400, 100, 500, 200));
        assertEquals(0, TvFocusNavigator.findNext(source, candidates,
                TvFocusNavigator.RIGHT), "nearest right target");
    }

    private static void prefersAlignedTarget() {
        TvFocusNavigator.Bounds source = bounds(100, 100, 200, 200);
        List<TvFocusNavigator.Bounds> candidates = new ArrayList<TvFocusNavigator.Bounds>();
        candidates.add(bounds(205, 500, 305, 600));
        candidates.add(bounds(260, 110, 360, 210));
        assertEquals(1, TvFocusNavigator.findNext(source, candidates,
                TvFocusNavigator.RIGHT), "aligned right target");
    }

    private static void rejectsOppositeDirection() {
        TvFocusNavigator.Bounds source = bounds(100, 100, 200, 200);
        List<TvFocusNavigator.Bounds> candidates = new ArrayList<TvFocusNavigator.Bounds>();
        candidates.add(bounds(0, 100, 90, 200));
        assertEquals(-1, TvFocusNavigator.findNext(source, candidates,
                TvFocusNavigator.RIGHT), "opposite target");
    }

    private static void limitsSupportedPackages() {
        assertTrue(TvRemotePackages.supports(TvRemotePackages.PEACOCK), "Peacock supported");
        assertTrue(TvRemotePackages.supports(TvRemotePackages.PARAMOUNT), "Paramount supported");
        assertTrue(TvRemotePackages.supports(TvRemotePackages.MGM), "MGM supported");
        assertTrue(!TvRemotePackages.supports("com.example.unrelated"),
                "unrelated package excluded");
        assertTrue(TvRemotePackages.supportsVariant(TvRemotePackages.PEACOCK, true),
                "Peacock TV variant supported");
        assertTrue(!TvRemotePackages.supportsVariant(TvRemotePackages.PEACOCK, false),
                "Peacock mobile variant excluded");
        assertTrue(!TvRemotePackages.supportsVariant("com.example.unrelated", true),
                "unrelated TV variant excluded");
        assertTrue(TvRemotePackages.usesVirtualDpad(TvRemotePackages.PEACOCK),
                "Peacock virtual D-pad");
        assertTrue(!TvRemotePackages.usesVirtualDpad(TvRemotePackages.MGM),
                "MGM semantic navigation");
    }

    private static TvFocusNavigator.Bounds bounds(int left, int top, int right, int bottom) {
        return new TvFocusNavigator.Bounds(left, top, right, bottom);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
