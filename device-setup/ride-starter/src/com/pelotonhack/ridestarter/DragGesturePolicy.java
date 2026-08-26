package com.pelotonhack.ridestarter;

final class DragGesturePolicy {
    private DragGesturePolicy() {
    }

    static boolean hasMoved(float startX, float startY, float currentX, float currentY,
                            int thresholdPx) {
        if (thresholdPx < 0) {
            throw new IllegalArgumentException("Drag threshold cannot be negative");
        }
        return Math.abs(currentX - startX) > thresholdPx
                || Math.abs(currentY - startY) > thresholdPx;
    }
}
