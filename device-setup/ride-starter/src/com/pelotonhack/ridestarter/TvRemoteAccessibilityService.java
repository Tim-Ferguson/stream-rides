package com.pelotonhack.ridestarter;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public final class TvRemoteAccessibilityService extends AccessibilityService {
    private static volatile TvRemoteAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TvRemoteController controller;

    static boolean isConnected() {
        return instance != null && instance.controller != null;
    }

    static boolean reloadPosition() {
        TvRemoteAccessibilityService service = instance;
        if (service == null || service.controller == null) {
            return false;
        }
        service.controller.reloadOverlayPosition();
        return true;
    }

    @Override
    protected void onServiceConnected() {
        if (controller != null) {
            controller.stop();
        }
        instance = this;
        controller = new TvRemoteController(this, handler);
        controller.start();
    }

    @Override
    public void onDestroy() {
        shutdownController();
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdownController();
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (controller != null) {
            controller.onAccessibilityEvent(event);
        }
    }

    @Override
    public void onInterrupt() {
        if (controller != null) {
            controller.interrupt();
        }
    }

    private void shutdownController() {
        if (instance == this) {
            instance = null;
        }
        if (controller != null) {
            controller.stop();
            controller = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
