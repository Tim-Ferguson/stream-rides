package com.pelotonhack.ridestarter;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

final class OverlayAppDrawer {
    interface VisibilityListener {
        void onVisibilityChanged(boolean visible);
    }

    private static final int COLUMN_COUNT = 4;

    private final AccessibilityService service;
    private final WindowManager windowManager;
    private final VisibilityListener visibilityListener;

    private View root;
    private boolean attached;

    OverlayAppDrawer(AccessibilityService service, WindowManager windowManager,
                     VisibilityListener visibilityListener) {
        this.service = service;
        this.windowManager = windowManager;
        this.visibilityListener = visibilityListener;
    }

    boolean isShowing() {
        return attached;
    }

    void toggle() {
        if (attached) {
            hide();
        } else {
            show();
        }
    }

    void show() {
        if (attached) {
            return;
        }
        root = buildView();
        try {
            windowManager.addView(root, buildLayoutParams());
            attached = true;
            notifyVisibility();
        } catch (RuntimeException exception) {
            root = null;
            attached = false;
            notifyVisibility();
            Toast.makeText(service, "SARO app drawer is unavailable", Toast.LENGTH_SHORT).show();
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
        attached = false;
        notifyVisibility();
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.width = Math.min(metrics.widthPixels - dp(24), dp(1160));
        params.y = dp(118);
        params.height = Math.min(dp(520), metrics.heightPixels - params.y - dp(52));
        return params;
    }

    private View buildView() {
        LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        panel.setBackground(background(0xF5121316, 0x806E7279, 4));
        panel.setClickable(true);
        panel.setContentDescription("SARO app drawer");
        panel.setElevation(dp(8));

        panel.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        ScrollView scroll = new ScrollView(service);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(buildAppRows(MediaLauncher.visibleInstalledApps(service)),
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(6);
        panel.addView(scroll, scrollParams);
        return panel;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(service);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("APPS", 22, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView home = commandButton("SARO HOME", 0xFF2FBE76);
        home.setContentDescription("Open SARO Home");
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
                MediaLauncher.openHub(service);
            }
        });
        header.addView(home, headerButtonParams(dp(132)));

        TextView close = commandButton("CLOSE", 0xFF303238);
        close.setContentDescription("Close app drawer");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
            }
        });
        header.addView(close, headerButtonParams(dp(102)));
        return header;
    }

    private LinearLayout buildAppRows(List<MediaLauncher.MediaApp> apps) {
        LinearLayout rows = new LinearLayout(service);
        rows.setOrientation(LinearLayout.VERTICAL);

        int index = 0;
        while (index < apps.size()) {
            LinearLayout row = new LinearLayout(service);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            for (int column = 0; column < COLUMN_COUNT; column++) {
                if (index < apps.size()) {
                    row.addView(buildAppButton(apps.get(index)), appTileParams());
                    index += 1;
                } else {
                    View spacer = new View(service);
                    row.addView(spacer, appTileParams());
                }
            }
            rows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(112)));
        }

        if (apps.isEmpty()) {
            TextView empty = text("NO VISIBLE APPS", 18, 0xFFB8BAC0, true);
            empty.setGravity(Gravity.CENTER);
            rows.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(112)));
        }
        return rows;
    }

    private View buildAppButton(final MediaLauncher.MediaApp app) {
        LinearLayout tile = new LinearLayout(service);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), dp(8), dp(12), dp(8));
        tile.setBackground(background(0xFF22252A, 0xFF45484E, 4));
        tile.setClickable(true);
        tile.setContentDescription("Open " + app.displayName);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
                if (!MediaLauncher.launch(service, app.packageName)) {
                    Toast.makeText(service, app.displayName + " is unavailable",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        ImageView icon = new ImageView(service);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Drawable drawable = service.getPackageManager().getApplicationIcon(
                    MediaLauncher.installedPackageName(service, app.packageName));
            icon.setImageDrawable(drawable);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        tile.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView label = text(app.displayName, 16, Color.WHITE, true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setSingleLine(false);
        label.setMaxLines(2);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(12);
        tile.addView(label, labelParams);
        return tile;
    }

    private LinearLayout.LayoutParams appTileParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }

    private LinearLayout.LayoutParams headerButtonParams(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(48));
        params.leftMargin = dp(8);
        return params;
    }

    private TextView commandButton(String label, int color) {
        TextView button = text(label, 15, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setBackground(background(color, 0x806E7279, 4));
        button.setClickable(true);
        return button;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(service);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable background(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void notifyVisibility() {
        if (visibilityListener != null) {
            visibilityListener.onVisibilityChanged(attached);
        }
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
