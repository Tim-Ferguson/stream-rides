package com.pelotonhack.ridestarter;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

final class StatsOverlayManager {
    private static final long POLL_MS = 1000;
    private static final long VALUE_HOLD_MS = 120000;
    private static final long END_CONFIRM_MS = 5000;
    private static final long BANNER_DISMISS_RETRY_MS = 5000;
    private static final int BANNER_MAX_TREE_NODES = 256;
    private static final long BANNER_TREE_BUDGET_MS = 25;
    private static final String[] CANCELLED_SUBSCRIPTION_MESSAGES = {
            "The subscription attached to this bike was cancelled. Please use your account to reactivate.",
            "The subscription to this bike has been cancelled. Please use your account to reactivate."
    };
    private static final String PREFS = "stats_overlay";
    private static final String PREF_X = "x";
    private static final String PREF_Y = "y";
    private static final String PREF_START_X = "start_x";
    private static final String PREF_START_Y = "start_y";
    private static final String PREF_AUTOMATION_ENABLED = "automation_enabled";

    static boolean isSubscriptionPromptAutomationEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_AUTOMATION_ENABLED, false);
    }

    static void setSubscriptionPromptAutomationEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_AUTOMATION_ENABLED, enabled)
                .commit();
    }

    private static final String[] LABELS = {
            "TIME", "EST MPH", "EST MI", "CAD", "WATTS", "RES", "KJ", "EST CAL"
    };

    private final AccessibilityService service;
    private final Handler handler;
    private final WindowManager windowManager;
    private final SharedPreferences preferences;
    private final RideEngine rideEngine;
    private final OverlayAppDrawer appDrawer;
    private final TextView[] values = new TextView[LABELS.length];
    private final String[] cachedValues = new String[LABELS.length];
    private final long[] cachedValueAt = new long[LABELS.length];
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            poll();
            handler.postDelayed(this, POLL_MS);
        }
    };
    private final Runnable eventUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            poll();
        }
    };

    private LinearLayout root;
    private WindowManager.LayoutParams overlayParams;
    private boolean attached;
    private LinearLayout startRideRoot;
    private TextView startRideButton;
    private WindowManager.LayoutParams startRideOverlayParams;
    private boolean startRideAttached;
    private boolean startRideDragMoved;
    private float startRideDragStartRawX;
    private float startRideDragStartRawY;
    private int startRideDragStartX;
    private int startRideDragStartY;
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;
    private TextView appsButton;
    private TextView pauseButton;
    private TextView endButton;
    private long endArmedUntil;
    private long lastBannerDismissAttemptAt;
    private final Runnable disarmEndRunnable = new Runnable() {
        @Override
        public void run() {
            disarmEnd();
        }
    };

    StatsOverlayManager(AccessibilityService service, Handler handler) {
        this.service = service;
        this.handler = handler;
        this.windowManager = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
        this.preferences = service.getSharedPreferences(PREFS, AccessibilityService.MODE_PRIVATE);
        this.rideEngine = RideEngine.get(service);
        this.appDrawer = new OverlayAppDrawer(service, windowManager,
                new OverlayAppDrawer.VisibilityListener() {
                    @Override
                    public void onVisibilityChanged(boolean visible) {
                        if (appsButton != null) {
                            appsButton.setText(visible ? "CLOSE" : "APPS");
                        }
                    }
                });
    }

    void start() {
        handler.removeCallbacks(pollRunnable);
        handler.removeCallbacks(eventUpdateRunnable);
        handler.post(pollRunnable);
    }

    void stop() {
        handler.removeCallbacks(pollRunnable);
        handler.removeCallbacks(eventUpdateRunnable);
        detach();
        detachStartRide();
    }

    void requestUpdateSoon() {
        handler.removeCallbacks(eventUpdateRunnable);
        handler.postDelayed(eventUpdateRunnable, 150);
    }

    void reloadSavedPositions() {
        detach();
        detachStartRide();
        requestUpdateSoon();
    }

    private void poll() {
        if (MainActivity.isForeground() || GameActivity.isForeground()) {
            detach();
            detachStartRide();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        dismissCancelledSubscriptionBanner(now);
        RideEngine.RideSnapshot localRide = rideEngine.snapshot();
        RideSessionStore.Session session = localRide.session;
        if (session.active) {
            MediaLauncher.disarmRideStartOverlay();
            detachStartRide();
            StatsSnapshot snapshot = readLocalStats(localRide);
            attachIfNeeded();
            render(snapshot, now, session.paused);
        } else {
            detach();
            if (MediaLauncher.isRideStartOverlayArmed()) {
                attachStartRideIfNeeded();
            } else {
                detachStartRide();
            }
        }
    }

    private void dismissCancelledSubscriptionBanner(long now) {
        if (!preferences.getBoolean(PREF_AUTOMATION_ENABLED, false)) {
            return;
        }
        if (now - lastBannerDismissAttemptAt < BANNER_DISMISS_RETRY_MS) {
            return;
        }
        lastBannerDismissAttemptAt = now;

        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return;
        }
        BikeAppAccessibilityPolicy.TraversalBudget budget =
                new BikeAppAccessibilityPolicy.TraversalBudget(
                        BANNER_MAX_TREE_NODES, BANNER_TREE_BUDGET_MS,
                        SystemClock.uptimeMillis());
        for (AccessibilityWindowInfo window : windows) {
            if (!budget.canContinue(SystemClock.uptimeMillis())) {
                return;
            }
            AccessibilityNodeInfo rootNode = window.getRoot();
            if (rootNode == null) {
                continue;
            }
            try {
                if (!isBikeAppNode(rootNode)) {
                    continue;
                }
                Rect messageBounds = new Rect();
                if (!findSubscriptionMessageBounds(rootNode, messageBounds, budget)) {
                    continue;
                }

                DisplayMetrics metrics = new DisplayMetrics();
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                Rect windowBounds = new Rect();
                window.getBoundsInScreen(windowBounds);
                int compactHeight = Math.round(120f * metrics.density);
                boolean compactTopWindow = !windowBounds.isEmpty()
                        && windowBounds.height() <= compactHeight
                        && windowBounds.centerY() < metrics.heightPixels / 4;
                int x = compactTopWindow
                        ? windowBounds.right - Math.round(18f * metrics.density)
                        : messageBounds.right + Math.round(40f * metrics.density);
                int y = compactTopWindow ? windowBounds.centerY() : messageBounds.centerY();
                x = clamp(x, 0, metrics.widthPixels - 1);
                y = clamp(y, 0, metrics.heightPixels - 1);
                dispatchScreenTap(x, y);
                return;
            } finally {
                rootNode.recycle();
            }
        }
    }

    private boolean findSubscriptionMessageBounds(AccessibilityNodeInfo node, Rect result,
                                                  BikeAppAccessibilityPolicy.TraversalBudget budget) {
        if (!budget.enterNode(SystemClock.uptimeMillis()) || !isBikeAppNode(node)) {
            return false;
        }
        if (matchesCancelledSubscriptionMessage(node.getText())
                || matchesCancelledSubscriptionMessage(node.getContentDescription())) {
            node.getBoundsInScreen(result);
            return !result.isEmpty();
        }
        for (int index = 0; index < node.getChildCount()
                && budget.canContinue(SystemClock.uptimeMillis()); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                if (findSubscriptionMessageBounds(child, result, budget)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private StatsSnapshot readLocalStats(RideEngine.RideSnapshot ride) {
        RideSessionStore.Session session = ride.session;
        SensorRepository.Snapshot sensor = ride.sensor;
        StatsSnapshot snapshot = new StatsSnapshot();
        snapshot.elapsed = formatElapsed(session.elapsedMs);
        snapshot.speed = String.format(Locale.US, "%.1f mph", ride.estimatedSpeedMph);
        snapshot.distance = String.format(Locale.US, "%.2f mi", ride.distanceMiles);
        snapshot.totalOutput = Integer.toString(Math.round(ride.outputKilojoules)) + " kj";
        snapshot.calories = Integer.toString(Math.round(ride.outputKilojoules)) + " kcal";
        if (sensor.fresh) {
            snapshot.cadence = Long.toString(sensor.cadenceRpm);
            snapshot.output = Integer.toString(Math.round(sensor.outputWatts));
            snapshot.resistance = Integer.toString(sensor.resistance);
        } else {
            snapshot.cadence = "--";
            snapshot.output = "--";
            snapshot.resistance = "--";
        }
        return snapshot;
    }

    private void attachIfNeeded() {
        if (attached) {
            return;
        }
        if (root == null) {
            root = buildView();
        }
        overlayParams = buildLayoutParams();
        try {
            windowManager.addView(root, overlayParams);
            attached = true;
        } catch (RuntimeException exception) {
            attached = false;
        }
    }

    private void detach() {
        appDrawer.hide();
        if (!attached || root == null) {
            return;
        }
        try {
            windowManager.removeView(root);
        } catch (RuntimeException ignored) {
            // The accessibility window may have been removed with the service.
        }
        attached = false;
    }

    private void attachStartRideIfNeeded() {
        if (startRideAttached) {
            return;
        }
        if (startRideRoot == null) {
            startRideRoot = buildStartRideView();
        }
        startRideOverlayParams = buildStartRideLayoutParams();
        try {
            windowManager.addView(startRideRoot, startRideOverlayParams);
            startRideAttached = true;
        } catch (RuntimeException exception) {
            startRideAttached = false;
        }
    }

    private void detachStartRide() {
        if (!startRideAttached || startRideRoot == null) {
            return;
        }
        try {
            windowManager.removeView(startRideRoot);
        } catch (RuntimeException ignored) {
            // The accessibility window may have been removed with the service.
        }
        startRideAttached = false;
    }

    private WindowManager.LayoutParams buildStartRideLayoutParams() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int width = dp(208);
        int height = dp(56);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.width = width;
        params.height = height;
        int defaultX = metrics.widthPixels - width - dp(16);
        int defaultY = Math.max(dp(16), (metrics.heightPixels - height) / 3);
        params.x = clamp(preferences.getInt(PREF_START_X, defaultX),
                0, Math.max(0, metrics.widthPixels - width));
        params.y = clamp(preferences.getInt(PREF_START_Y, defaultY),
                0, Math.max(0, metrics.heightPixels - height));
        return params;
    }

    private LinearLayout buildStartRideView() {
        LinearLayout layout = new LinearLayout(service);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);

        startRideButton = new TextView(service);
        startRideButton.setText("START RIDE");
        startRideButton.setTextColor(Color.WHITE);
        startRideButton.setGravity(Gravity.CENTER);
        startRideButton.setSingleLine(true);
        startRideButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        startRideButton.setTypeface(Typeface.DEFAULT_BOLD);
        startRideButton.setClickable(true);
        startRideButton.setContentDescription("Start SARO Local Ride; drag to move");
        startRideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestStartRide();
            }
        });
        startRideButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleStartRideTouch(view, event);
            }
        });

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xEE2EBD73);
        background.setCornerRadius(dp(4));
        background.setStroke(dp(1), 0x70FFFFFF);
        startRideButton.setBackground(background);
        layout.addView(startRideButton, new LinearLayout.LayoutParams(dp(156), dp(56)));

        TextView dismissButton = new TextView(service);
        dismissButton.setText("X");
        dismissButton.setTextColor(Color.WHITE);
        dismissButton.setGravity(Gravity.CENTER);
        dismissButton.setSingleLine(true);
        dismissButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dismissButton.setTypeface(Typeface.DEFAULT_BOLD);
        dismissButton.setClickable(true);
        dismissButton.setContentDescription("Dismiss start ride control");
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaLauncher.disarmRideStartOverlay();
                detachStartRide();
            }
        });
        dismissButton.setOnTouchListener(new ControlButtonTouchListener(dp(12)));
        GradientDrawable dismissBackground = new GradientDrawable();
        dismissBackground.setColor(0xEE2D2D34);
        dismissBackground.setCornerRadius(dp(4));
        dismissBackground.setStroke(dp(1), 0x70FFFFFF);
        dismissButton.setBackground(dismissBackground);
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(dp(48), dp(56));
        dismissParams.leftMargin = dp(4);
        layout.addView(dismissButton, dismissParams);
        return layout;
    }

    private boolean handleStartRideTouch(View view, MotionEvent event) {
        if (startRideOverlayParams == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startRideDragStartRawX = event.getRawX();
                startRideDragStartRawY = event.getRawY();
                startRideDragStartX = startRideOverlayParams.x;
                startRideDragStartY = startRideOverlayParams.y;
                startRideDragMoved = false;
                view.setAlpha(0.76f);
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - startRideDragStartRawX);
                int dy = Math.round(event.getRawY() - startRideDragStartRawY);
                if (DragGesturePolicy.hasMoved(startRideDragStartRawX,
                        startRideDragStartRawY, event.getRawX(), event.getRawY(), dp(8))) {
                    startRideDragMoved = true;
                    view.setAlpha(1f);
                    moveStartRideOverlayTo(startRideDragStartX + dx,
                            startRideDragStartY + dy);
                }
                return true;
            case MotionEvent.ACTION_UP:
                view.setAlpha(1f);
                if (startRideDragMoved) {
                    saveStartRideOverlayPosition();
                } else {
                    view.performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                view.setAlpha(1f);
                if (startRideDragMoved) {
                    saveStartRideOverlayPosition();
                }
                return true;
            default:
                return true;
        }
    }

    private void moveStartRideOverlayTo(int x, int y) {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        startRideOverlayParams.x = clamp(x, 0,
                Math.max(0, metrics.widthPixels - startRideOverlayParams.width));
        startRideOverlayParams.y = clamp(y, 0,
                Math.max(0, metrics.heightPixels - startRideOverlayParams.height));
        if (startRideAttached && startRideRoot != null) {
            try {
                windowManager.updateViewLayout(startRideRoot, startRideOverlayParams);
            } catch (RuntimeException ignored) {
                startRideAttached = false;
            }
        }
    }

    private void saveStartRideOverlayPosition() {
        if (startRideOverlayParams == null) {
            return;
        }
        preferences.edit()
                .putInt(PREF_START_X, startRideOverlayParams.x)
                .putInt(PREF_START_Y, startRideOverlayParams.y)
                .apply();
    }

    private void requestStartRide() {
        RideSessionStore.Session session = RideSessionStore.read(service);
        if (session.active) {
            MediaLauncher.disarmRideStartOverlay();
            detachStartRide();
            requestUpdateSoon();
            return;
        }
        if (!rideEngine.isSensorReady()) {
            rideEngine.reconnectSensors();
            Toast.makeText(service,
                    "Reconnecting to bike sensors. Try again in a moment.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        rideEngine.startLocalRide();
        MediaLauncher.disarmRideStartOverlay();
        detachStartRide();
        Toast.makeText(service, "SARO Local Ride started", Toast.LENGTH_SHORT).show();
        requestUpdateSoon();
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int availableWidth = Math.max(1, metrics.widthPixels - dp(24));
        int width = Math.min(availableWidth,
                Math.max(dp(780), (int) (metrics.widthPixels * 0.72f)));
        int height = Math.max(dp(56), (int) (metrics.heightPixels * 0.08f));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.width = width;
        params.height = height;
        int defaultX = dp(12);
        int defaultY = metrics.heightPixels - height
                - Math.max(dp(46), (int) (metrics.heightPixels * 0.07f));
        params.x = clamp(preferences.getInt(PREF_X, defaultX), 0, Math.max(0, metrics.widthPixels - width));
        params.y = clamp(preferences.getInt(PREF_Y, defaultY), 0, Math.max(0, metrics.heightPixels - height));
        return params;
    }

    private LinearLayout buildView() {
        LinearLayout layout = new LinearLayout(service);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(10), dp(4), dp(10), dp(4));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xE6101012);
        background.setCornerRadius(dp(4));
        background.setStroke(dp(1), 0x40FFFFFF);
        layout.setBackground(background);

        LinearLayout statsArea = new LinearLayout(service);
        statsArea.setOrientation(LinearLayout.HORIZONTAL);
        statsArea.setGravity(Gravity.CENTER_VERTICAL);
        statsArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleDragTouch(event);
            }
        });

        for (int index = 0; index < LABELS.length; index++) {
            if (index > 0) {
                View separator = new View(service);
                separator.setBackgroundColor(0x33FFFFFF);
                statsArea.addView(separator, new LinearLayout.LayoutParams(
                        dp(1), LinearLayout.LayoutParams.MATCH_PARENT));
            }
            LinearLayout cell = new LinearLayout(service);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(4), 0, dp(4), 0);

            TextView label = new TextView(service);
            label.setText(LABELS[index]);
            label.setTextColor(0xFFB8BAC0);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
            label.setTypeface(Typeface.DEFAULT_BOLD);

            TextView value = new TextView(service);
            value.setText("--");
            value.setTextColor(Color.WHITE);
            value.setGravity(Gravity.CENTER);
            value.setSingleLine(true);
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            value.setTypeface(Typeface.DEFAULT_BOLD);

            values[index] = value;
            cell.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            cell.addView(value, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            statsArea.addView(cell, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
        layout.addView(statsArea, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        View separator = new View(service);
        separator.setBackgroundColor(0x33FFFFFF);
        layout.addView(separator, new LinearLayout.LayoutParams(
                dp(1), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(service);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(6), 0, 0, 0);
        appsButton = buildControlButton("APPS", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                appDrawer.toggle();
            }
        });
        controls.addView(appsButton);
        pauseButton = buildControlButton("PAUSE", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPauseToggle();
            }
        });
        controls.addView(pauseButton);
        endButton = buildControlButton("END", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestEndWorkout();
            }
        });
        controls.addView(endButton);
        layout.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        return layout;
    }

    private TextView buildControlButton(String label, final View.OnClickListener listener) {
        TextView button = new TextView(service);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setClickable(true);
        button.setOnClickListener(listener);
        button.setOnTouchListener(new ControlButtonTouchListener(dp(12)));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF2D2D34);
        background.setCornerRadius(dp(4));
        background.setStroke(dp(1), 0x40FFFFFF);
        button.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(64), dp(48));
        params.leftMargin = dp(5);
        button.setLayoutParams(params);
        return button;
    }

    private boolean handleDragTouch(MotionEvent event) {
        if (overlayParams == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartX = overlayParams.x;
                dragStartY = overlayParams.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - dragStartRawX);
                int dy = Math.round(event.getRawY() - dragStartRawY);
                moveOverlayTo(dragStartX + dx, dragStartY + dy);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                saveOverlayPosition();
                return true;
            default:
                return false;
        }
    }

    private void moveOverlayTo(int x, int y) {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        overlayParams.x = clamp(x, 0, Math.max(0, metrics.widthPixels - overlayParams.width));
        overlayParams.y = clamp(y, 0, Math.max(0, metrics.heightPixels - overlayParams.height));
        if (attached && root != null) {
            try {
                windowManager.updateViewLayout(root, overlayParams);
            } catch (RuntimeException ignored) {
                attached = false;
            }
        }
    }

    private void saveOverlayPosition() {
        if (overlayParams == null) {
            return;
        }
        preferences.edit()
                .putInt(PREF_X, overlayParams.x)
                .putInt(PREF_Y, overlayParams.y)
                .apply();
    }

    private void requestPauseToggle() {
        RideSessionStore.Session session = RideSessionStore.read(service);
        if (!session.active) {
            Toast.makeText(service, "No SARO Local Ride is active", Toast.LENGTH_SHORT).show();
            return;
        }
        RideSessionStore.Session updated = rideEngine.togglePause();
        if (pauseButton != null) {
            pauseButton.setText(updated.paused ? "RESUME" : "PAUSE");
        }
        Toast.makeText(service, updated.paused ? "Ride paused" : "Ride resumed",
                Toast.LENGTH_SHORT).show();
        requestUpdateSoon();
    }

    private void requestEndWorkout() {
        RideSessionStore.Session localSession = RideSessionStore.read(service);
        if (!localSession.active) {
            disarmEnd();
            Toast.makeText(service, "No SARO Local Ride is active", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now > endArmedUntil) {
            endArmedUntil = now + END_CONFIRM_MS;
            endButton.setText("CONFIRM");
            handler.removeCallbacks(disarmEndRunnable);
            handler.postDelayed(disarmEndRunnable, END_CONFIRM_MS);
            Toast.makeText(service, "Tap CONFIRM to end the SARO Local Ride",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        disarmEnd();
        RideSessionStore.Session ended = rideEngine.endRide();
        detach();
        Toast.makeText(service, "Local ride ended: " + formatElapsed(ended.elapsedMs),
                Toast.LENGTH_SHORT).show();
    }

    private void disarmEnd() {
        endArmedUntil = 0;
        handler.removeCallbacks(disarmEndRunnable);
        if (endButton != null) {
            endButton.setText("END");
        }
    }

    private void dispatchScreenTap(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Toast.makeText(service, "Touch action unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 90))
                .build();
        service.dispatchGesture(gesture, null, handler);
    }

    private static boolean isBikeAppNode(AccessibilityNodeInfo node) {
        return BikeAppAccessibilityPolicy.allowsPackage(node.getPackageName());
    }

    private static boolean matchesCancelledSubscriptionMessage(CharSequence value) {
        if (value == null) {
            return false;
        }
        String actual = normalizeMessage(value.toString());
        for (String expected : CANCELLED_SUBSCRIPTION_MESSAGES) {
            if (actual.equals(normalizeMessage(expected))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeMessage(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
    }

    private void render(StatsSnapshot snapshot, long now, boolean paused) {
        if (appsButton != null) {
            appsButton.setVisibility(View.VISIBLE);
            appsButton.setText(appDrawer.isShowing() ? "CLOSE" : "APPS");
        }
        if (pauseButton != null) {
            pauseButton.setText(paused ? "RESUME" : "PAUSE");
        }
        setValue(0, snapshot.elapsed, now);
        setValue(1, stripUnit(snapshot.speed), now);
        setValue(2, stripUnit(snapshot.distance), now);
        setValue(3, snapshot.cadence, now);
        setValue(4, snapshot.output, now);
        setValue(5, snapshot.resistance, now);
        setValue(6, stripUnit(snapshot.totalOutput), now);
        setValue(7, stripUnit(snapshot.calories), now);
    }

    private void setValue(int index, String value, long now) {
        if (!TextUtils.isEmpty(value)) {
            cachedValues[index] = value;
            cachedValueAt[index] = now;
            values[index].setText(value);
            return;
        }
        if (!TextUtils.isEmpty(cachedValues[index]) && now - cachedValueAt[index] <= VALUE_HOLD_MS) {
            values[index].setText(cachedValues[index]);
            return;
        }
        values[index].setText("--");
    }

    private static String stripUnit(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.replace(" mph", "")
                .replace(" mi", "")
                .replace(" kj", "")
                .replace(" kcal", "");
    }

    private static String formatElapsed(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private static final class ControlButtonTouchListener implements View.OnTouchListener {
        private final float tapSlop;
        private float downRawX;
        private float downRawY;
        private boolean cancelled;

        ControlButtonTouchListener(float tapSlop) {
            this.tapSlop = tapSlop;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    cancelled = false;
                    view.setAlpha(0.72f);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() - downRawX) > tapSlop
                            || Math.abs(event.getRawY() - downRawY) > tapSlop) {
                        cancelled = true;
                        view.setAlpha(1f);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    view.setAlpha(1f);
                    if (!cancelled) {
                        view.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    view.setAlpha(1f);
                    cancelled = true;
                    return true;
                default:
                    return true;
            }
        }
    }

    private static final class StatsSnapshot {
        String elapsed = "";
        String speed = "";
        String distance = "";
        String cadence = "";
        String output = "";
        String resistance = "";
        String totalOutput = "";
        String calories = "";

    }
}
