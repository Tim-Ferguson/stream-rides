package com.pelotonhack.ridestarter;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

final class TvRemoteOverlay {
    interface Listener {
        void onDirection(int direction);

        void onSelect();

        void onBack();

        void onSaroHome();

        void onRemoteModeChanged();

        void onOverlayUnavailable();
    }

    static final String PREFS = "tv_remote_overlay";
    static final String PREF_X = "x";
    static final String PREF_Y = "y";
    private static final int PANEL_WIDTH_DP = 232;
    private static final int PANEL_HEIGHT_DP = 248;
    private static final int COLLAPSED_WIDTH_DP = 76;
    private static final int COLLAPSED_HEIGHT_DP = 56;

    private final AccessibilityService service;
    private final WindowManager windowManager;
    private final Listener listener;

    private View root;
    private WindowManager.LayoutParams params;
    private boolean attached;
    private boolean collapsed;
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;

    TvRemoteOverlay(AccessibilityService service, Listener listener) {
        this.service = service;
        this.listener = listener;
        this.windowManager = (WindowManager) service.getSystemService(
                AccessibilityService.WINDOW_SERVICE);
    }

    void show() {
        if (attached) {
            return;
        }
        root = collapsed ? buildCollapsedView() : buildPanelView();
        params = buildLayoutParams();
        try {
            windowManager.addView(root, params);
            attached = true;
        } catch (RuntimeException ignored) {
            root = null;
            params = null;
            attached = false;
            listener.onOverlayUnavailable();
        }
    }

    void hide() {
        if (attached && root != null) {
            try {
                windowManager.removeView(root);
            } catch (RuntimeException ignored) {
                // The accessibility service may have removed the window first.
            }
        }
        root = null;
        params = null;
        attached = false;
    }

    void reloadPosition() {
        if (!attached) {
            return;
        }
        hide();
        show();
    }

    boolean isVisible() {
        return attached;
    }

    private void setCollapsed(boolean value) {
        if (collapsed == value) {
            return;
        }
        savePosition();
        boolean wasAttached = attached;
        hide();
        collapsed = value;
        listener.onRemoteModeChanged();
        if (wasAttached) {
            show();
        }
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        DisplayMetrics metrics = displayMetrics();
        int width = dp(collapsed ? COLLAPSED_WIDTH_DP : PANEL_WIDTH_DP);
        int height = dp(collapsed ? COLLAPSED_HEIGHT_DP : PANEL_HEIGHT_DP);
        int defaultX = Math.max(0, metrics.widthPixels - width - dp(12));
        int usableHeight = usableHeight(metrics);
        int defaultY = Math.max(0, (usableHeight - height) / 2);

        WindowManager.LayoutParams result = new WindowManager.LayoutParams();
        result.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        result.format = PixelFormat.TRANSLUCENT;
        result.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        result.gravity = Gravity.TOP | Gravity.LEFT;
        result.width = width;
        result.height = height;
        result.x = clamp(preferences().getInt(PREF_X, defaultX), 0,
                Math.max(0, metrics.widthPixels - width));
        result.y = clamp(preferences().getInt(PREF_Y, defaultY), 0,
                Math.max(0, usableHeight - height));
        return result;
    }

