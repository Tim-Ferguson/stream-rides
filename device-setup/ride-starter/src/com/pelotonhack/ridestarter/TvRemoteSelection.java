package com.pelotonhack.ridestarter;

final class TvRemoteSelection {
    static final long ARM_TIMEOUT_MS = 4000;
    static final long CONFIRM_TIMEOUT_MS = 1800;

    private String packageName;
    private String targetKey;
    private int windowId = -1;
    private long armedAtMs;
    private boolean confirmationRequired;
    private boolean confirmationPending;

    void arm(String selectedPackage, int selectedWindowId, String selectedTargetKey,
             boolean requiresConfirmation, long nowMs) {
        if (selectedPackage == null || selectedWindowId < 0
                || (!requiresConfirmation && selectedTargetKey == null)) {
            clear();
            return;
        }
        packageName = selectedPackage;
        windowId = selectedWindowId;
        targetKey = selectedTargetKey;
        armedAtMs = nowMs;
        confirmationRequired = requiresConfirmation;
        confirmationPending = false;
    }

    Result consume(String currentPackage, int currentWindowId, long nowMs) {
        long ageMs = nowMs - armedAtMs;
        boolean allowed = packageName != null && packageName.equals(currentPackage)
                && windowId == currentWindowId
                && ageMs >= 0
                && ageMs <= (confirmationPending ? CONFIRM_TIMEOUT_MS : ARM_TIMEOUT_MS);
        if (!allowed) {
            clear();
            return new Result(Decision.REJECTED, null);
        }
        if (confirmationRequired && !confirmationPending) {
            confirmationPending = true;
            armedAtMs = nowMs;
            return new Result(Decision.CONFIRM_REQUIRED, null);
        }
        Result result = new Result(Decision.ALLOWED, targetKey);
        clear();
        return result;
    }

    void clear() {
        packageName = null;
        targetKey = null;
        windowId = -1;
        armedAtMs = 0;
        confirmationRequired = false;
        confirmationPending = false;
    }

    enum Decision {
        REJECTED,
        CONFIRM_REQUIRED,
        ALLOWED
    }

    static final class Result {
        final Decision decision;
        final String targetKey;

        Result(Decision decision, String targetKey) {
            this.decision = decision;
            this.targetKey = targetKey;
        }
    }
}
