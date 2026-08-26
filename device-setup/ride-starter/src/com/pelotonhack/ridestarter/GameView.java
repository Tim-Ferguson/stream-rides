package com.pelotonhack.ridestarter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

final class GameView extends View {
    private static final float FIXED_STEP_SECONDS = 1f / 60f;
    private static final int MAX_STEPS_PER_FRAME = 30;
    interface Host {
        void exitGames();
    }

    private static final int COLOR_BACKGROUND = 0xFF0F1114;
    private static final int COLOR_PANEL = 0xFF202328;
    private static final int COLOR_PANEL_BORDER = 0xFF3A3E45;
    private static final int COLOR_TEXT = 0xFFF4F5F7;
    private static final int COLOR_MUTED = 0xFFAEB4BC;
    private static final int COLOR_GREEN = 0xFF35D07F;
    private static final int COLOR_YELLOW = 0xFFFFC857;
    private static final int COLOR_RED = 0xFFFF5C5C;
    private static final int[] MODE_COLORS = {
            0xFF2FBA72, 0xFF3A9EC5, 0xFFD39A38,
            0xFF7E5BD6, 0xFFE06073, 0xFF4D83D1
    };

    private final Host host;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RideEngine rideEngine;
    private final GameProgressStore progressStore;
    private final GameSprites gameSprites;
    private final RectF exitRect = new RectF();
    private final RectF menuRect = new RectF();
    private final RectF pauseRect = new RectF();
    private final RectF demoBoostRect = new RectF();
    private final RectF demoModeRect = new RectF();
    private final RectF resumeRect = new RectF();
    private final RectF pickerRect = new RectF();
    private final RectF replayRect = new RectF();
    private final RectF[] modeRects = new RectF[GameMode.values().length];
    private final RectF[] upgradeRects = new RectF[GameProgressStore.Upgrade.values().length];

    private boolean pickerVisible = true;
    private boolean paused;
    private boolean hostActive;
    private boolean resultRecorded;
    private boolean newBest;
    private boolean sensorFresh;
    private boolean demoAllowed;
    private boolean sessionDemoMode;
    private GameSession session;
    private long lastFrameAt;
    private float scale = 1f;
    private float smoothedCadence;
    private float smoothedWatts;
    private float smoothedResistance;
    private float demoBoost;
    private float physicsAccumulator;
    private boolean demoInput = true;
    private String message = "";
    private long messageUntil;

    GameView(Context context, Host host) {
        super(context);
        this.host = host;
        rideEngine = RideEngine.get(context);
        progressStore = new GameProgressStore(context);
        gameSprites = new GameSprites(context);
        for (int index = 0; index < modeRects.length; index++) {
            modeRects[index] = new RectF();
        }
        for (int index = 0; index < upgradeRects.length; index++) {
            upgradeRects[index] = new RectF();
        }
        paint.setStrokeCap(Paint.Cap.ROUND);
        setBackgroundColor(COLOR_BACKGROUND);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription("SARO Games");
    }

    void onHostResume() {
        hostActive = true;
        lastFrameAt = SystemClock.elapsedRealtime();
        requestFocus();
        postInvalidateOnAnimation();
    }

    void onHostPause() {
        hostActive = false;
        if (session != null && !session.isFinished()) {
            paused = true;
        }
    }

