package com.pelotonhack.ridestarter;

public final class DragGesturePolicyTest {
    public static void main(String[] args) {
        keepsTapWithinThreshold();
        recognizesEitherDragAxis();
        System.out.println("DragGesturePolicyTest: PASS");
    }

    private static void keepsTapWithinThreshold() {
        assertTrue(!DragGesturePolicy.hasMoved(100, 100, 108, 92, 8),
                "movement at threshold remains a click");
    }

    private static void recognizesEitherDragAxis() {
        assertTrue(DragGesturePolicy.hasMoved(100, 100, 109, 100, 8),
                "horizontal drag");
        assertTrue(DragGesturePolicy.hasMoved(100, 100, 100, 91, 8),
                "vertical drag");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
