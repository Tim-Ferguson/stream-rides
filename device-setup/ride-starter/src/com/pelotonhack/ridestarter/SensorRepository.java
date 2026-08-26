package com.pelotonhack.ridestarter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

final class SensorRepository {
    private static final String TAG = "RideSensor";
    private static final String SERVICE_PACKAGE = "com.onepeloton.affernetservice";
    private static final String SERVICE_CLASS =
            "com.onepeloton.affernetservice.AffernetService";
    private static final String SERVICE_DESCRIPTOR =
            "com.onepeloton.affernetservice.IV1Interface";
    private static final String CALLBACK_DESCRIPTOR =
            "com.onepeloton.affernetservice.IV1Callback";
    private static final int TRANSACTION_REGISTER = 1;
    private static final int TRANSACTION_UNREGISTER = 2;
    private static final int CALLBACK_SENSOR_DATA = 1;
    private static final int CALLBACK_SENSOR_ERROR = 2;
    private static final int CALLBACK_CALIBRATION_STATUS = 3;
    private static final long FRESH_MS = 5000L;
    private static final long MIN_RETRY_MS = 1000L;
    private static final long MAX_RETRY_MS = 30000L;
    private static final long MAX_CADENCE_RPM = 250L;
    private static final long MAX_RAW_POWER = 300000L;
    private static final int MAX_RESISTANCE = 100;

    private static SensorRepository instance;

