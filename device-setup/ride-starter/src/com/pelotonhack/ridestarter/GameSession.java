package com.pelotonhack.ridestarter;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.Locale;

final class GameSession {
    static final int COLOR_TEXT = 0xFFF4F5F7;
    static final int COLOR_MUTED = 0xFFAEB4BC;
    static final int COLOR_GREEN = 0xFF35D07F;
    static final int COLOR_YELLOW = 0xFFFFC857;
    static final int COLOR_RED = 0xFFFF5C5C;
    static final int COLOR_CYAN = 0xFF43C6E8;

    static final class Input {
        final float cadence;
        final float watts;
        final float resistance;
        final boolean demo;

        Input(float cadence, float watts, float resistance, boolean demo) {
            this.cadence = Math.max(0f, cadence);
            this.watts = Math.max(0f, watts);
            this.resistance = Math.max(0f, resistance);
            this.demo = demo;
        }
    }

    private final GameMode mode;
    private final Runner runner;

    GameSession(GameMode mode, GameProgressStore progressStore) {
        this.mode = mode;
        switch (mode) {
            case CADENCE_DRAG:
                runner = new DragRunner(
                        progressStore.upgradeLevel(GameProgressStore.Upgrade.ENGINE),
                        progressStore.upgradeLevel(GameProgressStore.Upgrade.GRIP),
                        progressStore.upgradeLevel(GameProgressStore.Upgrade.AERO));
                break;
            case CADENCE_FLYER:
                runner = new FlyerRunner();
                break;
            case RESISTANCE_RIDGE:
                runner = new RidgeRunner();
                break;
            case POWER_REACTOR:
                runner = new ReactorRunner();
                break;
            case RHYTHM_RUNNER:
                runner = new RhythmRunner();
                break;
            case ORBITAL_COURIER:
            default:
                runner = new CourierRunner();
                break;
        }
    }

    GameMode mode() {
        return mode;
    }

    void update(float deltaSeconds, Input input) {
        if (!runner.finished) {
            runner.update(Math.max(0f, deltaSeconds), input);
        }
    }

    void draw(Canvas canvas, Paint paint, RectF area, float scale,
              GameSprites sprites) {
        runner.draw(canvas, paint, area, scale, sprites);
    }

    boolean isFinished() {
        return runner.finished;
    }

    float score() {
        return runner.score;
    }

    int rewardCredits() {
        return runner.rewardCredits();
    }

    String status() {
        return runner.status();
    }

    String hint() {
        return runner.hint();
    }

    private abstract static class Runner {
        float elapsed;
        float score;
        boolean finished;

        abstract void update(float dt, Input input);

        abstract void draw(Canvas canvas, Paint paint, RectF area, float scale,
                           GameSprites sprites);

        abstract String status();

        abstract String hint();

        int rewardCredits() {
            return 8 + Math.min(50, Math.max(0, Math.round(score / 250f)));
        }

        void drawTimer(Canvas canvas, Paint paint, RectF area, float duration, float scale) {
            float remaining = Math.max(0f, duration - elapsed);
            drawText(canvas, paint, String.format(Locale.US, "%.0f s", remaining),
                    area.right - 18f * scale, area.top + 36f * scale,
                    24f * scale, COLOR_TEXT, Paint.Align.RIGHT, true);
        }
    }

    private static final class DragRunner extends Runner {
        private static final float FINISH_METERS = 400f;
        private final int engineLevel;
        private final int gripLevel;
        private final int aeroLevel;
        private float distance;
        private float speedMps;

        DragRunner(int engineLevel, int gripLevel, int aeroLevel) {
            this.engineLevel = engineLevel;
            this.gripLevel = gripLevel;
            this.aeroLevel = aeroLevel;
        }

