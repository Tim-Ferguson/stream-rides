package com.pelotonhack.ridestarter;

final class TvRemoteTargetKey {
    private TvRemoteTargetKey() {
    }

    static String node(int windowId, int sourceHash, String bounds, String className,
                       String viewId, int actions, boolean focusable, boolean clickable,
                       boolean enabled, boolean focused, boolean visible,
                       CharSequence text, CharSequence description,
                       CharSequence stateDescription) {
        return windowId + "|" + sourceHash + '|'
                + value(bounds) + '|' + value(className) + '|' + value(viewId)
                + '|' + actions
                + '|' + flag(focusable) + flag(clickable) + flag(enabled)
                + flag(focused) + flag(visible)
                + '|' + textFingerprint(text)
                + '|' + textFingerprint(description)
                + '|' + textFingerprint(stateDescription);
    }

    static String target(String focusedNode, String clickableAncestor) {
        return value(focusedNode) + "||" + value(clickableAncestor);
    }

    static String textFingerprint(CharSequence value) {
        if (value == null) {
            return "0:0";
        }
        String text = value.toString();
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < text.length(); index++) {
            hash ^= text.charAt(index);
            hash *= 0x100000001b3L;
        }
        return text.length() + ":" + Long.toHexString(hash);
    }

    private static int flag(boolean value) {
        return value ? 1 : 0;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
