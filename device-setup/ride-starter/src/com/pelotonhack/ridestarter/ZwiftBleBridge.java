package com.pelotonhack.ridestarter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

final class ZwiftBleBridge {
    private static final String TAG = "ZwiftBleBridge";
    private static final String PREFS = "stats_overlay";
    private static final String PREF_ENABLED = "zwift_bridge_enabled";
    private static final String RUNTIME_PREFS = "zwift_bridge_runtime";
    private static final String PREF_OWNS_BLUETOOTH = "owns_bluetooth";
    private static final long NOTIFY_INTERVAL_MS = 1000L;
    private static final long SENSOR_FRESH_MS = 2500L;
    private static final long BLUETOOTH_ENABLE_TIMEOUT_MS = 10000L;
    private static final long BLUETOOTH_RESTORE_TIMEOUT_MS = 10000L;

    private static final UUID CYCLING_POWER_SERVICE = uuid(0x1818);
    private static final UUID CYCLING_POWER_MEASUREMENT = uuid(0x2a63);
    private static final UUID CYCLING_POWER_FEATURE = uuid(0x2a65);
    private static final UUID CYCLING_POWER_SENSOR_LOCATION = uuid(0x2a5d);
    private static final UUID CSC_SERVICE = uuid(0x1816);
    private static final UUID CSC_MEASUREMENT = uuid(0x2a5b);
    private static final UUID CSC_FEATURE = uuid(0x2a5c);
    private static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION = uuid(0x2902);
    private static final Handler BLUETOOTH_STATE_HANDLER =
            new Handler(Looper.getMainLooper());
    private static BluetoothRestoreWatcher activeBluetoothRestoreWatcher;

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_ENABLED, enabled)
                .commit();
    }

    static void restoreBluetoothIfOwned(Context context) {
        final Context applicationContext = context.getApplicationContext();
        final SharedPreferences runtime = applicationContext.getSharedPreferences(
                RUNTIME_PREFS, Context.MODE_PRIVATE);
        if (!runtime.getBoolean(PREF_OWNS_BLUETOOTH, false)) {
            return;
        }
        BluetoothAdapter currentAdapter = null;
        try {
            BluetoothManager manager = (BluetoothManager) applicationContext
                    .getSystemService(Context.BLUETOOTH_SERVICE);
            currentAdapter = manager == null ? null : manager.getAdapter();
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not restore Bluetooth without the SARO service", exception);
        }
        if (currentAdapter == null) {
            return;
        }
        if (currentAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            cancelBluetoothRestoreWatcher();
            runtime.edit().putBoolean(PREF_OWNS_BLUETOOTH, false).commit();
            return;
        }
        try {
            if (currentAdapter.getState() != BluetoothAdapter.STATE_TURNING_OFF
                    && !currentAdapter.disable()) {
                Log.w(TAG, "Android rejected the Bluetooth restore request");
                return;
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not restore Bluetooth without the SARO service", exception);
            return;
        }
        scheduleBluetoothRestoreWatcher(currentAdapter, runtime);
    }

    private final Context context;
    private final Handler handler;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter adapter;
    private final SensorRepository sensors;
    private final ZwiftBlePayload.CrankTracker crankTracker =
            new ZwiftBlePayload.CrankTracker();
    private final Set<String> powerSubscribers = new HashSet<>();
    private final Set<String> cadenceSubscribers = new HashSet<>();
    private final Set<String> connectedDevices = new HashSet<>();
    private final ArrayDeque<PendingNotification> notificationQueue = new ArrayDeque<>();

    private final Runnable enablePoll = new Runnable() {
        @Override
        public void run() {
            if (!requested) {
                return;
            }
            if (SystemClock.elapsedRealtime() >= enableDeadlineMs) {
                fail("Bluetooth did not start");
                return;
            }
            if (adapter == null) {
                fail("Bluetooth is unavailable");
                return;
            }
            try {
                int adapterState = adapter.getState();
                if (adapterState == BluetoothAdapter.STATE_ON) {
                    bluetoothEnableRequested = false;
                    openGattServer();
                    return;
                }
                if (adapterState == BluetoothAdapter.STATE_OFF
                        && !bluetoothEnableRequested) {
                    if (!adapter.enable()) {
                        fail("Bluetooth could not be enabled");
                        return;
                    }
                    bluetoothEnableRequested = true;
                    enabledBluetooth = true;
                    setBluetoothOwnership(true);
                }
            } catch (RuntimeException exception) {
                Log.w(TAG, "Bluetooth enable request failed", exception);
                fail("Bluetooth could not be enabled");
                return;
            }
            handler.postDelayed(this, 250L);
        }
    };
    private final Runnable notifyRunnable = new Runnable() {
        @Override
        public void run() {
            if (!requested || !advertising || gattServer == null) {
                return;
            }
            publishSensorData();
            handler.postDelayed(this, NOTIFY_INTERVAL_MS);
        }
    };

    private BluetoothGattServerCallback createGattCallback(final int callbackGeneration) {
        return new BluetoothGattServerCallback() {
                @Override
                public void onConnectionStateChange(final BluetoothDevice device,
                                                    final int status, final int newState) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleConnectionStateChange(callbackGeneration, device, status,
                                    newState);
                        }
                    });
                }

                @Override
                public void onServiceAdded(final int status,
                                           final BluetoothGattService service) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleServiceAdded(callbackGeneration, status, service);
                        }
                    });
                }

                @Override
                public void onCharacteristicReadRequest(final BluetoothDevice device,
                                                        final int requestId, final int offset,
                                                        final BluetoothGattCharacteristic characteristic) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleCharacteristicRead(callbackGeneration, device, requestId,
                                    offset, characteristic);
                        }
                    });
                }

                @Override
                public void onDescriptorReadRequest(final BluetoothDevice device,
                                                    final int requestId, final int offset,
                                                    final BluetoothGattDescriptor descriptor) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleDescriptorRead(callbackGeneration, device, requestId,
                                    offset, descriptor);
                        }
                    });
                }

                @Override
                public void onDescriptorWriteRequest(final BluetoothDevice device,
                                                     final int requestId,
                                                     final BluetoothGattDescriptor descriptor,
                                                     final boolean preparedWrite,
                                                     final boolean responseNeeded,
                                                     final int offset, byte[] value) {
                    final byte[] copiedValue = value == null ? null
                            : Arrays.copyOf(value, value.length);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleDescriptorWriteRequest(callbackGeneration, device,
                                    requestId, descriptor, preparedWrite, responseNeeded,
                                    offset, copiedValue);
                        }
                    });
                }

                @Override
                public void onNotificationSent(final BluetoothDevice device,
                                               final int notificationStatus) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleNotificationSent(callbackGeneration, device,
                                    notificationStatus);
                        }
                    });
                }
            };
    }

    private AdvertiseCallback createAdvertiseCallback(final int callbackGeneration) {
        return new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (ZwiftBleBridge.this) {
                            if (!isCurrentGeneration(callbackGeneration)) {
                                return;
                            }
                            advertising = true;
                            updateActiveStatus();
                            handler.removeCallbacks(notifyRunnable);
                            handler.post(notifyRunnable);
                            Log.i(TAG, "Cycling Power and CSC bridge advertising");
                        }
                    }
                });
            }

            @Override
            public void onStartFailure(final int errorCode) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (ZwiftBleBridge.this) {
                            if (isCurrentGeneration(callbackGeneration)) {
                                fail("Bluetooth advertising error " + errorCode);
                            }
                        }
                    }
                });
            }
        };
    }

    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private BluetoothGattCharacteristic powerMeasurement;
    private BluetoothGattCharacteristic cadenceMeasurement;
    private PendingNotification notificationInFlight;
    private boolean requested;
    private boolean advertising;
    private boolean enabledBluetooth;
    private boolean bluetoothEnableRequested;
    private int generation;
    private long enableDeadlineMs;
    private volatile String status = "OFF";

    ZwiftBleBridge(Context context, Handler handler) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        sensors = SensorRepository.get(context);
        sensors.start();
        enabledBluetooth = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_OWNS_BLUETOOTH, false);
        if (enabledBluetooth && adapter != null
                && adapter.getState() == BluetoothAdapter.STATE_OFF) {
            cancelBluetoothRestoreWatcher();
            enabledBluetooth = false;
            setBluetoothOwnership(false);
        }
    }

    void applyPreference() {
        if (isEnabled(context)) {
            start();
        } else {
            stop();
        }
    }

    synchronized String status() {
        return status;
    }

    synchronized void shutdown() {
        // Preserve an explicitly enabled bridge across service/package restarts.
        stopInternal(!isEnabled(context));
    }

    private synchronized void start() {
        if (requested) {
            return;
        }
        generation++;
        requested = true;
        bluetoothEnableRequested = false;
        status = "STARTING";
        if (adapter == null) {
            fail("Bluetooth is unavailable");
            return;
        }
        cancelBluetoothRestoreWatcher();
        enabledBluetooth = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_OWNS_BLUETOOTH, false);
        enableDeadlineMs = SystemClock.elapsedRealtime() + BLUETOOTH_ENABLE_TIMEOUT_MS;
        handler.removeCallbacks(enablePoll);
        handler.post(enablePoll);
    }

    private synchronized void stop() {
        stopInternal(true);
        status = "OFF";
    }

    private synchronized void openGattServer() {
        if (!requested || gattServer != null) {
            return;
        }
        if (bluetoothManager == null || adapter == null || !adapter.isEnabled()) {
            fail("Bluetooth is unavailable");
            return;
        }
        status = "STARTING GATT";
        try {
            gattServer = bluetoothManager.openGattServer(context,
                    createGattCallback(generation));
        } catch (RuntimeException exception) {
            Log.w(TAG, "GATT server open failed", exception);
            fail("GATT server could not start");
            return;
        }
        if (gattServer == null) {
            fail("GATT server could not start");
            return;
        }
        BluetoothGattService powerService = buildPowerService();
        try {
            if (!gattServer.addService(powerService)) {
                fail("Cycling Power service could not start");
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Cycling Power service add failed", exception);
            fail("Cycling Power service could not start");
        }
    }

    private BluetoothGattService buildPowerService() {
        BluetoothGattService service = new BluetoothGattService(
                CYCLING_POWER_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        powerMeasurement = new BluetoothGattCharacteristic(
                CYCLING_POWER_MEASUREMENT,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        powerMeasurement.addDescriptor(notificationDescriptor());
        BluetoothGattCharacteristic feature = new BluetoothGattCharacteristic(
                CYCLING_POWER_FEATURE,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        feature.setValue(ZwiftBlePayload.cyclingPowerFeature());
        BluetoothGattCharacteristic sensorLocation = new BluetoothGattCharacteristic(
                CYCLING_POWER_SENSOR_LOCATION,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        sensorLocation.setValue(ZwiftBlePayload.cyclingPowerSensorLocation());
        service.addCharacteristic(powerMeasurement);
        service.addCharacteristic(feature);
        service.addCharacteristic(sensorLocation);
        return service;
    }

    private BluetoothGattService buildCadenceService() {
        BluetoothGattService service = new BluetoothGattService(
                CSC_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        cadenceMeasurement = new BluetoothGattCharacteristic(
                CSC_MEASUREMENT,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        cadenceMeasurement.addDescriptor(notificationDescriptor());
        BluetoothGattCharacteristic feature = new BluetoothGattCharacteristic(
                CSC_FEATURE,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        feature.setValue(ZwiftBlePayload.cscFeature());
        service.addCharacteristic(cadenceMeasurement);
        service.addCharacteristic(feature);
        return service;
    }

    private BluetoothGattDescriptor notificationDescriptor() {
        return new BluetoothGattDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION,
                BluetoothGattDescriptor.PERMISSION_READ
                        | BluetoothGattDescriptor.PERMISSION_WRITE);
    }

    private synchronized void handleServiceAdded(int callbackGeneration, int result,
                                                 BluetoothGattService service) {
        if (!isCurrentGeneration(callbackGeneration) || gattServer == null) {
            return;
        }
        if (result != BluetoothGatt.GATT_SUCCESS) {
            fail("GATT service error " + result);
            return;
        }
        if (CYCLING_POWER_SERVICE.equals(service.getUuid())) {
            try {
                if (!gattServer.addService(buildCadenceService())) {
                    fail("Cadence service could not start");
                }
            } catch (RuntimeException exception) {
                Log.w(TAG, "Cadence service add failed", exception);
                fail("Cadence service could not start");
            }
        } else if (CSC_SERVICE.equals(service.getUuid())) {
            startAdvertising();
        }
    }

    private synchronized void startAdvertising() {
        if (!requested || advertising || adapter == null) {
            return;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            fail("BLE advertising is unavailable");
            return;
        }
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(CYCLING_POWER_SERVICE))
                .addServiceUuid(new ParcelUuid(CSC_SERVICE))
                .build();
        status = "STARTING ADVERTISEMENT";
        advertiseCallback = createAdvertiseCallback(generation);
        try {
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (RuntimeException exception) {
            Log.w(TAG, "BLE advertising start failed", exception);
            fail("BLE advertising could not start");
        }
    }

    private void publishSensorData() {
        SensorRepository.Snapshot snapshot = sensors.snapshot();
        boolean freshForZwift = snapshot.fresh && snapshot.ageMs <= SENSOR_FRESH_MS;
        byte[] power = ZwiftBlePayload.cyclingPowerMeasurement(
                snapshot.outputWatts, freshForZwift);
        byte[] cadence = crankTracker.measurement(
                snapshot.cadenceRpm, SystemClock.elapsedRealtime(), freshForZwift);
        queueNotifications(powerMeasurement, power, powerSubscribers);
        queueNotifications(cadenceMeasurement, cadence, cadenceSubscribers);
    }

    private synchronized void queueNotifications(BluetoothGattCharacteristic characteristic,
                                                 byte[] value, Set<String> subscribers) {
        if (gattServer == null || characteristic == null || subscribers.isEmpty()) {
            return;
        }
        for (String address : new HashSet<>(subscribers)) {
            removeQueuedNotification(address, characteristic.getUuid());
            notificationQueue.addLast(new PendingNotification(address, characteristic,
                    Arrays.copyOf(value, value.length)));
        }
        sendNextNotification();
    }

    private void sendNextNotification() {
        BluetoothGattServer server = gattServer;
        if (!requested || server == null || notificationInFlight != null) {
            return;
        }
        while (!notificationQueue.isEmpty()) {
            PendingNotification pending = notificationQueue.removeFirst();
            Set<String> subscribers = subscribersFor(pending.characteristic);
            if (subscribers == null || !subscribers.contains(pending.address)) {
                continue;
            }
            try {
                BluetoothDevice device = adapter.getRemoteDevice(pending.address);
                pending.characteristic.setValue(pending.value);
                if (server.notifyCharacteristicChanged(device, pending.characteristic, false)) {
                    notificationInFlight = pending;
                    return;
                }
                Log.w(TAG, "Android rejected a queued Companion notification");
            } catch (RuntimeException exception) {
                Log.w(TAG, "Could not notify a Companion subscriber", exception);
            }
        }
    }

    private void removeQueuedNotification(String address, UUID characteristicUuid) {
        Iterator<PendingNotification> iterator = notificationQueue.iterator();
        while (iterator.hasNext()) {
            PendingNotification pending = iterator.next();
            if (pending.address.equals(address)
                    && pending.characteristic.getUuid().equals(characteristicUuid)) {
                iterator.remove();
            }
        }
    }

    private void removeQueuedNotifications(String address) {
        Iterator<PendingNotification> iterator = notificationQueue.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().address.equals(address)) {
                iterator.remove();
            }
        }
    }

    private synchronized void handleConnectionStateChange(int callbackGeneration,
                                                          BluetoothDevice device, int result,
                                                          int newState) {
        if (!isCurrentGeneration(callbackGeneration)) {
            return;
        }
        String address = device.getAddress();
        if (result == BluetoothGatt.GATT_SUCCESS
                && newState == BluetoothProfile.STATE_CONNECTED) {
            connectedDevices.add(address);
        } else {
            connectedDevices.remove(address);
            powerSubscribers.remove(address);
            cadenceSubscribers.remove(address);
            removeQueuedNotifications(address);
            if (notificationInFlight != null
                    && notificationInFlight.address.equals(address)) {
                notificationInFlight = null;
                sendNextNotification();
            }
        }
        updateActiveStatus();
    }

    private synchronized void handleNotificationSent(int callbackGeneration,
                                                     BluetoothDevice device, int result) {
        if (!isCurrentGeneration(callbackGeneration)) {
            return;
        }
        String address = device.getAddress();
        if (notificationInFlight == null
                || !notificationInFlight.address.equals(address)) {
            Log.d(TAG, "Ignoring an unmatched notification callback for " + address);
            return;
        }
        notificationInFlight = null;
        if (result != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "Companion notification failed with GATT status " + result);
        }
        sendNextNotification();
    }

    private byte[] valueFor(BluetoothGattCharacteristic characteristic) {
        UUID characteristicUuid = characteristic.getUuid();
        if (CYCLING_POWER_FEATURE.equals(characteristicUuid)) {
            return ZwiftBlePayload.cyclingPowerFeature();
        }
        if (CYCLING_POWER_SENSOR_LOCATION.equals(characteristicUuid)) {
            return ZwiftBlePayload.cyclingPowerSensorLocation();
        }
        if (CSC_FEATURE.equals(characteristicUuid)) {
            return ZwiftBlePayload.cscFeature();
        }
        SensorRepository.Snapshot snapshot = sensors.snapshot();
        boolean freshForZwift = snapshot.fresh && snapshot.ageMs <= SENSOR_FRESH_MS;
        if (CYCLING_POWER_MEASUREMENT.equals(characteristicUuid)) {
            return ZwiftBlePayload.cyclingPowerMeasurement(
                    snapshot.outputWatts, freshForZwift);
        }
        if (CSC_MEASUREMENT.equals(characteristicUuid)) {
            return crankTracker.measurement(snapshot.cadenceRpm,
                    SystemClock.elapsedRealtime(), freshForZwift);
        }
        return null;
    }

    private synchronized void handleCharacteristicRead(int callbackGeneration,
                                                       BluetoothDevice device, int requestId,
                                                       int offset,
                                                       BluetoothGattCharacteristic characteristic) {
        if (!isCurrentGeneration(callbackGeneration)) {
            return;
        }
        sendReadResponse(device, requestId, offset, valueFor(characteristic));
    }

    private synchronized void handleDescriptorRead(int callbackGeneration,
                                                   BluetoothDevice device, int requestId,
                                                   int offset,
                                                   BluetoothGattDescriptor descriptor) {
        if (!isCurrentGeneration(callbackGeneration)) {
            return;
        }
        sendReadResponse(device, requestId, offset, descriptorValue(device, descriptor));
    }

    private synchronized void handleDescriptorWriteRequest(int callbackGeneration,
                                                           BluetoothDevice device,
                                                           int requestId,
                                                           BluetoothGattDescriptor descriptor,
                                                           boolean preparedWrite,
                                                           boolean responseNeeded, int offset,
                                                           byte[] value) {
        if (!isCurrentGeneration(callbackGeneration)) {
            return;
        }
        int result = handleDescriptorWrite(device, descriptor, preparedWrite, offset, value);
        BluetoothGattServer server = gattServer;
        if (responseNeeded && server != null) {
            server.sendResponse(device, requestId, result, 0, null);
        }
    }

    private byte[] descriptorValue(BluetoothDevice device, BluetoothGattDescriptor descriptor) {
        if (!CLIENT_CHARACTERISTIC_CONFIGURATION.equals(descriptor.getUuid())) {
            return null;
        }
        Set<String> subscribers = subscribersFor(descriptor.getCharacteristic());
        synchronized (this) {
            return subscribers != null && subscribers.contains(device.getAddress())
                    ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
        }
    }

    private void sendReadResponse(BluetoothDevice device, int requestId, int offset,
                                  byte[] value) {
        BluetoothGattServer server = gattServer;
        if (server == null) {
            return;
        }
        if (value == null) {
            server.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    0, null);
        } else if (offset < 0 || offset > value.length) {
            server.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET,
                    offset, null);
        } else {
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, Arrays.copyOfRange(value, offset, value.length));
        }
    }

    private synchronized int handleDescriptorWrite(BluetoothDevice device,
                                                   BluetoothGattDescriptor descriptor,
                                                   boolean preparedWrite, int offset,
                                                   byte[] value) {
        if (preparedWrite) {
            return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        }
        if (offset != 0) {
            return BluetoothGatt.GATT_INVALID_OFFSET;
        }
        if (!CLIENT_CHARACTERISTIC_CONFIGURATION.equals(descriptor.getUuid())) {
            return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        }
        Set<String> subscribers = subscribersFor(descriptor.getCharacteristic());
        if (subscribers == null) {
            return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        }
        String address = device.getAddress();
        if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
            subscribers.add(address);
        } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
            subscribers.remove(address);
            removeQueuedNotification(address, descriptor.getCharacteristic().getUuid());
        } else {
            return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        }
        updateActiveStatus();
        return BluetoothGatt.GATT_SUCCESS;
    }

    private Set<String> subscribersFor(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return null;
        }
        if (CYCLING_POWER_MEASUREMENT.equals(characteristic.getUuid())) {
            return powerSubscribers;
        }
        if (CSC_MEASUREMENT.equals(characteristic.getUuid())) {
            return cadenceSubscribers;
        }
        return null;
    }

    private synchronized void updateActiveStatus() {
        if (!requested) {
            status = "OFF";
        } else if (!powerSubscribers.isEmpty() || !cadenceSubscribers.isEmpty()) {
            status = "COMPANION RECEIVING";
        } else if (!connectedDevices.isEmpty()) {
            status = "COMPANION CONNECTED";
        } else if (advertising) {
            status = "ADVERTISING";
        }
    }

    private synchronized void fail(String message) {
        Log.w(TAG, message);
        stopInternal(true);
        status = "ERROR: " + message;
    }

    private void stopInternal(boolean restoreBluetooth) {
        requested = false;
        bluetoothEnableRequested = false;
        generation++;
        handler.removeCallbacks(enablePoll);
        handler.removeCallbacks(notifyRunnable);
        if (advertiser != null && advertiseCallback != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (RuntimeException exception) {
                Log.d(TAG, "BLE advertisement was already stopped", exception);
            }
        }
        advertiser = null;
        advertiseCallback = null;
        advertising = false;
        if (gattServer != null) {
            try {
                gattServer.clearServices();
                gattServer.close();
            } catch (RuntimeException exception) {
                Log.d(TAG, "GATT server was already closed", exception);
            }
        }
        gattServer = null;
        powerMeasurement = null;
        cadenceMeasurement = null;
        powerSubscribers.clear();
        cadenceSubscribers.clear();
        connectedDevices.clear();
        notificationQueue.clear();
        notificationInFlight = null;
        if (restoreBluetooth) {
            requestBluetoothRestore();
        }
    }

    private void requestBluetoothRestore() {
        if (!enabledBluetooth) {
            setBluetoothOwnership(false);
            return;
        }
        if (adapter == null) {
            return;
        }
        try {
            int adapterState = adapter.getState();
            if (adapterState == BluetoothAdapter.STATE_OFF) {
                enabledBluetooth = false;
                setBluetoothOwnership(false);
                return;
            }
            if (adapterState != BluetoothAdapter.STATE_TURNING_OFF && !adapter.disable()) {
                Log.w(TAG, "Android rejected the Bluetooth restore request");
                return;
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not restore Bluetooth state", exception);
            return;
        }
        scheduleBluetoothRestoreWatcher(adapter, context.getSharedPreferences(
                RUNTIME_PREFS, Context.MODE_PRIVATE));
    }

    private synchronized boolean isCurrentGeneration(int callbackGeneration) {
        return requested && generation == callbackGeneration;
    }

    private void setBluetoothOwnership(boolean ownsBluetooth) {
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_OWNS_BLUETOOTH, ownsBluetooth)
                .commit();
    }

    private static synchronized void scheduleBluetoothRestoreWatcher(
            BluetoothAdapter adapter, SharedPreferences runtime) {
        cancelBluetoothRestoreWatcher();
        activeBluetoothRestoreWatcher = new BluetoothRestoreWatcher(adapter, runtime,
                SystemClock.elapsedRealtime() + BLUETOOTH_RESTORE_TIMEOUT_MS);
        BLUETOOTH_STATE_HANDLER.post(activeBluetoothRestoreWatcher);
    }

    private static synchronized void cancelBluetoothRestoreWatcher() {
        if (activeBluetoothRestoreWatcher != null) {
            BLUETOOTH_STATE_HANDLER.removeCallbacks(activeBluetoothRestoreWatcher);
            activeBluetoothRestoreWatcher = null;
        }
    }

    private static synchronized boolean isActiveBluetoothRestoreWatcher(
            BluetoothRestoreWatcher watcher) {
        return activeBluetoothRestoreWatcher == watcher;
    }

    private static synchronized void finishBluetoothRestoreWatcher(
            BluetoothRestoreWatcher watcher) {
        if (activeBluetoothRestoreWatcher == watcher) {
            activeBluetoothRestoreWatcher = null;
        }
    }

    private static UUID uuid(int shortUuid) {
        return UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", shortUuid));
    }

    private static final class PendingNotification {
        final String address;
        final BluetoothGattCharacteristic characteristic;
        final byte[] value;

        PendingNotification(String address, BluetoothGattCharacteristic characteristic,
                            byte[] value) {
            this.address = address;
            this.characteristic = characteristic;
            this.value = value;
        }
    }

    private static final class BluetoothRestoreWatcher implements Runnable {
        private final BluetoothAdapter adapter;
        private final SharedPreferences runtime;
        private final long deadlineMs;

        BluetoothRestoreWatcher(BluetoothAdapter adapter, SharedPreferences runtime,
                                long deadlineMs) {
            this.adapter = adapter;
            this.runtime = runtime;
            this.deadlineMs = deadlineMs;
        }

        @Override
        public void run() {
            if (!isActiveBluetoothRestoreWatcher(this)) {
                return;
            }
            try {
                if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                    runtime.edit().putBoolean(PREF_OWNS_BLUETOOTH, false).commit();
                    finishBluetoothRestoreWatcher(this);
                    return;
                }
            } catch (RuntimeException exception) {
                Log.w(TAG, "Could not confirm restored Bluetooth state", exception);
                finishBluetoothRestoreWatcher(this);
                return;
            }
            if (SystemClock.elapsedRealtime() < deadlineMs) {
                BLUETOOTH_STATE_HANDLER.postDelayed(this, 250L);
            } else {
                Log.w(TAG, "Bluetooth restore did not reach OFF; ownership retained");
                finishBluetoothRestoreWatcher(this);
            }
        }
    }
}