    static synchronized SensorRepository get(Context context) {
        if (instance == null) {
            instance = new SensorRepository(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SensorCallback callback = new SensorCallback();
    private final IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    reconnect("binder died");
                }
            });
        }
    };
    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            bind();
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            binding = false;
            serviceBinder = binder;
            try {
                binder.linkToDeath(deathRecipient, 0);
                connectedAt = SystemClock.elapsedRealtime();
                connected = true;
                lastError = "waiting for sample";
                registerCallback(binder, TRANSACTION_REGISTER);
                retryAttempt = 0;
                Log.i(TAG, "Direct bike sensor service connected");
            } catch (RemoteException | RuntimeException exception) {
                Log.w(TAG, "Could not register sensor callback", exception);
                reconnect(exception.getClass().getSimpleName());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            reconnect("service disconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            reconnect("binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            reconnect("null binding");
        }
    };

    private volatile boolean started;
    private volatile boolean bound;
    private volatile boolean binding;
    private volatile boolean connected;
    private volatile IBinder serviceBinder;
    private volatile Sample latest = Sample.EMPTY;
    private volatile long connectedAt;
    private volatile String lastError = "not started";
    private boolean reconnectQueued;
    private int retryAttempt;

    private SensorRepository(Context context) {
        this.context = context;
    }

    synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        bind();
    }

    synchronized void reconnectNow() {
        handler.removeCallbacks(retryRunnable);
        retryAttempt = 0;
        lastError = started ? "manual reconnect" : "manual connect";
        if (!started) {
            started = true;
            bind();
            return;
        }
        disconnectBinding();
        handler.post(retryRunnable);
    }

    Snapshot snapshot() {
        Sample sample = latest;
        long capturedAt = sample.capturedAt;
        long age = capturedAt == 0L
                ? Math.max(0L, SystemClock.elapsedRealtime() - connectedAt)
                : Math.max(0L, SystemClock.elapsedRealtime() - capturedAt);
        boolean fresh = connected && capturedAt != 0L && age <= FRESH_MS;
        if (started && connected && age > FRESH_MS && !reconnectQueued) {
            reconnectQueued = true;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    reconnectQueued = false;
                    Snapshot current = snapshotWithoutReconnect();
                    if (current.connected && current.ageMs > FRESH_MS) {
                        reconnect("stale sample");
                    }
                }
            });
        }
        return new Snapshot(connected, capturedAt != 0L, fresh,
                sample.cadenceRpm, sample.outputWatts, sample.resistance, age, lastError);
    }

    private Snapshot snapshotWithoutReconnect() {
        Sample sample = latest;
        long capturedAt = sample.capturedAt;
        long age = capturedAt == 0L
                ? Math.max(0L, SystemClock.elapsedRealtime() - connectedAt)
                : Math.max(0L, SystemClock.elapsedRealtime() - capturedAt);
        return new Snapshot(connected, capturedAt != 0L,
                connected && capturedAt != 0L && age <= FRESH_MS,
                sample.cadenceRpm, sample.outputWatts, sample.resistance, age, lastError);
    }

    private void bind() {
        handler.removeCallbacks(retryRunnable);
        if (!started || binding || bound) {
            return;
        }
        binding = true;
        Intent intent = new Intent(SERVICE_DESCRIPTOR);
        intent.setComponent(new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS));
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            binding = false;
            if (!bound) {
                lastError = "bind returned false";
                scheduleRetry();
            }
        } catch (SecurityException exception) {
            binding = false;
            bound = false;
            lastError = "permission denied";
            Log.w(TAG, "Sensor service permission denied", exception);
            scheduleRetry();
        } catch (RuntimeException exception) {
            binding = false;
            bound = false;
            lastError = exception.getClass().getSimpleName();
            Log.w(TAG, "Sensor service bind failed", exception);
            scheduleRetry();
        }
    }

    private void reconnect(String reason) {
        lastError = reason;
        disconnectBinding();
        scheduleRetry();
    }

    private void disconnectBinding() {
        IBinder binder = serviceBinder;
        serviceBinder = null;
        connected = false;
        binding = false;
        latest = Sample.EMPTY;
        connectedAt = 0L;
        reconnectQueued = false;
        if (binder != null) {
            try {
                registerCallback(binder, TRANSACTION_UNREGISTER);
            } catch (RemoteException | RuntimeException exception) {
                Log.d(TAG, "Sensor callback unregister failed", exception);
            }
            try {
                binder.unlinkToDeath(deathRecipient, 0);
            } catch (RuntimeException ignored) {
                // The binder may already be dead.
            }
        }
        if (bound) {
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // Android already released the binding.
            }
        }
        bound = false;
    }

    private void scheduleRetry() {
        if (!started) {
            return;
        }
        handler.removeCallbacks(retryRunnable);
        long delay = Math.min(MAX_RETRY_MS,
                MIN_RETRY_MS * (1L << Math.min(5, retryAttempt)));
        retryAttempt += 1;
        handler.postDelayed(retryRunnable, delay);
    }

    private void registerCallback(IBinder binder, int transaction) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeStrongBinder(callback);
            data.writeString(context.getPackageName());
            if (!binder.transact(transaction, data, reply, 0)) {
                throw new RemoteException("Sensor service rejected transaction " + transaction);
            }
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private final class SensorCallback extends Binder {
        SensorCallback() {
            attachInterface(null, CALLBACK_DESCRIPTOR);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(CALLBACK_DESCRIPTOR);
                }
                return true;
            }
            if (code >= FIRST_CALL_TRANSACTION && code <= LAST_CALL_TRANSACTION) {
                data.enforceInterface(CALLBACK_DESCRIPTOR);
            }
            switch (code) {
                case CALLBACK_SENSOR_DATA:
                    readSensorData(data);
                    return true;
                case CALLBACK_SENSOR_ERROR:
                    final long error = data.dataAvail() >= 8 ? data.readLong() : -1L;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            reconnect("sensor error " + error);
                        }
                    });
                    return true;
                case CALLBACK_CALIBRATION_STATUS:
                    if (data.dataAvail() >= 16) {
                        data.readInt();
                        data.readInt();
                        data.readLong();
                    }
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private void readSensorData(Parcel data) {
            if (data.dataAvail() < 4 || data.readInt() == 0) {
                return;
            }
            if (data.dataAvail() < 36) {
                lastError = "truncated sensor parcel";
                return;
            }

            long nextCadence = data.readLong();
            long rawPower = data.readLong();
            data.readLong();
            data.readLong();
            int nextResistance = data.readInt();
            if (nextCadence < 0L || nextCadence > MAX_CADENCE_RPM
                    || rawPower < 0L || rawPower > MAX_RAW_POWER
                    || nextResistance < 0 || nextResistance > MAX_RESISTANCE) {
                lastError = "sensor sample outside expected range";
                return;
            }

            latest = new Sample(nextCadence, rawPower / 100f, nextResistance,
                    SystemClock.elapsedRealtime());
            connected = true;
            retryAttempt = 0;
            lastError = "";
        }
    }

    private static final class Sample {
        static final Sample EMPTY = new Sample(0L, 0f, 0, 0L);

        final long cadenceRpm;
        final float outputWatts;
        final int resistance;
        final long capturedAt;

        Sample(long cadenceRpm, float outputWatts, int resistance, long capturedAt) {
            this.cadenceRpm = cadenceRpm;
            this.outputWatts = outputWatts;
            this.resistance = resistance;
            this.capturedAt = capturedAt;
        }
    }

    static final class Snapshot {
        final boolean connected;
        final boolean hasSample;
        final boolean fresh;
        final long cadenceRpm;
        final float outputWatts;
        final int resistance;
        final long ageMs;
        final String error;

        Snapshot(boolean connected, boolean hasSample, boolean fresh, long cadenceRpm,
                 float outputWatts, int resistance, long ageMs, String error) {
            this.connected = connected;
            this.hasSample = hasSample;
            this.fresh = fresh;
            this.cadenceRpm = cadenceRpm;
            this.outputWatts = outputWatts;
            this.resistance = resistance;
            this.ageMs = ageMs;
            this.error = error;
        }
    }
}
