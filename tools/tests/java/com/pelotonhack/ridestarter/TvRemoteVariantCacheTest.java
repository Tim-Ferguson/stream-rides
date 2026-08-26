package com.pelotonhack.ridestarter;

public final class TvRemoteVariantCacheTest {
    public static void main(String[] args) {
        excludesTouchNativeVariants();
        cachesOnlyUntilExpiry();
        retriesProbeFailures();
        System.out.println("TvRemoteVariantCacheTest: PASS");
    }

    private static void excludesTouchNativeVariants() {
        TvRemoteVariantCache cache = new TvRemoteVariantCache();
        CountingProbe probe = new CountingProbe(false);
        assertTrue(!cache.isTvVariant(TvRemotePackages.PEACOCK, 100, probe),
                "mobile Peacock excluded");
        assertTrue(!cache.isTvVariant("com.example.unrelated", 100, probe),
                "unrelated app excluded");
        assertEquals(1, probe.calls, "unrelated package does not reach probe");
    }

    private static void cachesOnlyUntilExpiry() {
        TvRemoteVariantCache cache = new TvRemoteVariantCache();
        CountingProbe probe = new CountingProbe(true);
        assertTrue(cache.isTvVariant(TvRemotePackages.MGM, 100, probe),
                "TV variant accepted");
        assertTrue(cache.isTvVariant(TvRemotePackages.MGM, 29_999, probe),
                "TV result reused before expiry");
        assertEquals(1, probe.calls, "fresh result is cached");
        assertTrue(cache.isTvVariant(TvRemotePackages.MGM, 30_100, probe),
                "TV result refreshed at expiry");
        assertEquals(2, probe.calls, "expired result is probed again");
    }

    private static void retriesProbeFailures() {
        TvRemoteVariantCache cache = new TvRemoteVariantCache();
        CountingProbe probe = new CountingProbe(true);
        probe.failNext = true;
        assertTrue(!cache.isTvVariant(TvRemotePackages.PARAMOUNT, 100, probe),
                "probe failure fails closed");
        assertTrue(cache.isTvVariant(TvRemotePackages.PARAMOUNT, 101, probe),
                "probe failure is retried");
        assertEquals(2, probe.calls, "failure is not cached");
    }

    private static final class CountingProbe implements TvRemoteVariantCache.Probe {
        final boolean result;
        int calls;
        boolean failNext;

        CountingProbe(boolean result) {
            this.result = result;
        }

        @Override
        public boolean hasLeanbackLauncher(String packageName) {
            calls += 1;
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("temporary resolver failure");
            }
            return result;
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but was "
                    + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