    boolean handleBack() {
        if (!pickerVisible) {
            showPicker();
            return true;
        }
        return false;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        scale = Math.max(0.68f, Math.min(1.45f,
                Math.min(width / 1920f, height / 1080f)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.elapsedRealtime();
        float dt = Math.min(0.5f, Math.max(0f, (now - lastFrameAt) / 1000f));
        lastFrameAt = now;
        GameSession.Input input = readInput(now, dt);

        canvas.drawColor(COLOR_BACKGROUND);
        if (pickerVisible) {
            drawPicker(canvas, input);
        } else {
            if (!paused && !isSensorPaused() && session != null && !session.isFinished()) {
                physicsAccumulator += dt;
                int steps = 0;
                while (physicsAccumulator >= FIXED_STEP_SECONDS
                        && steps < MAX_STEPS_PER_FRAME) {
                    session.update(FIXED_STEP_SECONDS, input);
                    physicsAccumulator -= FIXED_STEP_SECONDS;
                    steps += 1;
                }
                if (steps == MAX_STEPS_PER_FRAME) {
                    physicsAccumulator = 0f;
                }
            }
            if (session != null && session.isFinished() && !resultRecorded) {
                if (sessionDemoMode) {
                    newBest = false;
                } else {
                    newBest = progressStore.recordResult(
                            session.mode(), session.score(), session.rewardCredits());
                }
                resultRecorded = true;
            }
            drawGame(canvas, input);
        }
        drawMessage(canvas, now);

        if (hostActive) {
            postInvalidateOnAnimation();
        }
    }

    private GameSession.Input readInput(long now, float dt) {
        SensorRepository.Snapshot snapshot = rideEngine.sensorSnapshot();
        sensorFresh = snapshot.fresh;
        boolean useDemo = pickerVisible ? demoAllowed : sessionDemoMode;
        demoInput = useDemo;
        float cadence;
        float watts;
        float resistance;
        if (snapshot.fresh && !useDemo) {
            cadence = snapshot.cadenceRpm;
            watts = snapshot.outputWatts;
            resistance = snapshot.resistance;
            demoBoost = 0f;
        } else if (useDemo) {
            float seconds = now / 1000f;
            demoBoost = Math.max(0f, demoBoost - dt * 16f);
            cadence = 70f + 13f * (float) Math.sin(seconds * 0.72f) + demoBoost;
            watts = 135f + 58f * (float) Math.sin(seconds * 0.51f + 0.8f)
                    + demoBoost * 3.2f;
            resistance = 41f + 12f * (float) Math.sin(seconds * 0.19f + 1.4f);
        } else {
            cadence = 0f;
            watts = 0f;
            resistance = 0f;
            demoBoost = 0f;
        }
        float blend = Math.min(1f, Math.max(0.18f, dt * 8f));
        smoothedCadence += (cadence - smoothedCadence) * blend;
        smoothedWatts += (watts - smoothedWatts) * blend;
        smoothedResistance += (resistance - smoothedResistance) * blend;
        return new GameSession.Input(
                smoothedCadence, smoothedWatts, smoothedResistance, demoInput);
    }

    private void drawPicker(Canvas canvas, GameSession.Input input) {
        float width = getWidth();
        float height = getHeight();
        float margin = 28f * scale;
        float minimumTouch = minimumTouchPx();
        float headerHeight = Math.max(116f * scale, minimumTouch + 36f * scale);
        exitRect.set(margin, 18f * scale, margin + 132f * scale,
                18f * scale + minimumTouch);
        drawButton(canvas, exitRect, "EXIT", COLOR_PANEL, COLOR_TEXT);
        drawText(canvas, "SARO GAMES", exitRect.right + 24f * scale,
                67f * scale, 34f * scale, COLOR_TEXT, Paint.Align.LEFT, true);
        drawText(canvas, "Pedal-powered training games",
                exitRect.right + 24f * scale, 94f * scale,
                17f * scale, COLOR_MUTED, Paint.Align.LEFT, false);
        demoModeRect.set(width - margin - 188f * scale, 18f * scale,
                width - margin, 18f * scale + minimumTouch);
        drawButton(canvas, demoModeRect, demoAllowed ? "DEMO ON" : "DEMO OFF",
                demoAllowed ? 0xFF355A68 : COLOR_PANEL, COLOR_TEXT);
        drawSensorBadge(canvas, demoModeRect.left - 18f * scale, 34f * scale, input);

        float cardsTop = headerHeight + 28f * scale;
        float upgradesHeight = 138f * scale;
        float cardsBottom = height - upgradesHeight - 22f * scale;
        float gap = 16f * scale;
        float columnWidth = (width - margin * 2f - gap) / 2f;
        float rowHeight = (cardsBottom - cardsTop - gap * 2f) / 3f;
        GameMode[] modes = GameMode.values();
        for (int index = 0; index < modes.length; index++) {
            int row = index / 2;
            int column = index % 2;
            RectF bounds = modeRects[index];
            float left = margin + column * (columnWidth + gap);
            float top = cardsTop + row * (rowHeight + gap);
            bounds.set(left, top, left + columnWidth, top + rowHeight);
            drawModeCard(canvas, bounds, modes[index], MODE_COLORS[index]);
        }

        float upgradesTop = height - upgradesHeight;
        drawText(canvas, "DRAG GARAGE", margin, upgradesTop + 24f * scale,
                14f * scale, COLOR_MUTED, Paint.Align.LEFT, true);
        drawText(canvas, progressStore.credits() + " CREDITS", margin,
                upgradesTop + 62f * scale, 25f * scale, COLOR_YELLOW,
                Paint.Align.LEFT, true);
        float upgradeLeft = margin + 250f * scale;
        float upgradeGap = 12f * scale;
        float upgradeWidth = (width - upgradeLeft - margin - upgradeGap * 2f) / 3f;
        GameProgressStore.Upgrade[] upgrades = GameProgressStore.Upgrade.values();
        for (int index = 0; index < upgrades.length; index++) {
            RectF bounds = upgradeRects[index];
            float left = upgradeLeft + index * (upgradeWidth + upgradeGap);
            bounds.set(left, upgradesTop + 15f * scale,
                    left + upgradeWidth, height - 24f * scale);
            drawUpgrade(canvas, bounds, upgrades[index]);
        }
    }

    private void drawModeCard(Canvas canvas, RectF bounds, GameMode mode, int accent) {
        drawPanel(canvas, bounds, COLOR_PANEL, COLOR_PANEL_BORDER, 7f * scale);
        paint.setColor(accent);
        canvas.drawRoundRect(bounds.left, bounds.top,
                bounds.left + 10f * scale, bounds.bottom,
                7f * scale, 7f * scale, paint);
        float left = bounds.left + 30f * scale;
        float spriteSize = Math.min(bounds.height() - 54f * scale, 158f * scale);
        gameSprites.draw(canvas, mode.ordinal(),
                bounds.right - 34f * scale - spriteSize / 2f,
                bounds.top + 24f * scale + spriteSize / 2f,
                spriteSize, spriteSize, paint);
        drawText(canvas, mode.title, left, bounds.top + 43f * scale,
                27f * scale, COLOR_TEXT, Paint.Align.LEFT, true);
        drawWrappedText(canvas, mode.subtitle, left, bounds.top + 75f * scale,
                bounds.width() - spriteSize - 86f * scale,
                17f * scale, COLOR_MUTED, 2);
        float best = progressStore.best(mode);
        drawText(canvas, mode.scoreLabel + "  " + mode.formatScore(best),
                left, bounds.bottom - 22f * scale, 16f * scale,
                Float.isNaN(best) ? COLOR_MUTED : accent,
                Paint.Align.LEFT, true);
        drawText(canvas, "PLAY", bounds.right - 25f * scale,
                bounds.bottom - 22f * scale, 17f * scale, COLOR_TEXT,
                Paint.Align.RIGHT, true);
    }

    private void drawUpgrade(Canvas canvas, RectF bounds, GameProgressStore.Upgrade upgrade) {
        int level = progressStore.upgradeLevel(upgrade);
        boolean maxed = progressStore.isMaxed(upgrade);
        int cost = progressStore.upgradeCost(upgrade);
        int color = maxed ? 0xFF2F684D
                : progressStore.credits() >= cost ? 0xFF2A3F35 : COLOR_PANEL;
        drawPanel(canvas, bounds, color, COLOR_PANEL_BORDER, 6f * scale);
        drawText(canvas, upgrade.label.toUpperCase(Locale.US) + "  " + level + "/5",
                bounds.left + 20f * scale, bounds.top + 34f * scale,
                18f * scale, COLOR_TEXT, Paint.Align.LEFT, true);
        drawText(canvas, maxed ? "MAX" : cost + " CREDITS",
                bounds.left + 20f * scale, bounds.bottom - 19f * scale,
                15f * scale, maxed ? COLOR_GREEN : COLOR_YELLOW,
                Paint.Align.LEFT, true);
        drawText(canvas, maxed ? "" : "UPGRADE",
                bounds.right - 18f * scale, bounds.bottom - 19f * scale,
                15f * scale, COLOR_TEXT, Paint.Align.RIGHT, true);
    }

    private void drawGame(Canvas canvas, GameSession.Input input) {
        float width = getWidth();
        float height = getHeight();
        float margin = 22f * scale;
        float headerBottom = 94f * scale;
        float footerTop = height - 104f * scale;

        float minimumTouch = minimumTouchPx();
        menuRect.set(margin, 12f * scale, margin + 142f * scale,
                12f * scale + minimumTouch);
        pauseRect.set(width - margin - 154f * scale, 18f * scale,
                width - margin, 18f * scale + minimumTouch);
        drawButton(canvas, menuRect, "MODES", COLOR_PANEL, COLOR_TEXT);
        drawButton(canvas, pauseRect, paused ? "RESUME" : "PAUSE",
                paused ? COLOR_GREEN : COLOR_PANEL, COLOR_TEXT);
        drawText(canvas, session.mode().title.toUpperCase(Locale.US),
                width / 2f, 55f * scale, 30f * scale,
                COLOR_TEXT, Paint.Align.CENTER, true);
        drawInputMetrics(canvas, input, headerBottom);

        RectF playArea = new RectF(margin, headerBottom + 54f * scale,
                width - margin, footerTop - 10f * scale);
        session.draw(canvas, paint, playArea, scale, gameSprites);

        paint.setColor(0xFF171A1E);
        canvas.drawRect(0f, footerTop, width, height, paint);
        drawText(canvas, session.status(), margin, footerTop + 38f * scale,
                20f * scale, COLOR_TEXT, Paint.Align.LEFT, true);
        drawText(canvas, session.hint(), margin, footerTop + 73f * scale,
                17f * scale, COLOR_MUTED, Paint.Align.LEFT, false);

        if (sessionDemoMode && !paused && !session.isFinished()) {
            demoBoostRect.set(width - margin - 230f * scale,
                    footerTop + 18f * scale, width - margin,
                    Math.max(footerTop + 18f * scale + minimumTouch,
                            height - 17f * scale));
            drawButton(canvas, demoBoostRect, "DEMO BOOST", 0xFF355A68, COLOR_TEXT);
        } else {
            demoBoostRect.setEmpty();
        }

        if (isSensorPaused()) {
            drawSensorLossOverlay(canvas);
        } else if (paused) {
            drawPauseOverlay(canvas);
        } else if (session.isFinished()) {
            drawResultOverlay(canvas);
        }
    }

    private void drawInputMetrics(Canvas canvas, GameSession.Input input, float y) {
        float center = getWidth() / 2f;
        float gap = 214f * scale;
        drawMetric(canvas, center - gap, y, "CADENCE",
                String.format(Locale.US, "%.0f", input.cadence));
        drawMetric(canvas, center, y, "WATTS",
                String.format(Locale.US, "%.0f", input.watts));
        drawMetric(canvas, center + gap, y, "RESISTANCE",
                String.format(Locale.US, "%.0f", input.resistance));
        drawSensorBadge(canvas, getWidth() - 20f * scale, y + 3f * scale, input);
    }

    private void drawMetric(Canvas canvas, float x, float y, String label, String value) {
        drawText(canvas, value, x, y + 28f * scale,
                28f * scale, COLOR_TEXT, Paint.Align.CENTER, true);
        drawText(canvas, label, x, y + 49f * scale,
                12f * scale, COLOR_MUTED, Paint.Align.CENTER, true);
    }

    private void drawSensorBadge(Canvas canvas, float right, float top,
                                 GameSession.Input input) {
        String label = sessionDemoMode || (pickerVisible && demoAllowed)
                ? "DEMO MODE" : sensorFresh ? "LIVE SENSOR" : "SENSOR WAITING";
        int color = sessionDemoMode || (pickerVisible && demoAllowed) || !sensorFresh
                ? COLOR_YELLOW : COLOR_GREEN;
        paint.setTextSize(14f * scale);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        float width = paint.measureText(label) + 28f * scale;
        RectF bounds = new RectF(right - width, top, right, top + 34f * scale);
        paint.setColor(0xFF25282D);
        canvas.drawRoundRect(bounds, 6f * scale, 6f * scale, paint);
        paint.setColor(color);
        canvas.drawCircle(bounds.left + 13f * scale,
                bounds.centerY(), 4f * scale, paint);
        drawText(canvas, label, bounds.right - 10f * scale,
                bounds.top + 23f * scale, 14f * scale, color,
                Paint.Align.RIGHT, true);
    }

    private void drawPauseOverlay(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        paint.setColor(0xD9000000);
        canvas.drawRect(0f, 0f, width, height, paint);
        RectF panel = centeredPanel(width, height, 640f * scale, 330f * scale);
        drawPanel(canvas, panel, 0xFF202328, COLOR_PANEL_BORDER, 8f * scale);
        drawText(canvas, "GAME PAUSED", panel.centerX(), panel.top + 72f * scale,
                34f * scale, COLOR_TEXT, Paint.Align.CENTER, true);
        resumeRect.set(panel.left + 42f * scale, panel.top + 122f * scale,
                panel.right - 42f * scale, panel.top + 204f * scale);
        pickerRect.set(panel.left + 42f * scale, panel.top + 222f * scale,
                panel.right - 42f * scale, panel.bottom - 28f * scale);
        drawButton(canvas, resumeRect, "RESUME", COLOR_GREEN, 0xFF07150D);
        drawButton(canvas, pickerRect, "CHOOSE MODE", COLOR_PANEL_BORDER, COLOR_TEXT);
    }

    private void drawSensorLossOverlay(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        paint.setColor(0xD9000000);
        canvas.drawRect(0f, 0f, width, height, paint);
        RectF panel = centeredPanel(width, height, 700f * scale, 250f * scale);
        drawPanel(canvas, panel, 0xFF202328, COLOR_PANEL_BORDER, 8f * scale);
        drawText(canvas, "SENSOR CONNECTION LOST", panel.centerX(),
                panel.top + 82f * scale, 30f * scale, COLOR_YELLOW,
                Paint.Align.CENTER, true);
        drawText(canvas, "Live game paused until fresh bike data returns",
                panel.centerX(), panel.top + 135f * scale, 18f * scale,
                COLOR_TEXT, Paint.Align.CENTER, false);
        pickerRect.set(panel.left + 50f * scale, panel.top + 162f * scale,
                panel.right - 50f * scale, panel.bottom - 24f * scale);
        drawButton(canvas, pickerRect, "CHOOSE MODE", COLOR_PANEL_BORDER, COLOR_TEXT);
    }

    private void drawResultOverlay(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        paint.setColor(0xD9000000);
        canvas.drawRect(0f, 0f, width, height, paint);
        RectF panel = centeredPanel(width, height, 700f * scale, 390f * scale);
        drawPanel(canvas, panel, 0xFF202328, COLOR_PANEL_BORDER, 8f * scale);
        drawText(canvas, sessionDemoMode ? "DEMO COMPLETE - NOT SAVED"
                        : newBest ? "NEW BEST" : "SESSION COMPLETE",
                panel.centerX(), panel.top + 63f * scale,
                24f * scale, newBest ? COLOR_GREEN : COLOR_MUTED,
                Paint.Align.CENTER, true);
        drawText(canvas, session.mode().formatScore(session.score()),
                panel.centerX(), panel.top + 132f * scale,
                50f * scale, COLOR_TEXT, Paint.Align.CENTER, true);
        drawText(canvas, sessionDemoMode ? "NO CREDITS IN DEMO"
                        : "+" + session.rewardCredits() + " CREDITS",
                panel.centerX(), panel.top + 172f * scale,
                20f * scale, COLOR_YELLOW, Paint.Align.CENTER, true);
        replayRect.set(panel.left + 44f * scale, panel.top + 218f * scale,
                panel.right - 44f * scale, panel.top + 296f * scale);
        pickerRect.set(panel.left + 44f * scale, panel.top + 310f * scale,
                panel.right - 44f * scale, panel.bottom - 24f * scale);
        drawButton(canvas, replayRect, "PLAY AGAIN", COLOR_GREEN, 0xFF07150D);
        drawButton(canvas, pickerRect, "CHOOSE MODE", COLOR_PANEL_BORDER, COLOR_TEXT);
    }

    private RectF centeredPanel(float width, float height, float panelWidth, float panelHeight) {
        return new RectF((width - panelWidth) / 2f, (height - panelHeight) / 2f,
                (width + panelWidth) / 2f, (height + panelHeight) / 2f);
    }

    private void drawMessage(Canvas canvas, long now) {
        if (message.length() == 0 || now >= messageUntil) {
            return;
        }
        paint.setTextSize(18f * scale);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        float width = paint.measureText(message) + 46f * scale;
        RectF bounds = new RectF((getWidth() - width) / 2f, 20f * scale,
                (getWidth() + width) / 2f, 75f * scale);
        paint.setColor(0xEF30343A);
        canvas.drawRoundRect(bounds, 6f * scale, 6f * scale, paint);
        drawText(canvas, message, bounds.centerX(), bounds.top + 36f * scale,
                18f * scale, COLOR_TEXT, Paint.Align.CENTER, true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        performClick();
        float x = event.getX();
        float y = event.getY();
        if (pickerVisible) {
            handlePickerTouch(x, y);
        } else {
            handleGameTouch(x, y);
        }
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handlePickerTouch(float x, float y) {
        if (exitRect.contains(x, y)) {
            host.exitGames();
            return;
        }
        if (demoModeRect.contains(x, y)) {
            demoAllowed = !demoAllowed;
            showMessage(demoAllowed ? "Demo mode enabled" : "Live sensor required");
            return;
        }
        GameMode[] modes = GameMode.values();
        for (int index = 0; index < modeRects.length; index++) {
            if (modeRects[index].contains(x, y)) {
                startMode(modes[index]);
                return;
            }
        }
        GameProgressStore.Upgrade[] upgrades = GameProgressStore.Upgrade.values();
        for (int index = 0; index < upgradeRects.length; index++) {
            if (!upgradeRects[index].contains(x, y)) {
                continue;
            }
            GameProgressStore.Upgrade upgrade = upgrades[index];
            if (progressStore.isMaxed(upgrade)) {
                showMessage(upgrade.label + " is already maxed");
            } else if (progressStore.buyUpgrade(upgrade)) {
                showMessage(upgrade.label + " upgraded");
            } else {
                showMessage("More credits needed");
            }
            return;
        }
    }

    private void handleGameTouch(float x, float y) {
        if (isSensorPaused()) {
            if (pickerRect.contains(x, y) || menuRect.contains(x, y)) {
                showPicker();
            }
            return;
        }
        if (paused) {
            if (resumeRect.contains(x, y) || pauseRect.contains(x, y)) {
                paused = false;
                lastFrameAt = SystemClock.elapsedRealtime();
            } else if (pickerRect.contains(x, y) || menuRect.contains(x, y)) {
                showPicker();
            }
            return;
        }
        if (session != null && session.isFinished()) {
            if (replayRect.contains(x, y)) {
                startMode(session.mode());
            } else if (pickerRect.contains(x, y) || menuRect.contains(x, y)) {
                showPicker();
            }
            return;
        }
        if (menuRect.contains(x, y)) {
            showPicker();
        } else if (pauseRect.contains(x, y)) {
            paused = true;
        } else if (demoBoostRect.contains(x, y)) {
            demoBoost = Math.min(55f, demoBoost + 24f);
            showMessage("Demo cadence boost");
        }
    }

    private void startMode(GameMode mode) {
        if (!sensorFresh && !demoAllowed) {
            showMessage("Sensor not ready. Wait or enable Demo.");
            return;
        }
        session = new GameSession(mode, progressStore);
        sessionDemoMode = demoAllowed;
        pickerVisible = false;
        paused = false;
        resultRecorded = false;
        newBest = false;
        lastFrameAt = SystemClock.elapsedRealtime();
        physicsAccumulator = 0f;
        message = "";
    }

    private void showPicker() {
        pickerVisible = true;
        paused = false;
        session = null;
        sessionDemoMode = false;
        physicsAccumulator = 0f;
        resultRecorded = false;
        replayRect.setEmpty();
        resumeRect.setEmpty();
        pickerRect.setEmpty();
    }

    private void showMessage(String nextMessage) {
        message = nextMessage;
        messageUntil = SystemClock.elapsedRealtime() + 1800L;
    }

    private boolean isSensorPaused() {
        return session != null && !sessionDemoMode && !sensorFresh && !session.isFinished();
    }

    private float minimumTouchPx() {
        return 48f * getResources().getDisplayMetrics().density;
    }

    private void drawPanel(Canvas canvas, RectF bounds, int color, int border, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * scale);
        paint.setColor(border);
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawButton(Canvas canvas, RectF bounds, String label,
                            int background, int foreground) {
        paint.setColor(background);
        canvas.drawRoundRect(bounds, 6f * scale, 6f * scale, paint);
        float textSize = 17f * scale;
        paint.setTextSize(textSize);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
        drawText(canvas, label, bounds.centerX(), baseline,
                textSize, foreground, Paint.Align.CENTER, true);
    }

    private void drawWrappedText(Canvas canvas, String text, float x, float y,
                                 float maxWidth, float textSize, int color, int maxLines) {
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.DEFAULT);
        String remaining = text;
        for (int line = 0; line < maxLines && remaining.length() > 0; line++) {
            int count = paint.breakText(remaining, true, maxWidth, null);
            if (count <= 0) {
                break;
            }
            if (count < remaining.length()) {
                int space = remaining.lastIndexOf(' ', count - 1);
                if (space > 0) {
                    count = space;
                }
            }
            String part = remaining.substring(0, count).trim();
            drawText(canvas, part, x, y + line * textSize * 1.32f,
                    textSize, color, Paint.Align.LEFT, false);
            remaining = remaining.substring(count).trim();
        }
    }

    private void drawText(Canvas canvas, String text, float x, float y,
                          float size, int color, Paint.Align align, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        canvas.drawText(text, x, y, paint);
    }
}
