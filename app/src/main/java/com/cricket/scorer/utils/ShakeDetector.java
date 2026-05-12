package com.cricket.scorer.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * ShakeDetector.java
 *
 * Lightweight shake gesture detector. Listens to the accelerometer and
 * fires onShake() when the user shakes the device hard enough.
 *
 * Algorithm:
 * Compute the magnitude of acceleration with gravity removed
 * (essentially the device's own motion, independent of orientation).
 * A "shake event" is registered when magnitude exceeds SHAKE_THRESHOLD.
 * Require at least SHAKE_COUNT events within SHAKE_WINDOW_MS to fire
 * onShake — this prevents single jolts (e.g. dropping the phone on a
 * table) from triggering it.
 *
 * Lifecycle:
 * Call start() in Activity.onResume and stop() in onPause to avoid
 * draining the battery while the app is in the background.
 *
 * Throttle:
 * After firing, ignores further shakes for COOLDOWN_MS to prevent
 * accidentally launching the bug report dialog twice.
 */
public class ShakeDetector implements SensorEventListener {

    /** ~2.7g — needs a deliberate shake, not just walking. */
    private static final float SHAKE_THRESHOLD   = 2.7f * SensorManager.GRAVITY_EARTH;
    /** Number of strong-acceleration samples needed within the window. */
    private static final int   SHAKE_COUNT       = 3;
    /** Time window for collecting samples. */
    private static final long  SHAKE_WINDOW_MS   = 1000;
    /** Ignore further shakes for this long after firing. */
    private static final long  COOLDOWN_MS       = 2000;

    public interface OnShakeListener {
        void onShake();
    }

    private final SensorManager   sensorManager;
    private final Sensor          accelerometer;
    private final OnShakeListener listener;

    private long firstShakeMs    = 0;
    private int  shakeSamples    = 0;
    private long lastFiredMs     = 0;

    public ShakeDetector(Context ctx, OnShakeListener listener) {
        this.sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        this.listener      = listener;
    }

    public void start() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void stop() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        firstShakeMs = 0;
        shakeSamples = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        if (magnitude < SHAKE_THRESHOLD) return;

        long now = System.currentTimeMillis();
        // Cooldown
        if (now - lastFiredMs < COOLDOWN_MS) return;

        if (firstShakeMs == 0 || now - firstShakeMs > SHAKE_WINDOW_MS) {
            // Start a fresh window
            firstShakeMs = now;
            shakeSamples = 1;
            return;
        }
        shakeSamples++;
        if (shakeSamples >= SHAKE_COUNT) {
            lastFiredMs  = now;
            firstShakeMs = 0;
            shakeSamples = 0;
            if (listener != null) listener.onShake();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* unused */ }
}
