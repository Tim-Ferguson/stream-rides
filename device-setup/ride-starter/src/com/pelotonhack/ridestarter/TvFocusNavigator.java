package com.pelotonhack.ridestarter;

import java.util.List;

final class TvFocusNavigator {
    static final int UP = 0;
    static final int RIGHT = 1;
    static final int DOWN = 2;
    static final int LEFT = 3;

    static final class Bounds {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int centerX() {
            return left + (right - left) / 2;
        }

        int centerY() {
            return top + (bottom - top) / 2;
        }

        boolean isEmpty() {
            return right <= left || bottom <= top;
        }
    }

    private TvFocusNavigator() {
    }

    static int findNext(Bounds source, List<Bounds> candidates, int direction) {
        if (source == null || source.isEmpty() || candidates == null) {
            return -1;
        }
        int bestIndex = -1;
        long bestScore = Long.MAX_VALUE;
        for (int index = 0; index < candidates.size(); index++) {
            Bounds candidate = candidates.get(index);
            if (candidate == null || candidate.isEmpty()
                    || !isInDirection(source, candidate, direction)) {
                continue;
            }
            long primary = primaryDistance(source, candidate, direction);
            long secondary = secondaryDistance(source, candidate, direction);
            long score = primary * primary * 13L + secondary * secondary;
            if (!overlapsBeam(source, candidate, direction)) {
                score += 1_000_000_000_000L;
            }
            if (score < bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static boolean isInDirection(Bounds source, Bounds candidate, int direction) {
        switch (direction) {
            case UP:
                return candidate.centerY() < source.centerY();
            case RIGHT:
                return candidate.centerX() > source.centerX();
            case DOWN:
                return candidate.centerY() > source.centerY();
            case LEFT:
                return candidate.centerX() < source.centerX();
            default:
                return false;
        }
    }

    private static long primaryDistance(Bounds source, Bounds candidate, int direction) {
        switch (direction) {
            case UP:
                return Math.max(1, source.centerY() - candidate.centerY());
            case RIGHT:
                return Math.max(1, candidate.centerX() - source.centerX());
            case DOWN:
                return Math.max(1, candidate.centerY() - source.centerY());
            case LEFT:
                return Math.max(1, source.centerX() - candidate.centerX());
            default:
                return Integer.MAX_VALUE;
        }
    }

    private static long secondaryDistance(Bounds source, Bounds candidate, int direction) {
        if (direction == LEFT || direction == RIGHT) {
            return Math.abs((long) candidate.centerY() - source.centerY());
        }
        return Math.abs((long) candidate.centerX() - source.centerX());
    }

    private static boolean overlapsBeam(Bounds source, Bounds candidate, int direction) {
        if (direction == LEFT || direction == RIGHT) {
            return candidate.bottom > source.top && candidate.top < source.bottom;
        }
        return candidate.right > source.left && candidate.left < source.right;
    }
}
