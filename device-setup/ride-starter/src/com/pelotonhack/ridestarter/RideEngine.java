package com.pelotonhack.ridestarter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class RideEngine {
    private static final long HEARTBEAT_MS = 1000L;
    private static final long CHECKPOINT_MS = 2000L;

    private static RideEngine instance;

    static synchronized RideEngine get(Context context) {
        if (instance == null) {
            instance = new RideEngine(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SensorRepository sensors;
    private final RideAccumulator accumulator = new RideAccumulator();
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            accountNow(false);
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private long lastCheckpointAt;

    private RideEngine(Context context) {
        this.context = context;
        sensors = SensorRepository.get(context);
        sensors.start();
        RideSessionStore.pauseIfStale(context);
        handler.post(heartbeat);
    }

    boolean isSensorReady() {
        return sensors.snapshot().fresh;
    }

    SensorRepository.Snapshot sensorSnapshot() {
        return sensors.snapshot();
    }

    void reconnectSensors() {
        sensors.reconnectNow();
    }

    synchronized RideSessionStore.Session startLocalRide() {
        RideSessionStore.Session session = RideSessionStore.startIfNeeded(context);
        if (session.id != accumulator.sessionId()) {
            initializeSession(session, SystemClock.elapsedRealtime());
        } else {
            accountNow(true);
            session = RideSessionStore.read(context);
        }
        checkpoint(session, true);
        return RideSessionStore.read(context);
    }

    synchronized RideSessionStore.Session togglePause() {
        accountNow(true);
        RideSessionStore.Session updated = RideSessionStore.togglePause(context);
        accumulator.tick(SystemClock.elapsedRealtime(), true, false, 0f, 0f);
        checkpoint(updated, true);
        return RideSessionStore.read(context);
    }

    synchronized RideSessionStore.Session endRide() {
        accountNow(true);
        RideSessionStore.Session current = RideSessionStore.read(context);
        checkpoint(current, true);
        RideSessionStore.Session ended = RideSessionStore.end(context);
        resetTracking();
        return ended;
    }

    synchronized RideSnapshot snapshot() {
        accountNow(false);
        RideSessionStore.Session session = RideSessionStore.read(context);
        SensorRepository.Snapshot sensor = sensors.snapshot();
        float output = session.active && session.id == accumulator.sessionId()
                ? accumulator.outputKilojoules() : session.outputKilojoules;
        float distance = session.active && session.id == accumulator.sessionId()
                ? accumulator.distanceMiles() : session.distanceMiles;
        return new RideSnapshot(session, sensor, output, distance, estimateSpeedMph(sensor));
    }

    private synchronized void accountNow(boolean forceCheckpoint) {
        RideSessionStore.Session session = RideSessionStore.read(context);
        long now = SystemClock.elapsedRealtime();
        if (!session.active) {
            resetTracking();
            return;
        }
        if (session.id != accumulator.sessionId()) {
            initializeSession(session, now);
        }

        SensorRepository.Snapshot sensor = sensors.snapshot();
        accumulator.tick(now, session.paused, sensor.fresh,
                sensor.outputWatts, estimateSpeedMph(sensor));
        if (forceCheckpoint || now - lastCheckpointAt >= CHECKPOINT_MS) {
            checkpoint(session, forceCheckpoint);
        }
    }

    private void initializeSession(RideSessionStore.Session session, long now) {
        accumulator.reset(session.id, now,
                session.outputKilojoules, session.distanceMiles);
        lastCheckpointAt = now;
    }

    private void checkpoint(RideSessionStore.Session session, boolean synchronous) {
        if (!session.active || session.id != accumulator.sessionId()) {
            return;
        }
        RideSessionStore.checkpoint(context, session.elapsedMs,
                accumulator.outputKilojoules(), accumulator.distanceMiles(), synchronous);
        lastCheckpointAt = SystemClock.elapsedRealtime();
    }

    private void resetTracking() {
        accumulator.clear();
        lastCheckpointAt = 0L;
    }

    static float estimateSpeedMph(SensorRepository.Snapshot sensor) {
        if (!sensor.fresh || sensor.cadenceRpm < 1L || sensor.outputWatts <= 0f) {
            return 0f;
        }
        return Math.min(50f, 5f + 1.45f * (float) Math.sqrt(sensor.outputWatts));
    }

    static final class RideSnapshot {
        final RideSessionStore.Session session;
        final SensorRepository.Snapshot sensor;
        final float outputKilojoules;
        final float distanceMiles;
        final float estimatedSpeedMph;

        RideSnapshot(RideSessionStore.Session session, SensorRepository.Snapshot sensor,
                     float outputKilojoules, float distanceMiles, float estimatedSpeedMph) {
            this.session = session;
            this.sensor = sensor;
            this.outputKilojoules = outputKilojoules;
            this.distanceMiles = distanceMiles;
            this.estimatedSpeedMph = estimatedSpeedMph;
        }
    }
}
