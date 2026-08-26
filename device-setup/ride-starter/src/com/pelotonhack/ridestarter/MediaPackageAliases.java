package com.pelotonhack.ridestarter;

final class MediaPackageAliases {
    static final String MAX_MOBILE = "com.wbd.stream";
    static final String MAX_TV = "com.wbd.hbomax";
    static final String PARAMOUNT_MOBILE = "com.cbs.app";
    static final String PARAMOUNT_TV = "com.cbs.ott";
    static final String PRIME_MOBILE = "com.amazon.avod.thirdpartyclient";
    static final String PRIME_TV = "com.amazon.amazonvideo.livingroom";

    private MediaPackageAliases() {
    }

    static String canonical(String packageName) {
        if (MAX_TV.equals(packageName)) {
            return MAX_MOBILE;
        }
        if (PARAMOUNT_TV.equals(packageName)) {
            return PARAMOUNT_MOBILE;
        }
        if (PRIME_TV.equals(packageName)) {
            return PRIME_MOBILE;
        }
        return packageName == null ? "" : packageName;
    }

    static String[] launchCandidates(String packageName) {
        String canonical = canonical(packageName);
        if (PARAMOUNT_MOBILE.equals(canonical)) {
            return new String[]{PARAMOUNT_MOBILE, PARAMOUNT_TV};
        }
        return new String[]{canonical};
    }
}
