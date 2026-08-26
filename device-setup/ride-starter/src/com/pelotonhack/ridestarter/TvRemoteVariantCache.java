package com.pelotonhack.ridestarter;

import java.util.HashMap;
import java.util.Map;

final class TvRemoteVariantCache {
    interface Probe {
        boolean hasLeanbackLauncher(String packageName);
    }

    private static final long TTL_MS = 30_000;

    private final Map<String, Entry> entries = new HashMap<String, Entry>();

    boolean isTvVariant(CharSequence packageName, long now, Probe probe) {
        if (!TvRemotePackages.supports(packageName)) {
            return false;
        }
        String value = packageName.toString();
        Entry cached = entries.get(value);
        if (cached != null && now < cached.expiresAt) {
            return cached.tvVariant;
        }
        final boolean hasLeanbackLauncher;
        try {
            hasLeanbackLauncher = probe.hasLeanbackLauncher(value);
        } catch (RuntimeException exception) {
            entries.remove(value);
            return false;
        }
        boolean tvVariant = TvRemotePackages.supportsVariant(
                value, hasLeanbackLauncher);
        entries.put(value, new Entry(tvVariant, now + TTL_MS));
        return tvVariant;
    }

    boolean hasAnyTvVariant(long now, Probe probe) {
        for (String packageName : TvRemotePackages.all()) {
            if (isTvVariant(packageName, now, probe)) {
                return true;
            }
        }
        return false;
    }

    private static final class Entry {
        final boolean tvVariant;
        final long expiresAt;

        Entry(boolean tvVariant, long expiresAt) {
            this.tvVariant = tvVariant;
            this.expiresAt = expiresAt;
        }
    }
}