        @Override
        void update(float dt, Input input) {
            elapsed += dt;
            float drive = input.cadence * 0.085f + input.watts * 0.025f;
            float launch = 0.72f + gripLevel * 0.045f
                    + Math.min(0.18f, elapsed * 0.06f);
            float engine = 1f + engineLevel * 0.055f;
            float aero = 1f + aeroLevel * 0.035f * Math.min(1f, speedMps / 15f);
            float desiredSpeed = drive * launch * engine * aero;
            speedMps += (desiredSpeed - speedMps) * Math.min(1f, dt * 2.8f);
            speedMps = Math.max(0f, speedMps - dt * 0.12f);
            distance += speedMps * dt;
            score = elapsed;
            if (distance >= FINISH_METERS || elapsed >= 75f) {
                finished = true;
                distance = Math.min(FINISH_METERS, distance);
                if (distance < FINISH_METERS) {
                    score = 75f;
                }
            }
        }

        @Override
        void draw(Canvas canvas, Paint paint, RectF area, float scale,
                  GameSprites sprites) {
            fill(canvas, paint, area, 0xFF12171B);
            float roadTop = area.top + area.height() * 0.26f;
            float roadBottom = area.bottom - area.height() * 0.14f;
            paint.setColor(0xFF292C31);
            canvas.drawRect(area.left, roadTop, area.right, roadBottom, paint);
            paint.setStrokeWidth(3f * scale);
            paint.setColor(0xFF727780);
            for (int lane = 1; lane < 4; lane++) {
                float y = roadTop + (roadBottom - roadTop) * lane / 4f;
                canvas.drawLine(area.left, y, area.right, y, paint);
            }

            float progress = clamp(distance / FINISH_METERS, 0f, 1f);
            float carX = area.left + area.width() * (0.12f + progress * 0.76f);
            float carY = roadTop + (roadBottom - roadTop) * 0.62f;
            sprites.draw(canvas, 0, carX, carY - 8f * scale,
                    250f * scale, 150f * scale, paint);

            float rivalProgress = clamp(elapsed / 31f, 0f, 1f);
            drawCar(canvas, paint,
                    area.left + area.width() * (0.12f + rivalProgress * 0.76f),
                    roadTop + (roadBottom - roadTop) * 0.26f,
                    0.88f * scale, 0xFFE75A7C);

            drawProgress(canvas, paint,
                    new RectF(area.left + 36f * scale, area.bottom - 52f * scale,
                            area.right - 36f * scale, area.bottom - 34f * scale),
                    progress, COLOR_GREEN);
            drawText(canvas, paint,
                    String.format(Locale.US, "%.0f / 400 m", distance),
                    area.left + 24f * scale, area.top + 38f * scale,
                    24f * scale, COLOR_TEXT, Paint.Align.LEFT, true);
            drawText(canvas, paint,
                    String.format(Locale.US, "%.1f km/h", speedMps * 3.6f),
                    area.centerX(), area.top + 38f * scale,
                    26f * scale, COLOR_YELLOW, Paint.Align.CENTER, true);
            drawText(canvas, paint,
                    String.format(Locale.US, "%.2f s", elapsed),
                    area.right - 24f * scale, area.top + 38f * scale,
                    24f * scale, COLOR_TEXT, Paint.Align.RIGHT, true);
        }

        @Override
        String status() {
            return String.format(Locale.US, "%.0f m  |  %.1f km/h", distance, speedMps * 3.6f);
        }

        @Override
        String hint() {
            return "Cadence launches. Watts build speed.";
        }

        @Override
        int rewardCredits() {
            return distance >= FINISH_METERS ? Math.max(12, 65 - Math.round(score)) : 5;
        }
    }

    private static final class FlyerRunner extends Runner {
        private static final float DURATION = 45f;
        private float flyerY = 0.5f;
        private float velocityY;
        private float gateX = 1.05f;
        private float gapY = 0.5f;
        private float previousCadence;
        private int gateIndex;
        private int gates;
        private int lives = 3;
        private boolean gateChecked;
        private float collisionCooldown;

