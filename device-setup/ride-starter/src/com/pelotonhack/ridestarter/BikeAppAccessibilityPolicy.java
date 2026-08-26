package com.pelotonhack.ridestarter;

final class BikeAppAccessibilityPolicy {
    static final String PACKAGE_NAME = "com.peloton.activity";

    private BikeAppAccessibilityPolicy() {
    }

    static boolean allowsPackage(CharSequence packageName) {
        return packageName != null && PACKAGE_NAME.contentEquals(packageName);
    }

    static final class TraversalBudget {
        private final int maxNodes;
        private final long deadlineMs;
        private int visitedNodes;

        TraversalBudget(int maxNodes, long budgetMs, long startedAtMs) {
            if (maxNodes < 1 || budgetMs < 0L) {
                throw new IllegalArgumentException("Traversal limits must be positive");
            }
            this.maxNodes = maxNodes;
            this.deadlineMs = startedAtMs + budgetMs;
        }

        boolean enterNode(long nowMs) {
            if (!canContinue(nowMs)) {
                return false;
            }
            visitedNodes += 1;
            return true;
        }

        boolean canContinue(long nowMs) {
            return visitedNodes < maxNodes && nowMs <= deadlineMs;
        }

        int visitedNodes() {
            return visitedNodes;
        }
    }
}
