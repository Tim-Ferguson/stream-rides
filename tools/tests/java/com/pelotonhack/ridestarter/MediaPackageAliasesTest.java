package com.pelotonhack.ridestarter;

public final class MediaPackageAliasesTest {
    public static void main(String[] args) {
        migratesLegacyTvPackages();
        prefersMobileAndRetainsTvFallback();
        preservesUnrelatedPackages();
        System.out.println("MediaPackageAliasesTest: PASS");
    }

    private static void migratesLegacyTvPackages() {
        assertEquals(MediaPackageAliases.MAX_MOBILE,
                MediaPackageAliases.canonical(MediaPackageAliases.MAX_TV), "Max migration");
        assertEquals(MediaPackageAliases.PARAMOUNT_MOBILE,
                MediaPackageAliases.canonical(MediaPackageAliases.PARAMOUNT_TV),
                "Paramount migration");
        assertEquals(MediaPackageAliases.PRIME_MOBILE,
                MediaPackageAliases.canonical(MediaPackageAliases.PRIME_TV), "Prime migration");
    }

    private static void prefersMobileAndRetainsTvFallback() {
        // Max and Prime TV builds are known playback failures on this hardware.
        assertCandidates(MediaPackageAliases.MAX_TV, MediaPackageAliases.MAX_MOBILE);
        assertCandidates(MediaPackageAliases.PARAMOUNT_TV,
                MediaPackageAliases.PARAMOUNT_MOBILE, MediaPackageAliases.PARAMOUNT_TV);
        assertCandidates(MediaPackageAliases.PRIME_TV, MediaPackageAliases.PRIME_MOBILE);
    }

    private static void preservesUnrelatedPackages() {
        assertEquals("", MediaPackageAliases.canonical(null), "null package");
        assertCandidates("com.example.player", "com.example.player");
    }

    private static void assertCandidates(String input, String... expected) {
        String[] actual = MediaPackageAliases.launchCandidates(input);
        if (actual.length != expected.length) {
            throw new AssertionError(input + " candidate count: expected " + expected.length
                    + ", got " + actual.length);
        }
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual[index], input + " candidate " + index);
        }
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