        @Override
        void update(float dt, Input input) {
            elapsed += dt;
            collisionCooldown = Math.max(0f, collisionCooldown - dt);
            float surge = Math.max(0f, input.cadence - previousCadence);
            previousCadence += (input.cadence - previousCadence) * Math.min(1f, dt * 5f);
            float lift = Math.max(0f, input.cadence - 35f) * 0.0145f
                    + input.watts * 0.0012f;
            velocityY += (0.86f - lift) * dt;
            velocityY -= Math.min(0.34f, surge * 0.008f);
            velocityY = clamp(velocityY, -0.75f, 0.9f);
            flyerY += velocityY * dt;

            float scroll = 0.18f + Math.min(0.10f, input.cadence * 0.00075f);
            gateX -= scroll * dt;
            if (!gateChecked && gateX <= 0.25f) {
                gateChecked = true;
                if (Math.abs(flyerY - gapY) <= 0.17f) {
                    gates += 1;
                } else {
                    hit();
                }
            }
            if (gateX < -0.12f) {
                gateIndex += 1;
                gateX = 1.08f;
                gapY = 0.27f + 0.46f * hashWave(gateIndex * 1.91f);
                gateChecked = false;
            }
            if ((flyerY < 0.08f || flyerY > 0.92f) && collisionCooldown <= 0f) {
                hit();
            }
            flyerY = clamp(flyerY, 0.08f, 0.92f);
            score = gates * 120f + elapsed * 5f + lives * 25f;
            if (elapsed >= DURATION || lives <= 0) {
                finished = true;
            }
        }

        private void hit() {
            lives -= 1;
            collisionCooldown = 1.1f;
            velocityY = 0f;
        }

        @Override
        void draw(Canvas canvas, Paint paint, RectF area, float scale,
                  GameSprites sprites) {
            fill(canvas, paint, area, 0xFF102438);
            paint.setColor(0xFF17354E);
            for (int index = 0; index < 7; index++) {
                float x = area.left + ((index * 271f + elapsed * 35f) % area.width());
                float y = area.top + 45f * scale + (index % 3) * 62f * scale;
                canvas.drawCircle(x, y, 3f * scale, paint);
            }

            float gateCenterX = area.left + gateX * area.width();
            float gapCenterY = area.top + gapY * area.height();
            float gapHalf = area.height() * 0.17f;
            float gateWidth = 42f * scale;
            paint.setColor(0xFF3AA981);
            canvas.drawRect(gateCenterX - gateWidth / 2f, area.top,
                    gateCenterX + gateWidth / 2f, gapCenterY - gapHalf, paint);
            canvas.drawRect(gateCenterX - gateWidth / 2f, gapCenterY + gapHalf,
                    gateCenterX + gateWidth / 2f, area.bottom, paint);

            float flyerX = area.left + area.width() * 0.25f;
            float flyerCenterY = area.top + flyerY * area.height();
            sprites.draw(canvas, 1, flyerX, flyerCenterY,
                    188f * scale, 172f * scale, paint);
            drawText(canvas, paint, "GATES " + gates,
                    area.left + 24f * scale, area.top + 38f * scale,
                    24f * scale, COLOR_TEXT, Paint.Align.LEFT, true);
            drawText(canvas, paint, "LIVES " + Math.max(0, lives),
                    area.centerX(), area.top + 38f * scale,
                    24f * scale, lives <= 1 ? COLOR_RED : COLOR_TEXT,
                    Paint.Align.CENTER, true);
            drawTimer(canvas, paint, area, DURATION, scale);
        }

        @Override
        String status() {
            return "Gates " + gates + "  |  Lives " + Math.max(0, lives);
        }

        @Override
        String hint() {
            return "Surge cadence to climb. Ease off to descend.";
        }
    }

    private static final class RidgeRunner extends Runner {
        private static final float DURATION = 75f;
        private float distance;
        private float elevation;
        private float speed;
        private float targetResistance = 35f;
        private float currentResistance;
        private float match = 1f;

        @Override
        void update(float dt, Input input) {
            elapsed += dt;
            float slope = 0.045f
                    + 0.032f * (float) Math.sin(distance / 62f)
                    + 0.024f * (float) Math.sin(distance / 27f + 0.8f);
            targetResistance = clamp(27f + slope * 520f, 25f, 68f);
            currentResistance = input.resistance;
            float resistanceError = Math.abs(input.resistance - targetResistance);
            match = clamp(1f - resistanceError / 38f, 0.28f, 1f);
            float drive = input.cadence * 0.052f + input.watts * 0.015f;
            speed += (drive * match - speed) * Math.min(1f, dt * 2.5f);
            distance += speed * dt;
            elevation += Math.max(0f, speed * slope * dt);
            score = distance + elevation * 6f;
            if (elapsed >= DURATION) {
                finished = true;
            }
        }

