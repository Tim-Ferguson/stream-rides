package com.pelotonhack.ridestarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int COLOR_BACKGROUND = 0xFF101113;
    private static final int COLOR_PANEL = 0xFF202226;
    private static final int COLOR_GREEN = 0xFF2EBD73;
    private static final int COLOR_RED = 0xFFD85050;
    private static final int COLOR_MUTED = 0xFFAEB2B9;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TvRemoteServiceState tvRemoteServiceState = new TvRemoteServiceState();
    private static volatile boolean foreground;
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateRideState();
            handler.postDelayed(this, 1000);
        }
    };

    private TextView rideState;
    private TextView startButton;
    private TextView pauseButton;
    private TextView endButton;
    private TextView sensorStatus;
    private TextView sensorDetail;
    private TextView sensorAction;
    private TextView zwiftBridgeStatus;
    private TextView zwiftBridgeToggle;
    private TextView tvRemoteStatus;
    private boolean returnToPreviousMediaTask;

    static boolean isForeground() {
        return foreground;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readLaunchMode(getIntent());
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(buildScreen());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readLaunchMode(intent);
        showHub();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(updateRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        MediaLauncher.disarmRideStartOverlay();
        RideStarterAccessibilityService.refreshOverlay();
    }

    @Override
    protected void onPause() {
        foreground = false;
        RideStarterAccessibilityService.refreshOverlay();
        super.onPause();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(updateRunnable);
        super.onStop();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setPadding(dp(56), dp(34), dp(56), dp(34));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("SARO", 34, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        rideState = text("", 18, COLOR_MUTED, true);
        rideState.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(rideState, new LinearLayout.LayoutParams(
                0, dp(54), 1f));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(buildSensorStatusBand(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        primaryActions.setGravity(Gravity.CENTER_VERTICAL);
        primaryActions.setPadding(0, dp(26), 0, dp(12));

        startButton = commandButton("START LOCAL RIDE", COLOR_GREEN);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startOrReturnToRide();
            }
        });
        primaryActions.addView(startButton, primaryActionParams(1.6f));

        pauseButton = commandButton("PAUSE", COLOR_PANEL);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RideEngine.get(MainActivity.this).togglePause();
                RideStarterAccessibilityService.refreshOverlay();
                updateRideState();
            }
        });
        primaryActions.addView(pauseButton, primaryActionParams(1f));

        endButton = commandButton("END", COLOR_RED);
        endButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmEndRide();
            }
        });
        primaryActions.addView(endButton, primaryActionParams(1f));
        root.addView(primaryActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout utilities = new LinearLayout(this);
        utilities.setOrientation(LinearLayout.HORIZONTAL);
        utilities.setGravity(Gravity.CENTER_VERTICAL);
        utilities.setPadding(0, 0, 0, dp(28));

        TextView bikeHomeButton = commandButton("BIKE HOME", COLOR_PANEL);
        bikeHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!MediaLauncher.launchBikeHome(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "Bike Home is unavailable",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        utilities.addView(bikeHomeButton, actionParams());

        TextView gamesButton = commandButton("GAMES", COLOR_PANEL);
        gamesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, GameActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
        utilities.addView(gamesButton, actionParams());

        TextView storeButton = commandButton("APP STORE", COLOR_PANEL);
        storeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!MediaLauncher.launchAppStore(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "App Store is unavailable",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        utilities.addView(storeButton, actionParams());

        TextView manageButton = commandButton("MANAGE APPS", COLOR_PANEL);
        manageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAppManager();
            }
        });
        utilities.addView(manageButton, actionParams());
        root.addView(utilities, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView appsHeading = text("WATCH", 15, COLOR_MUTED, true);
        root.addView(appsHeading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        ScrollView appScroll = new ScrollView(this);
        appScroll.setFillViewport(true);
        appScroll.addView(buildAppRows(MediaLauncher.visibleInstalledApps(this)));
        root.addView(appScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View buildSensorStatusBand() {
        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);

        sensorStatus = text("SENSORS CONNECTING", 16, 0xFFE0A43A, true);
        band.addView(sensorStatus, new LinearLayout.LayoutParams(dp(238),
                ViewGroup.LayoutParams.MATCH_PARENT));

        sensorDetail = text("Waiting for bike telemetry", 15, COLOR_MUTED, false);
        sensorDetail.setGravity(Gravity.CENTER_VERTICAL);
        band.addView(sensorDetail, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        sensorAction = commandButton("RETRY", COLOR_PANEL);
        sensorAction.setTextSize(14);
        sensorAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recoverSensorRuntime();
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                dp(210), dp(52));
        actionParams.leftMargin = dp(14);
        band.addView(sensorAction, actionParams);
        return band;
    }

    private void showAppManager() {
        rideState = null;
        startButton = null;
        pauseButton = null;
        endButton = null;
        sensorStatus = null;
        sensorDetail = null;
        sensorAction = null;
        setContentView(buildAppManagerScreen());
    }

    private void showHub() {
        zwiftBridgeStatus = null;
        zwiftBridgeToggle = null;
        tvRemoteStatus = null;
        setContentView(buildScreen());
        updateRideState();
    }

    private View buildAppManagerScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setPadding(dp(56), dp(34), dp(56), dp(34));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Manage apps", 34, Color.WHITE, true),
                new LinearLayout.LayoutParams(0, dp(58), 1f));

        TextView doneButton = commandButton("DONE", COLOR_GREEN);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showHub();
            }
        });
        TextView exportButton = commandButton("EXPORT", COLOR_PANEL);
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportConfiguration();
            }
        });
        header.addView(exportButton, headerButtonParams());

        TextView importButton = commandButton("IMPORT", COLOR_PANEL);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                importConfiguration();
            }
        });
        header.addView(importButton, headerButtonParams());
        header.addView(doneButton, headerButtonParams());
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(buildAutomationRow());
        if (isAccessibilityServiceEnabled(TvRemoteAccessibilityService.class)
                || new TvRemoteVariantResolver(this).hasInstalledTvVariant()) {
            list.addView(buildTvRemoteRow());
        }
        list.addView(buildZwiftBridgeRow());
        List<MediaLauncher.MediaApp> apps = MediaLauncher.orderedInstalledApps(this);
        for (int index = 0; index < apps.size(); index++) {
            list.addView(buildManagerRow(apps.get(index), index, apps.size()));
        }
        for (MediaLauncher.MediaApp app : MediaLauncher.missingApps(this)) {
            list.addView(buildMissingAppRow(app));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setPadding(0, dp(24), 0, 0);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View buildAutomationRow() {
        final boolean enabled =
                StatsOverlayManager.isSubscriptionPromptAutomationEnabled(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        row.setBackground(roundedBackground(COLOR_PANEL, 6, 0xFF3A3D42));

        TextView label = text("Subscription prompt automation", 20, Color.WHITE, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, labelParams);

        TextView toggle = managerButton(enabled ? "ON" : "OFF");
        toggle.setContentDescription(
                "Subscription prompt automation " + (enabled ? "on" : "off"));
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StatsOverlayManager.setSubscriptionPromptAutomationEnabled(
                        MainActivity.this, !enabled);
                showAppManager();
            }
        });
        row.addView(toggle, managerButtonParams());

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100));
        rowParams.bottomMargin = dp(12);
        row.setLayoutParams(rowParams);
        return row;
    }

    private View buildZwiftBridgeRow() {
        final boolean enabled = ZwiftBleBridge.isEnabled(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        row.setBackground(roundedBackground(COLOR_PANEL, 6, 0xFF3A3D42));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("Zwift Companion sensor bridge", 20, Color.WHITE, true);
        labels.addView(label);
        zwiftBridgeStatus = text(
                RideStarterAccessibilityService.zwiftBridgeStatus(this),
                14, COLOR_MUTED, true);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(6);
        labels.addView(zwiftBridgeStatus, statusParams);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        zwiftBridgeToggle = managerButton(enabled ? "ON" : "OFF");
        zwiftBridgeToggle.setContentDescription(
                "Zwift Companion sensor bridge " + (enabled ? "on" : "off"));
        zwiftBridgeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean next = !ZwiftBleBridge.isEnabled(MainActivity.this);
                boolean applied = RideStarterAccessibilityService.setZwiftBridgeEnabled(
                        MainActivity.this, next);
                String message;
                if (!next) {
                    message = "Zwift sensor bridge stopped";
                } else if (applied) {
                    message = "Bridge starting. In Zwift, pair through Zwift Companion.";
                } else {
                    message = "Bridge saved. Enable the SARO accessibility service to start it.";
                }
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                showAppManager();
            }
        });
        row.addView(zwiftBridgeToggle, managerButtonParams());

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
        rowParams.bottomMargin = dp(12);
        row.setLayoutParams(rowParams);
        return row;
    }

    private View buildTvRemoteRow() {
        boolean enabled = isAccessibilityServiceEnabled(
                TvRemoteAccessibilityService.class);
        boolean connected = TvRemoteAccessibilityService.isConnected();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        row.setBackground(roundedBackground(COLOR_PANEL, 6, 0xFF3A3D42));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("TV app touchscreen remote", 20, Color.WHITE, true));
        TvRemoteServiceState.State state = tvRemoteServiceState.evaluate(
                enabled, connected, android.os.SystemClock.elapsedRealtime());
        tvRemoteStatus = text(tvRemoteStatusLabel(state), 14,
                tvRemoteStatusColor(state), true);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(6);
        labels.addView(tvRemoteStatus, statusParams);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = managerButton("SETTINGS");
        settings.setContentDescription("Open Accessibility settings for SARO TV Remote");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (RuntimeException exception) {
                    Toast.makeText(MainActivity.this,
                            "Accessibility settings are unavailable", Toast.LENGTH_LONG).show();
                }
            }
        });
        row.addView(settings, managerButtonParams());

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
        rowParams.bottomMargin = dp(12);
        row.setLayoutParams(rowParams);
        return row;
    }

    private boolean isAccessibilityServiceEnabled(Class<?> serviceClass) {
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            return false;
        }
        ComponentName expected = new ComponentName(this, serviceClass);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName current = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(current)) {
                return true;
            }
        }
        return false;
    }

    private View buildMissingAppRow(final MediaLauncher.MediaApp app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        row.setBackground(roundedBackground(0xFF181A1D, 6, 0xFF303338));

        ImageView icon = appIcon(app);
        icon.setAlpha(0.45f);
        row.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView label = text(app.displayName, 20, COLOR_MUTED, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(18);
        row.addView(label, labelParams);

        TextView installButton = managerButton("INSTALL");
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!MediaLauncher.launchStoreListing(MainActivity.this, app.packageName)) {
                    Toast.makeText(MainActivity.this, "App Store is unavailable",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        row.addView(installButton, managerButtonParams());

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100));
        rowParams.bottomMargin = dp(12);
        row.setLayoutParams(rowParams);
        return row;
    }

    private void exportConfiguration() {
        try {
            ConfigStore.exportToDefaultFile(this);
            Toast.makeText(this, "Configuration exported", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "Export failed: " + exception.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void importConfiguration() {
        try {
            ConfigStore.importFromDefaultFile(this);
            Toast.makeText(this, "Configuration restored", Toast.LENGTH_SHORT).show();
            showAppManager();
        } catch (Exception exception) {
            Toast.makeText(this, "Import failed: " + exception.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private View buildManagerRow(final MediaLauncher.MediaApp app, int index, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        row.setBackground(roundedBackground(COLOR_PANEL, 6, 0xFF3A3D42));

        ImageView icon = appIcon(app);
        row.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView label = text(app.displayName, 20, Color.WHITE, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(18);
        row.addView(label, labelParams);

        TextView upButton = managerButton("MOVE UP");
        upButton.setEnabled(index > 0);
        upButton.setAlpha(index > 0 ? 1f : 0.35f);
        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaLauncher.move(MainActivity.this, app.packageName, -1);
                showAppManager();
            }
        });
        row.addView(upButton, managerButtonParams());

        TextView downButton = managerButton("MOVE DOWN");
        downButton.setEnabled(index < count - 1);
        downButton.setAlpha(index < count - 1 ? 1f : 0.35f);
        downButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaLauncher.move(MainActivity.this, app.packageName, 1);
                showAppManager();
            }
        });
        row.addView(downButton, managerButtonParams());

        final boolean hidden = MediaLauncher.isHidden(this, app.packageName);
        TextView visibilityButton = managerButton(hidden ? "SHOW" : "HIDE");
        visibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaLauncher.setHidden(MainActivity.this, app.packageName, !hidden);
                showAppManager();
            }
        });
        row.addView(visibilityButton, managerButtonParams());

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100));
        rowParams.bottomMargin = dp(12);
        row.setLayoutParams(rowParams);
        return row;
    }

    private LinearLayout buildAppRows(List<MediaLauncher.MediaApp> apps) {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int index = 0;
        while (index < apps.size()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            for (int column = 0; column < 4; column++) {
                if (index < apps.size()) {
                    row.addView(buildAppButton(apps.get(index)), appTileParams());
                    index += 1;
                } else {
                    View spacer = new View(this);
                    row.addView(spacer, appTileParams());
                }
            }
            rows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));
        }
        return rows;
    }

    private View buildAppButton(final MediaLauncher.MediaApp app) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(18), dp(18), dp(18), dp(18));
        tile.setBackground(roundedBackground(COLOR_PANEL, 6, 0xFF3A3D42));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription("Open " + app.displayName);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!MediaLauncher.launch(MainActivity.this, app.packageName)) {
                    Toast.makeText(MainActivity.this, app.displayName + " is unavailable",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        ImageView icon = appIcon(app);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(78), dp(78)));

        TextView label = text(app.displayName, 19, Color.WHITE, true);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(12);
        tile.addView(label, labelParams);
        return tile;
    }

    private ImageView appIcon(MediaLauncher.MediaApp app) {
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Drawable drawable = getPackageManager().getApplicationIcon(
                    MediaLauncher.installedPackageName(this, app.packageName));
            icon.setImageDrawable(drawable);
        } catch (PackageManager.NameNotFoundException ignored) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        return icon;
    }

    private void startOrReturnToRide() {
        RideSessionStore.Session current = RideSessionStore.read(this);
        if (current.active) {
            if (returnToPreviousMediaTask) {
                returnToPreviousMediaTask = false;
                if (moveTaskToBack(true)) {
                    return;
                }
            }
            if (!MediaLauncher.launchLast(this)) {
                Toast.makeText(this, "Choose an app to continue the local ride.",
                        Toast.LENGTH_SHORT).show();
                updateRideState();
            }
            return;
        }
        if (!RideStarterAccessibilityService.isOverlayReady()) {
            Toast.makeText(this,
                    "Enable the SARO Accessibility service before starting a ride.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        RideEngine rideEngine = RideEngine.get(this);
        if (!rideEngine.isSensorReady()) {
            rideEngine.reconnectSensors();
            Toast.makeText(this,
                    "Reconnecting to bike sensors. Try again in a moment.",
                    Toast.LENGTH_LONG).show();
            updateSensorState();
            return;
        }
        rideEngine.startLocalRide();
        RideStarterAccessibilityService.refreshOverlay();
        if (!MediaLauncher.launchLast(this)) {
            Toast.makeText(this, "SARO Local Ride started. Choose an app.",
                    Toast.LENGTH_SHORT).show();
            updateRideState();
        }
    }

    private void readLaunchMode(Intent intent) {
        returnToPreviousMediaTask = intent != null
                && intent.getBooleanExtra(MediaLauncher.EXTRA_RETURN_TO_MEDIA_TASK, false);
    }

    private void confirmEndRide() {
        new AlertDialog.Builder(this)
                .setTitle("End SARO Local Ride?")
                .setMessage("The latest local summary will remain visible on this tablet.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("END RIDE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RideSessionStore.Session ended = RideEngine.get(MainActivity.this).endRide();
                        RideStarterAccessibilityService.refreshOverlay();
                        Toast.makeText(MainActivity.this,
                                "Local ride ended: " + formatElapsed(ended.elapsedMs),
                                Toast.LENGTH_SHORT).show();
                        updateRideState();
                    }
                })
                .show();
    }

    private void updateRideState() {
        updateZwiftBridgeState();
        updateTvRemoteState();
        updateSensorState();
        if (rideState == null) {
            return;
        }
        RideSessionStore.Session session = RideSessionStore.read(this);
        String lastPackage = MediaLauncher.lastPackage(this);
        String mediaName = lastPackage.isEmpty()
                ? "NO MEDIA SELECTED"
                : MediaLauncher.displayNameForPackage(lastPackage).toUpperCase(Locale.US);
        if (!session.active) {
            RideSessionStore.LastRide lastRide = RideSessionStore.lastRide(this);
            if (lastRide.available) {
                rideState.setText("LAST LOCAL " + formatElapsed(lastRide.elapsedMs)
                        + "  |  " + Math.round(lastRide.outputKilojoules) + " KJ"
                        + "  |  " + String.format(Locale.US, "%.2f MI", lastRide.distanceMiles));
            } else {
                rideState.setText(mediaName);
            }
            startButton.setText("START LOCAL RIDE");
            pauseButton.setVisibility(View.GONE);
            endButton.setVisibility(View.GONE);
            return;
        }

        rideState.setText((session.paused ? "LOCAL RIDE PAUSED" : "LOCAL RIDE ACTIVE")
                + "  |  " + formatElapsed(session.elapsedMs));
        startButton.setText(lastPackage.isEmpty() ? "CHOOSE APP" : "RETURN TO VIDEO");
        pauseButton.setText(session.paused ? "RESUME" : "PAUSE");
        pauseButton.setVisibility(View.VISIBLE);
        endButton.setVisibility(View.VISIBLE);
    }

    private void updateSensorState() {
        if (sensorStatus == null || sensorDetail == null || sensorAction == null) {
            return;
        }
        SensorRepository.Snapshot sensor = RideEngine.get(this).sensorSnapshot();
        SensorHealth.State state = SensorHealth.evaluate(
                RideStarterAccessibilityService.isOverlayReady(), sensor.connected,
                sensor.hasSample, sensor.fresh);
        sensorAction.setVisibility(View.VISIBLE);
        sensorAction.setEnabled(true);
        switch (state) {
            case OVERLAY_DISABLED:
                sensorStatus.setText("OVERLAY OFF");
                sensorStatus.setTextColor(COLOR_RED);
                sensorDetail.setText("Ride overlay unavailable");
                sensorAction.setText("ENABLE OVERLAY");
                sensorAction.setContentDescription("Open Accessibility settings");
                break;
            case READY:
                sensorStatus.setText("SENSORS READY");
                sensorStatus.setTextColor(COLOR_GREEN);
                sensorDetail.setText("CAD " + sensor.cadenceRpm
                        + "  |  " + Math.round(sensor.outputWatts) + " W"
                        + "  |  RES " + sensor.resistance);
                sensorAction.setVisibility(View.INVISIBLE);
                sensorAction.setEnabled(false);
                break;
            case WAITING:
                sensorStatus.setText("SENSORS WAITING");
                sensorStatus.setTextColor(0xFFE0A43A);
                sensorDetail.setText("Waiting for the first bike sample");
                sensorAction.setText("RECONNECT");
                sensorAction.setContentDescription("Reconnect bike sensors");
                break;
            case STALE:
                sensorStatus.setText("SENSOR DATA STALE");
                sensorStatus.setTextColor(0xFFE0A43A);
                sensorDetail.setText("Last update " + formatSensorAge(sensor.ageMs) + " ago");
                sensorAction.setText("RECONNECT");
                sensorAction.setContentDescription("Reconnect bike sensors");
                break;
            case CONNECTING:
            default:
                sensorStatus.setText("SENSORS CONNECTING");
                sensorStatus.setTextColor(0xFFE0A43A);
                sensorDetail.setText("Waiting for bike telemetry");
                sensorAction.setText("RETRY");
                sensorAction.setContentDescription("Retry bike sensor connection");
                break;
        }
    }

    private void recoverSensorRuntime() {
        if (!RideStarterAccessibilityService.isOverlayReady()) {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (RuntimeException exception) {
                Toast.makeText(this, "Accessibility settings are unavailable",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        RideEngine.get(this).reconnectSensors();
        Toast.makeText(this, "Reconnecting bike sensors", Toast.LENGTH_SHORT).show();
        updateSensorState();
    }

    private static String formatSensorAge(long ageMs) {
        long seconds = Math.max(1L, ageMs / 1000L);
        return seconds < 60L ? seconds + "s" : (seconds / 60L) + "m";
    }

    private void updateZwiftBridgeState() {
        if (zwiftBridgeStatus == null || zwiftBridgeToggle == null) {
            return;
        }
        boolean enabled = ZwiftBleBridge.isEnabled(this);
        zwiftBridgeToggle.setText(enabled ? "ON" : "OFF");
        zwiftBridgeStatus.setText(
                RideStarterAccessibilityService.zwiftBridgeStatus(this));
    }

    private void updateTvRemoteState() {
        if (tvRemoteStatus == null) {
            return;
        }
        boolean enabled = isAccessibilityServiceEnabled(
                TvRemoteAccessibilityService.class);
        TvRemoteServiceState.State state = tvRemoteServiceState.evaluate(enabled,
                TvRemoteAccessibilityService.isConnected(),
                android.os.SystemClock.elapsedRealtime());
        tvRemoteStatus.setText(tvRemoteStatusLabel(state));
        tvRemoteStatus.setTextColor(tvRemoteStatusColor(state));
    }

    private static String tvRemoteStatusLabel(TvRemoteServiceState.State state) {
        switch (state) {
            case READY:
                return "READY";
            case STARTING:
                return "STARTING";
            case NOT_RUNNING:
                return "ENABLED, NOT RUNNING";
            case OFF:
            default:
                return "OFF";
        }
    }

    private static int tvRemoteStatusColor(TvRemoteServiceState.State state) {
        if (state == TvRemoteServiceState.State.READY) {
            return COLOR_GREEN;
        }
        if (state == TvRemoteServiceState.State.OFF) {
            return COLOR_MUTED;
        }
        return 0xFFE0A43A;
    }

    private TextView commandButton(String label, int color) {
        TextView button = text(label, 18, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(roundedBackground(color, 6, 0x50FFFFFF));
        return button;
    }

    private TextView managerButton(String label) {
        TextView button = commandButton(label, COLOR_BACKGROUND);
        button.setTextSize(14);
        return button;
    }

    private LinearLayout.LayoutParams managerButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(180), dp(56));
        params.leftMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams headerButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(180), dp(58));
        params.leftMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(68), 1f);
        params.rightMargin = dp(14);
        return params;
    }

    private LinearLayout.LayoutParams primaryActionParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(68), weight);
        params.rightMargin = dp(14);
        return params;
    }

    private LinearLayout.LayoutParams appTileParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.rightMargin = dp(14);
        params.bottomMargin = dp(14);
        return params;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable roundedBackground(int color, int radiusDp, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), strokeColor);
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
}
