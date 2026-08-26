package com.pelotonhack.ridestarter;

final class TvRemoteWindowPolicy {
    static final int TYPE_APPLICATION = 1;

    private TvRemoteWindowPolicy() {
    }

    static int rank(boolean active, boolean focused) {
        return active ? 2 : focused ? 1 : 0;
    }

    static boolean shouldReplace(int selectedRank, int selectedLayer,
                                 int candidateRank, int candidateLayer) {
        return candidateRank > 0 && (candidateLayer > selectedLayer
                || (candidateLayer == selectedLayer && candidateRank > selectedRank));
    }

    static boolean isSupportedForegroundType(int selectedType) {
        return selectedType == TYPE_APPLICATION;
    }
}