        @Override
        void draw(Canvas canvas, Paint paint, RectF area, float scale,
                  GameSprites sprites) {
            fill(canvas, paint, area, 0xFF16302B);
            Path farMountains = new Path();
            farMountains.moveTo(area.left, area.bottom);
            for (int step = 0; step <= 24; step++) {
                float x = area.left + area.width() * step / 24f;
                float world = distance * 0.18f + step * 13f;
                float y = area.top + area.height() * (0.38f
                        + 0.12f * (float) Math.sin(world / 31f));
                farMountains.lineTo(x, y);
            }
            farMountains.lineTo(area.right, area.bottom);
            farMountains.close();
            paint.setColor(0xFF285045);
            canvas.drawPath(farMountains, paint);

            Path trail = new Path();
            trail.moveTo(area.left, area.top + area.height() * 0.86f);
            for (int step = 0; step <= 30; step++) {
                float x = area.left + area.width() * step / 30f;
                float world = distance + step * 9f;
                float y = area.top + area.height() * (0.65f
                        - 0.10f * (float) Math.sin(world / 58f)
                        - 0.055f * (float) Math.sin(world / 24f));
                trail.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10f * scale);
            paint.setColor(0xFF9D8A65);
            canvas.drawPath(trail, paint);
            paint.setStyle(Paint.Style.FILL);

            float bikeX = area.left + area.width() * 0.30f;
            float world = distance + 30f * 9f / 30f;
            float bikeY = area.top + area.height() * (0.65f
                    - 0.10f * (float) Math.sin(world / 58f)
                    - 0.055f * (float) Math.sin(world / 24f));
            sprites.draw(canvas, 2, bikeX, bikeY - 38f * scale,
                    230f * scale, 165f * scale, paint);

            drawText(canvas, paint,
                    String.format(Locale.US, "%.0f m  +%.0f m", distance, elevation),
                    area.left + 24f * scale, area.top + 38f * scale,
                    24f * scale, COLOR_TEXT, Paint.Align.LEFT, true);
            drawText(canvas, paint,
                    String.format(Locale.US, "RES %.0f  TARGET %.0f",
                            currentResistance, targetResistance),
                    area.centerX(), area.top + 38f * scale,
                    24f * scale, match > 0.78f ? COLOR_GREEN : COLOR_YELLOW,
                    Paint.Align.CENTER, true);
            drawTimer(canvas, paint, area, DURATION, scale);
        }

        @Override
        String status() {
            return String.format(Locale.US, "Target resistance %.0f  |  Match %.0f%%",
                    targetResistance, match * 100f);
        }

        @Override
        String hint() {
            return "Match resistance for traction, then add cadence.";
        }
    }

    private static final class ReactorRunner extends Runner {
        private static final float DURATION = 60f;
        private static final float[] TARGETS = {110f, 155f, 205f, 135f, 235f, 175f};
        private float target = TARGETS[0];
        private float stability = 0.55f;
        private float match;

        @Override
        void update(float dt, Input input) {
            elapsed += dt;
            target = TARGETS[Math.min(TARGETS.length - 1, (int) (elapsed / 10f))];
            float error = Math.abs(input.watts - target);
            match = clamp(1f - error / Math.max(90f, target * 0.72f), 0f, 1f);
            stability += (match - 0.58f) * dt * 0.36f;
            stability = clamp(stability, 0.04f, 1f);
            score += match * (75f + stability * 55f) * dt;
            if (elapsed >= DURATION) {
                finished = true;
            }
        }

