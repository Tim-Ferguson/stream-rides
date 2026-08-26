package com.pelotonhack.ridestarter;

public final class BikeAppAccessibilityPolicyTest {
    public static void main(String[] args) {
        rejectsProviderAndMissingPackages();
        enforcesOneScanWideNodeBudget();
        enforcesElapsedTimeBudget();
        System.out.println("BikeAppAccessibilityPolicyTest: PASS");
    }

    private static void rejectsProviderAndMissingPackages() {
        assertTrue(BikeAppAccessibilityPolicy.allowsPackage("com.peloton.activity"),
                "target package allowed");
        assertTrue(!BikeAppAccessibilityPolicy.allowsPackage("com.peacocktv.peacockandroid"),
                "provider package rejected");
        assertTrue(!BikeAppAccessibilityPolicy.allowsPackage(null),
                "missing package rejected");
    }

    private static void enforcesOneScanWideNodeBudget() {
        BikeAppAccessibilityPolicy.TraversalBudget budget =
                new BikeAppAccessibilityPolicy.TraversalBudget(3, 25, 100);
        assertTrue(budget.enterNode(100), "first node");
        assertTrue(budget.enterNode(101), "second node");
        assertTrue(budget.enterNode(102), "third node");
        assertTrue(!budget.enterNode(103), "fourth node rejected");
        assertEquals(3, budget.visitedNodes(), "shared scan count");
    }

    private static void enforcesElapsedTimeBudget() {
        BikeAppAccessibilityPolicy.TraversalBudget budget =
                new BikeAppAccessibilityPolicy.TraversalBudget(256, 25, 100);
        assertTrue(budget.enterNode(125), "deadline is inclusive");
        assertTrue(!budget.enterNode(126), "late node rejected");
        assertEquals(1, budget.visitedNodes(), "late node not counted");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
