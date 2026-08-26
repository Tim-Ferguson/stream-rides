package com.pelotonhack.ridestarter;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public final class RideStarterAccessibilityService extends AccessibilityService {
    private static RideStarterAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private StatsOverlayManager statsOverlayManager;
    private ZwiftBleBridge zwiftBleBridge;

    public static boolean refreshOverlay() {
        RideStarterAccessibilityService service = instance;
        if (service == null || service.statsOverlayManager == null) {
            return false;
        }
        service.statsOverlayManager.requestUpdateSoon();
        return true;
    }

    public static boolean reloadOverlayPositions() {
        RideStarterAccessibilityService service = instance;
        if (service == null || service.statsOverlayManager == null) {
            return false;
        }
        service.statsOverlayManager.reloadSavedPositions();
        return true;
    }

    public static boolean isRuntimeReady() {
        RideStarterAccessibilityService service = instance;
        return service != null
                && service.statsOverlayManager != null
                && RideEngine.get(service).isSensorReady();
    }

    public static boolean isOverlayReady() {
        RideStarterAccessibilityService service = instance;
        return service != null && service.statsOverlayManager != null;
    }

    public static boolean setZwiftBridgeEnabled(android.content.Context context,
                                                boolean enabled) {
        ZwiftBleBridge.setEnabled(context, enabled);
        RideStarterAccessibilityService service = instance;
        if (service == null || service.zwiftBleBridge == null) {
            if (!enabled) {
                ZwiftBleBridge.restoreBluetoothIfOwned(context);
            }
            return false;
        }
        service.zwiftBleBridge.applyPreference();
        return true;
    }

    public static String zwiftBridgeStatus(android.content.Context context) {
        if (!ZwiftBleBridge.isEnabled(context)) {
            return "OFF";
        }
        RideStarterAccessibilityService service = instance;
        return service == null || service.zwiftBleBridge == null
                ? "WAITING FOR SARO SERVICE" : service.zwiftBleBridge.status();
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        statsOverlayManager = new StatsOverlayManager(this, handler);
        statsOverlayManager.start();
        zwiftBleBridge = new ZwiftBleBridge(this, handler);
        zwiftBleBridge.applyPreference();
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        MediaLauncher.resetTransientOverlayState();
        if (statsOverlayManager != null) {
            statsOverlayManager.stop();
            statsOverlayManager = null;
        }
        if (zwiftBleBridge != null) {
            zwiftBleBridge.shutdown();
            zwiftBleBridge = null;
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence packageName = event == null ? null : event.getPackageName();
        if (statsOverlayManager != null && packageName != null
                && "com.peloton.activity".contentEquals(packageName)) {
            statsOverlayManager.requestUpdateSoon();
        }
    }

    @Override
    public void onInterrupt() {
        // The independent ride heartbeat continues; rendering resumes on reconnect.
    }
}
