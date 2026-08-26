package com.pelotonhack.ridestarter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

final class TvRemoteVariantResolver {
    private final TvRemoteVariantCache cache = new TvRemoteVariantCache();
    private final PackageManager packageManager;
    private final TvRemoteVariantCache.Probe probe = new TvRemoteVariantCache.Probe() {
        @Override
        public boolean hasLeanbackLauncher(String packageName) {
            Intent launcher = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setPackage(packageName);
            return !packageManager.queryIntentActivities(launcher, 0).isEmpty();
        }
    };

    TvRemoteVariantResolver(Context context) {
        packageManager = context.getPackageManager();
    }

    boolean isTvVariant(CharSequence packageName) {
        return cache.isTvVariant(packageName,
                android.os.SystemClock.elapsedRealtime(), probe);
    }

    boolean hasInstalledTvVariant() {
        return cache.hasAnyTvVariant(
                android.os.SystemClock.elapsedRealtime(), probe);
    }
}
