package com.hack.note4.robot;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** RobotBrain: the phone IS the brain. Runs entirely on-device. Holds the camera open as the eye,
 *  reads every sensor continuously, and serves a tiny HTTP API on the LAN so anything on the network
 *  can ask what the phone sees / senses. No PC orchestration: capture and sensing happen here.
 * Frames come from the continuous preview (YUV → JPEG via YuvImage), so no crashy takePicture. */
public class RobotBrainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback, SensorEventListener {

    private Camera camera;
    private SurfaceHolder holder;
    private TextView readout;
    private PowerManager.WakeLock wake;
    private SensorManager sm;
    private HttpServer server;
    private final Map<String, float[]> sensorValues = new HashMap<>();
    private int previewW, previewH;
    private final AtomicReference<byte[]> lastPreview = new AtomicReference<>();
    private final AtomicReference<byte[]> lastJpeg = new AtomicReference<>();

    // PhoneBot: the phone sends joint targets over Wi-Fi to an onboard ESP32.
    private WifiSender wifi;
    private volatile String controllerIp = "192.168.5.200";  // set via /controller?ip=x.x.x.x
    private final int[] joints = new int[13];
    private volatile float walkPhase = 0f;
    private volatile int action = 0;
    private volatile String decision = "booting";
    private volatile int brightL = 0, brightR = 0;

