package com.pelotonhack.ridestarter;

final class TvRemotePackages {
    static final String PEACOCK = "com.peacocktv.peacockandroid";
    static final String PARAMOUNT = "com.cbs.ott";
    static final String MGM = "com.epix.epix.now";
    private static final String[] SUPPORTED = {PEACOCK, PARAMOUNT, MGM};

    private TvRemotePackages() {
    }

    static boolean supports(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toString();
        return PEACOCK.equals(value) || PARAMOUNT.equals(value) || MGM.equals(value);
    }

    static boolean supportsVariant(CharSequence packageName, boolean hasLeanbackLauncher) {
        return hasLeanbackLauncher && supports(packageName);
    }

    static boolean usesVirtualDpad(CharSequence packageName) {
        return packageName != null && PEACOCK.contentEquals(packageName);
    }

    static String[] all() {
        return SUPPORTED.clone();
    }
}