    private View buildCollapsedView() {
        TextView button = commandButton("D-PAD", 13);
        button.setContentDescription("Open TV remote");
        button.setBackground(panelBackground());
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCollapsed(false);
            }
        });
        return button;
    }

    private View buildPanelView() {
        LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(6), dp(8), dp(8));
        panel.setBackground(panelBackground());
        panel.setElevation(dp(8));
        panel.setContentDescription("TV remote");

        panel.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        panel.addView(buildCommandRow(
                command("BACK", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onBack();
                    }
                }),
                command("^", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onDirection(TvFocusNavigator.UP);
                    }
                }),
                command("SARO", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onSaroHome();
                    }
                })), rowParams());
        panel.addView(buildCommandRow(
                command("<", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onDirection(TvFocusNavigator.LEFT);
                    }
                }),
                command("OK", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onSelect();
                    }
                }),
                command(">", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onDirection(TvFocusNavigator.RIGHT);
                    }
                })), rowParams());
        panel.addView(buildCommandRow(
                spacer(),
                command("v", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onDirection(TvFocusNavigator.DOWN);
                    }
                }),
                spacer()), rowParams());
        return panel;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(service);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("TV REMOTE", 15, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setContentDescription("Drag TV remote");
        title.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleDrag(event);
            }
        });
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView collapse = commandButton("_", 18);
        collapse.setContentDescription("Collapse TV remote");
        collapse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCollapsed(true);
            }
        });
        header.addView(collapse, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private LinearLayout buildCommandRow(View left, View center, View right) {
        LinearLayout row = new LinearLayout(service);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(left, commandParams());
        row.addView(center, commandParams());
        row.addView(right, commandParams());
        return row;
    }

    private View command(String label, View.OnClickListener clickListener) {
        TextView button = commandButton(label, label.length() > 2 ? 12 : 20);
        button.setContentDescription(commandDescription(label));
        button.setOnClickListener(clickListener);
        return button;
    }

    private View spacer() {
        return new View(service);
    }

    private TextView commandButton(String label, int sizeSp) {
        TextView button = text(label, sizeSp, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setClickable(true);
        button.setBackground(buttonBackground());
        button.setOnTouchListener(new PressTouchListener(dp(12)));
        return button;
    }

    private TextView text(String value, int sizeSp, boolean bold) {
        TextView view = new TextView(service);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    private LinearLayout.LayoutParams commandParams() {
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(0, dp(56), 1f);
        result.setMargins(dp(3), dp(3), dp(3), dp(3));
        return result;
    }

    private boolean handleDrag(MotionEvent event) {
        if (params == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartX = params.x;
                dragStartY = params.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                moveTo(dragStartX + Math.round(event.getRawX() - dragStartRawX),
                        dragStartY + Math.round(event.getRawY() - dragStartRawY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                savePosition();
                return true;
            default:
                return false;
        }
    }

    private void moveTo(int x, int y) {
        DisplayMetrics metrics = displayMetrics();
        int usableHeight = usableHeight(metrics);
        params.x = clamp(x, 0, Math.max(0, metrics.widthPixels - params.width));
        params.y = clamp(y, 0, Math.max(0, usableHeight - params.height));
        if (attached && root != null) {
            try {
                windowManager.updateViewLayout(root, params);
            } catch (RuntimeException ignored) {
                detachAfterFailure();
            }
        }
    }

    private void detachAfterFailure() {
        if (root != null) {
            try {
                windowManager.removeViewImmediate(root);
            } catch (RuntimeException ignored) {
                // The failed update can mean WindowManager already detached it.
            }
        }
        root = null;
        params = null;
        attached = false;
        listener.onOverlayUnavailable();
    }

    private void savePosition() {
        if (params == null) {
            return;
        }
        preferences().edit().putInt(PREF_X, params.x).putInt(PREF_Y, params.y).apply();
    }

    private android.content.SharedPreferences preferences() {
        return service.getSharedPreferences(PREFS, AccessibilityService.MODE_PRIVATE);
    }

    private DisplayMetrics displayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }

    private int usableHeight(DisplayMetrics realMetrics) {
        DisplayMetrics usableMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(usableMetrics);
        return usableMetrics.heightPixels > 0
                ? Math.min(realMetrics.heightPixels, usableMetrics.heightPixels)
                : realMetrics.heightPixels;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xF5121316);
        drawable.setCornerRadius(dp(4));
        drawable.setStroke(dp(1), 0x806E7279);
        return drawable;
    }

    private GradientDrawable buttonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xFF303238);
        drawable.setCornerRadius(dp(4));
        drawable.setStroke(dp(1), 0x806E7279);
        return drawable;
    }

    private static String commandDescription(String label) {
        if ("^".equals(label)) {
            return "TV remote up";
        }
        if ("v".equals(label)) {
            return "TV remote down";
        }
        if ("<".equals(label)) {
            return "TV remote left";
        }
        if (">".equals(label)) {
            return "TV remote right";
        }
        if ("OK".equals(label)) {
            return "TV remote select";
        }
        if ("BACK".equals(label)) {
            return "TV remote back";
        }
        return "Open SARO Home";
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class PressTouchListener implements View.OnTouchListener {
        private final float slop;
        private float downX;
        private float downY;
        private boolean cancelled;

        PressTouchListener(float slop) {
            this.slop = slop;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    cancelled = false;
                    view.setAlpha(0.72f);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() - downX) > slop
                            || Math.abs(event.getRawY() - downY) > slop) {
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
}