    private static final int PORT = 8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF050608);

        SurfaceView preview = new SurfaceView(this);
        holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f));

        readout = new TextView(this);
        readout.setTextColor(0xFFE6E6F0);
        readout.setTextSize(13);
        readout.setTypeface(android.graphics.Typeface.MONOSPACE);
        readout.setPadding(dp(16), dp(12), dp(16), dp(12));
        readout.setGravity(Gravity.TOP);
        root.addView(readout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));
        setContentView(root);

        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        registerSensors();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "robotbrain");
        wake.acquire();

        wifi = new WifiSender(controllerIp, 4242);
        server = new HttpServer();
        server.start();
        new DecisionLoop().start();
        updateReadout();
    }

    private void registerSensors() {
        int[] types = {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_PROXIMITY, Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE,
                Sensor.TYPE_RELATIVE_HUMIDITY, Sensor.TYPE_AMBIENT_TEMPERATURE,
                Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR
        };
        for (int t : types) {
            Sensor s = sm.getDefaultSensor(t);
            if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] copy = new float[event.values.length];
        System.arraycopy(event.values, 0, copy, 0, event.values.length);
        sensorValues.put(event.sensor.getStringType(), copy);
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ---- Camera as the eye (continuous preview → JPEG) ----
    @Override
    public void surfaceCreated(SurfaceHolder h) {
        try {
            camera = Camera.open();
            Camera.Parameters p = camera.getParameters();
            Camera.Size sz = p.getPreviewSize();
            previewW = sz.width; previewH = sz.height;
            if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(p);
            camera.setDisplayOrientation(90);  // standard upright preview for a back camera
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (IOException e) {
            appendReadout("camera error: " + e.getMessage());
        }
    }
    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        if (camera != null) { camera.stopPreview(); camera.setPreviewCallback(null); camera.release(); camera = null; }
    }
    @Override
    public void onPreviewFrame(byte[] data, Camera c) {
        lastPreview.set(data);
        // Convert to JPEG lazily on demand; keep the latest YUV and convert when asked.
    }

    /** Returns the latest camera frame as JPEG, computed on-device from the preview YUV,
     *  rotated by `rot` degrees so the served frame is upright for however the phone is mounted. */
    byte[] captureFrame(int rot) {
        byte[] yuv = lastPreview.get();
        if (yuv == null || previewW == 0) return null;
        if (yuv.length < previewW * previewH * 3 / 2) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            YuvImage img = new YuvImage(yuv, ImageFormat.NV21, previewW, previewH, null);
            img.compressToJpeg(new Rect(0, 0, previewW, previewH), 80, out);
            byte[] jpg;
            if (rot == 0) {
                jpg = out.toByteArray();
            } else {
                Bitmap bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap rotb = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                ByteArrayOutputStream jpg2 = new ByteArrayOutputStream();
                rotb.compress(Bitmap.CompressFormat.JPEG, 80, jpg2);
                jpg = jpg2.toByteArray();
            }
            lastJpeg.set(jpg);
            return jpg;
        } catch (Exception e) {
            appendReadout("jpeg error: " + e.getMessage());
            return null;
        }
    }

    // ---- The brain: image + sensors -> action -> 13 joint targets -> Wi-Fi ----
    private class DecisionLoop extends Thread {
        @Override public void run() {
            int[] out = new int[4];
            float t = 0f;
            while (!isInterrupted()) {
                try {
                    decision = Brain.decide(lastPreview.get(), previewW, previewH, sensorValues, out);
                    action = out[0]; brightL = out[2]; brightR = out[3];
                    // Map the high-level action to 13 joint targets, then stream over Wi-Fi.
                    Gait.standing(joints);
                    Gait.balance(joints, sensorValues.get("android.sensor.gravity"));
                    if (action == Brain.WALK) {
                        Gait.stepInPlace(joints, t);
                        walkPhase = t;
                        t += 0.04f;
                    } else if (action == Brain.TURN_LEFT) {
                        joints[12] = 70;   // waist yaw left
                        Gait.stepInPlace(joints, t);
                        t += 0.04f;
                    } else if (action == Brain.TURN_RIGHT) {
                        joints[12] = 110;  // waist yaw right
                        Gait.stepInPlace(joints, t);
                        t += 0.04f;
                    } else {
                        t = 0f;
                    }
                    wifi.send(joints);
                } catch (Exception e) {}
                try { Thread.sleep(33); } catch (InterruptedException e) { break; }  // ~30 Hz
            }
        }
    }

    // ---- HTTP API served from the phone ----
    private class HttpServer extends Thread {
        private ServerSocket ss;
        @Override public void run() {
            try {
                ss = new ServerSocket(PORT);
                while (!isInterrupted()) {
                    final Socket s = ss.accept();
                    handle(s);
                }
            } catch (IOException e) { /* port busy or closing */ }
        }
        void close() { if (ss != null) try { ss.close(); } catch (IOException ignored) {} }
        private void handle(Socket s) {
            try {
                BufferedReader in = new BufferedReader(new java.io.InputStreamReader(s.getInputStream()));
                String line = in.readLine();
                if (line == null) { s.close(); return; }
                String[] req = line.split(" ");
                String path = req.length >= 2 ? req[1] : "/";
                int rot = 90;  // default; override with ?rot=0/90/180/270
                int q = path.indexOf('?');
                if (q >= 0) {
                    String query = path.substring(q + 1);
                    path = path.substring(0, q);
                    for (String kv : query.split("&")) {
                        if (kv.startsWith("rot=")) {
                            try { rot = Integer.parseInt(kv.substring(4)); } catch (Exception e) {}
                        } else if (kv.startsWith("ip=")) {
                            controllerIp = kv.substring(3);
                            if (wifi != null) wifi.close();
                            wifi = new WifiSender(controllerIp, 4242);
                        }
                    }
                }
                String hl;
                while ((hl = in.readLine()) != null && hl.length() > 0) {}
                OutputStream out = s.getOutputStream();
                if (path.equals("/see")) {
                    byte[] jpg = captureFrame(rot);
                    if (jpg == null) write(out, "503", "text/plain", "no frame yet".getBytes());
                    else write(out, "200", "image/jpeg", jpg);
                } else if (path.equals("/sensors")) {
                    write(out, "200", "application/json", sensorJson().getBytes());
                } else if (path.startsWith("/controller")) {
                    write(out, "200", "text/plain", ("controller=" + controllerIp).getBytes());
                } else {
                    write(out, "200", "text/html", statusPage().getBytes());
                }
                out.flush();
                s.close();
            } catch (IOException e) { /* client gone */ }
        }
        private void write(OutputStream out, String status, String ct, byte[] body) throws IOException {
            out.write(("HTTP/1.1 " + status + " OK\r\n").getBytes());
            out.write(("Content-Type: " + ct + "\r\n").getBytes());
            out.write(("Content-Length: " + body.length + "\r\n").getBytes());
            out.write("Connection: close\r\n\r\n".getBytes());
            out.write(body);
        }
    }

    private String sensorJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, float[]> e : sensorValues.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":[");
            float[] v = e.getValue();
            for (int i = 0; i < v.length; i++) { if (i > 0) sb.append(","); sb.append(String.format(Locale.US, "%.3f", v[i])); }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    private String statusPage() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>");
        sb.append("<meta http-equiv='refresh' content='3'></head><body style='font-family:monospace;background:#050608;color:#e6e6f0'>");
        sb.append("<h2>RobotBrain — Note 4</h2>");
        sb.append("<img src='/see' style='width:100%;max-width:520px;border:1px solid #333'><br>");
        sb.append("<p>GET <a href='/see'>/see</a> — latest camera frame (JPEG)<br>");
        sb.append("GET <a href='/sensors'>/sensors</a> — all sensors as JSON</p><hr><pre>");
        for (Map.Entry<String, float[]> e : sensorValues.entrySet()) {
            sb.append(e.getKey()).append(": ");
            for (float v : e.getValue()) sb.append(String.format(Locale.US, "%.3f ", v));
            sb.append("\n");
        }
        sb.append("</pre></body></html>");
        return sb.toString();
    }

    private String localIp() {
        try {
            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            int ip = info.getIpAddress();
            if (ip == 0) return "(no wifi)";
            return Formatter.formatIpAddress(ip);
        } catch (Exception e) { return "(no ip)"; }
    }

    private void updateReadout() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append("ROBOT BRAIN  http://").append(localIp()).append(":").append(PORT).append("\n");
                sb.append("controller: ").append(controllerIp).append(":4242 (Wi-Fi)\n");
                sb.append("endpoints:  /see  /sensors  /controller?ip=x.x.x.x\n");
                sb.append("action: ").append(actionName(action)).append("  [").append(brightL).append("/").append(brightR).append("]\n");
                sb.append("decision: ").append(decision).append("\n");
                sb.append("joints: ");
                for (int i = 0; i < 13; i++) sb.append(joints[i]).append(i < 12 ? " " : "\n");
                for (Map.Entry<String, float[]> e : sensorValues.entrySet()) {
                    sb.append(e.getKey()).append(": ");
                    for (float v : e.getValue()) sb.append(String.format(Locale.US, "%.2f ", v));
                    sb.append("\n");
                }
                readout.setText(sb.toString());
                readout.postDelayed(this, 500);
            }
        });
    }

    private void appendReadout(final String msg) {
        runOnUiThread(new Runnable() { @Override public void run() {
            readout.append("\n" + msg);
        }});
    }

    private String actionName(int a) {
        switch (a) {
            case Brain.WALK: return "WALK";
            case Brain.TURN_LEFT: return "TURN_LEFT";
            case Brain.TURN_RIGHT: return "TURN_RIGHT";
            case Brain.STOP: return "STOP";
            default: return "STAND";
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) server.interrupt();
        if (wake != null && wake.isHeld()) wake.release();
        if (sm != null) sm.unregisterListener(this);
        if (wifi != null) wifi.close();
        if (camera != null) { camera.setPreviewCallback(null); camera.release(); camera = null; }
    }
}