        @Override
        void draw(Canvas canvas, Paint paint, RectF area, float scale,
                  GameSprites sprites) {
            fill(canvas, paint, area, 0xFF19152B);
            float pulse = 1f + 0.05f * (float) Math.sin(elapsed * 5f);
            float radius = Math.min(area.width(), area.height()) * 0.20f * pulse;
            paint.setColor(stability > 0.45f ? 0xFF6536D9 : COLOR_RED);
            canvas.drawCircle(area.centerX(), area.centerY(), radius, paint);
            sprites.draw(canvas, 3, area.centerX(), area.centerY(),
                    radius * 1.9f, radius * 1.9f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(18f * scale);
            paint.setColor(COLOR_CYAN);
            canvas.drawArc(new RectF(area.centerX() - radius * 1.35f,
                            area.centerY() - radius * 1.35f,
                            area.centerX() + radius * 1.35f,
                            area.centerY() + radius * 1.35f),
                    -90f, 360f * stability, false, paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(canvas, paint, String.format(Locale.US, "%.0f W", target),
                    area.centerX(), area.centerY() + 10f * scale,
                    44f * scale, COLOR_TEXT, Paint.Align.CENTER, true);
            drawText(canvas, paint, "TARGET POWER",
                    area.centerX(), area.centerY() + 48f * scale,
                    17f * scale, COLOR_MUTED, Paint.Align.CENTER, true);
            drawText(canvas, paint, "STABILITY " + Math.round(stability * 100f) + "%",
                    area.left + 24f * scale, area.top + 38f * scale,
                    24f * scale, stability > 0.55f ? COLOR_GREEN : COLOR_YELLOW,
                    Paint.Align.LEFT, true);
            drawTimer(canvas, paint, area, DURATION, scale);
        }

        @Override
        String status() {
            return String.format(Locale.US, "Target %.0f W  |  Stability %.0f%%",
                    target, stability * 100f);
        }

        @Override
        String hint() {
            return "Hold watts near target. Each phase lasts 10 seconds.";
        }
    }

    private static final class RhythmRunner extends Runner {
        private static final float DURATION = 60f;
        private static final float[] TARGETS = {65f, 78f, 92f, 72f, 86f, 98f, 76f, 88f};
        private float target = TARGETS[0];
        private float match;
        private float combo;

        @Override
        void update(float dt, Input input) {
            elapsed += dt;
            lastCadenceForDraw = input.cadence;
            int phase = Math.min(TARGETS.length - 1, (int) (elapsed / 7.5f));
            target = TARGETS[phase];
            float error = Math.abs(input.cadence - target);
            match = clamp(1f - error / 32f, 0f, 1f);
            if (error <= 7f) {
                combo = Math.min(20f, combo + dt * 2.2f);
            } else {
                combo = Math.max(0f, combo - dt * 5f);
            }
            score += match * (55f + combo * 4f) * dt;
            if (elapsed >= DURATION) {
                finished = true;
            }
        }

        @Override
        void draw(Canvas canvas, Paint paint, RectF area, float scale,
                  GameSprites sprites) {
            fill(canvas, paint, area, 0xFF102522);
            float trackLeft = area.left + area.width() * 0.12f;
            float trackRight = area.right - area.width() * 0.12f;
            float trackY = area.centerY();
            paint.setColor(0xFF29453F);
            canvas.drawRoundRect(trackLeft, trackY - 52f * scale,
                    trackRight, trackY + 52f * scale, 20f * scale, 20f * scale, paint);
            float targetX = map(target, 45f, 110f, trackLeft, trackRight);
            paint.setColor(COLOR_GREEN);
            canvas.drawRect(targetX - 42f * scale, trackY - 70f * scale,
                    targetX + 42f * scale, trackY + 70f * scale, paint);
            float markerX = map(lastCadenceForDraw, 45f, 110f, trackLeft, trackRight);
            sprites.draw(canvas, 4, markerX, trackY - 12f * scale,
                    155f * scale, 175f * scale, paint);
            drawText(canvas, paint, String.format(Locale.US, "TARGET %.0f RPM", target),
                    area.centerX(), area.top + 46f * scale,
                    30f * scale, COLOR_TEXT, Paint.Align.CENTER, true);
            drawText(canvas, paint, String.format(Locale.US, "COMBO x%.1f", 1f + combo / 5f),
                    area.left + 24f * scale, area.top + 42f * scale,
                    23f * scale, combo > 8f ? COLOR_GREEN : COLOR_MUTED,
                    Paint.Align.LEFT, true);
            drawTimer(canvas, paint, area, DURATION, scale);
        }

        private float lastCadenceForDraw;

        @Override
        String status() {
            return String.format(Locale.US, "Target %.0f RPM  |  Match %.0f%%",
                    target, match * 100f);
        }

        @Override
        String hint() {
            return "Keep the yellow marker inside the green cadence lane.";
        }

        @Override
        int rewardCredits() {
            return 8 + Math.round(score / 450f);
        }

    }

    private static final class CourierRunner extends Runner {
        private static final float DURATION = 60f;
        private float obstacleX = 1.08f;
        private int obstacleIndex;
        private int obstacleLane = 1;
        private int riderLane = 1;
        private int deliveries;
        private int lives = 3;
        private float shield;
        private float lastCadence;

        @Override
        void update(float dt, Input input) {
            elapsed += dt;
            lastCadence = input.cadence;
            riderLane = input.resistance < 36f ? 0 : input.resistance < 56f ? 1 : 2;
            shield = clamp((input.watts - 220f) / 150f, 0f, 1f);
            float speed = 0.16f + Math.min(0.16f, input.cadence * 0.0012f);
            obstacleX -= speed * dt;
            if (obstacleX <= 0.23f) {
                if (obstacleLane == riderLane && shield < 0.55f) {
                    lives -= 1;
                } else {
                    deliveries += 1;
                }
                obstacleIndex += 1;
                obstacleLane = (obstacleIndex * 2 + obstacleIndex / 2) % 3;
                obstacleX = 1.08f;
            }
            score = deliveries * 180f + elapsed * 4f + lives * 30f;
            if (elapsed >= DURATION || lives <= 0) {
                finished = true;
            }
        }

        @Override
        void draw(Canvas canvas, Paint paint, RectF area, float scale,
                  GameSprites sprites) {
            fill(canvas, paint, area, 0xFF0B1024);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * scale);
            for (int lane = 0; lane < 3; lane++) {
                float laneY = laneY(area, lane);
                paint.setColor(lane == riderLane ? 0xFF2E596A : 0xFF252B47);
                canvas.drawOval(new RectF(area.left + 25f * scale,
                        laneY - 48f * scale, area.right - 25f * scale,
                        laneY + 48f * scale), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            float shipX = area.left + area.width() * 0.23f;
            float shipY = laneY(area, riderLane);
            sprites.draw(canvas, 5, shipX, shipY,
                    220f * scale, 145f * scale, paint);
            if (shield > 0.05f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth((3f + shield * 6f) * scale);
                paint.setColor(0xAA7BE8FF);
                canvas.drawCircle(shipX, shipY, 57f * scale, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            float rockX = area.left + obstacleX * area.width();
            drawMeteor(canvas, paint, rockX, laneY(area, obstacleLane), scale);
            drawText(canvas, paint, "DELIVERIES " + deliveries,
                    area.left + 24f * scale, area.top + 38f * scale,
                    24f * scale, COLOR_TEXT, Paint.Align.LEFT, true);
            drawText(canvas, paint,
                    String.format(Locale.US, "SHIELD %.0f%%", shield * 100f),
                    area.centerX(), area.top + 38f * scale,
                    24f * scale, shield >= 0.55f ? COLOR_CYAN : COLOR_MUTED,
                    Paint.Align.CENTER, true);
            drawTimer(canvas, paint, area, DURATION, scale);
        }

        private float laneY(RectF area, int lane) {
            return area.top + area.height() * (0.30f + lane * 0.22f);
        }

        @Override
        String status() {
            return String.format(Locale.US, "Orbit %d  |  Lives %d  |  %.0f RPM",
                    riderLane + 1, Math.max(0, lives), lastCadence);
        }

        @Override
        String hint() {
            return "Resistance chooses orbit. High watts power the shield.";
        }

        @Override
        int rewardCredits() {
            return 8 + deliveries * 4;
        }
    }

    private static void fill(Canvas canvas, Paint paint, RectF area, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(area, paint);
    }

    private static void drawProgress(Canvas canvas, Paint paint, RectF bounds,
                                     float progress, int color) {
        paint.setColor(0xFF3A3E44);
        canvas.drawRoundRect(bounds, bounds.height() / 2f, bounds.height() / 2f, paint);
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(bounds.left, bounds.top,
                        bounds.left + bounds.width() * clamp(progress, 0f, 1f), bounds.bottom),
                bounds.height() / 2f, bounds.height() / 2f, paint);
    }

    private static void drawCar(Canvas canvas, Paint paint, float x, float y,
                                float scale, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(x - 55f * scale, y - 21f * scale,
                x + 55f * scale, y + 18f * scale, 10f * scale, 10f * scale, paint);
        canvas.drawRect(x - 22f * scale, y - 39f * scale,
                x + 31f * scale, y - 12f * scale, paint);
        paint.setColor(0xFF0B0C0E);
        canvas.drawCircle(x - 33f * scale, y + 20f * scale, 13f * scale, paint);
        canvas.drawCircle(x + 34f * scale, y + 20f * scale, 13f * scale, paint);
    }

    private static void drawFlyer(Canvas canvas, Paint paint, float x, float y,
                                  float scale, int color) {
        paint.setColor(color);
        canvas.drawCircle(x, y, 24f * scale, paint);
        Path wing = new Path();
        wing.moveTo(x - 8f * scale, y);
        wing.lineTo(x - 48f * scale, y - 24f * scale);
        wing.lineTo(x - 33f * scale, y + 22f * scale);
        wing.close();
        canvas.drawPath(wing, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x + 8f * scale, y - 7f * scale, 4f * scale, paint);
    }

    private static void drawBike(Canvas canvas, Paint paint, float x, float y, float scale) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f * scale);
        paint.setColor(0xFF101113);
        canvas.drawCircle(x - 27f * scale, y + 25f * scale, 22f * scale, paint);
        canvas.drawCircle(x + 31f * scale, y + 25f * scale, 22f * scale, paint);
        paint.setColor(COLOR_YELLOW);
        canvas.drawLine(x - 27f * scale, y + 25f * scale,
                x, y - 4f * scale, paint);
        canvas.drawLine(x, y - 4f * scale,
                x + 31f * scale, y + 25f * scale, paint);
        canvas.drawLine(x - 27f * scale, y + 25f * scale,
                x + 10f * scale, y + 25f * scale, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_TEXT);
        canvas.drawCircle(x + 4f * scale, y - 27f * scale, 11f * scale, paint);
    }

    private static void drawShip(Canvas canvas, Paint paint, float x, float y,
                                 float scale, float shield) {
        Path ship = new Path();
        ship.moveTo(x + 48f * scale, y);
        ship.lineTo(x - 35f * scale, y - 29f * scale);
        ship.lineTo(x - 22f * scale, y);
        ship.lineTo(x - 35f * scale, y + 29f * scale);
        ship.close();
        paint.setColor(COLOR_CYAN);
        canvas.drawPath(ship, paint);
        if (shield > 0.05f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((3f + shield * 6f) * scale);
            paint.setColor(0xAA7BE8FF);
            canvas.drawCircle(x, y, 53f * scale, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static void drawMeteor(Canvas canvas, Paint paint, float x, float y, float scale) {
        paint.setColor(0xFF9A7063);
        canvas.drawCircle(x, y, 31f * scale, paint);
        paint.setColor(0xFF624844);
        canvas.drawCircle(x - 9f * scale, y - 7f * scale, 7f * scale, paint);
        canvas.drawCircle(x + 11f * scale, y + 9f * scale, 5f * scale, paint);
    }

    private static void drawText(Canvas canvas, Paint paint, String text,
                                 float x, float y, float size, int color,
                                 Paint.Align align, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD
                : android.graphics.Typeface.DEFAULT);
        canvas.drawText(text, x, y, paint);
    }

    private static float hashWave(float value) {
        return 0.5f + 0.5f * (float) Math.sin(value * 2.17f + 0.63f);
    }

    private static float map(float value, float min, float max, float outMin, float outMax) {
        float normalized = clamp((value - min) / (max - min), 0f, 1f);
        return outMin + (outMax - outMin) * normalized;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
