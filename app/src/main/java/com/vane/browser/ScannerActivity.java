package com.vane.browser;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.vane.browser.settings.Prefs;
import com.vane.browser.settings.UiConfig;
import com.vane.browser.ui.Theme;
import com.vane.browser.ui.Widgets;

import java.util.Arrays;

/** 扫码：系统 Camera2 自己取景（不依赖 CameraX），中间画取景框，识别到就返回。 */
public class ScannerActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 9001;

    private TextureView preview;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private ImageReader reader;
    private CameraDevice device;
    private CameraCaptureSession session;
    private final MultiFormatReader zxing = new MultiFormatReader();
    private volatile boolean done = false;
    private volatile boolean busy = false;
    private UiConfig ui;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        ui = UiConfig.load();
        Theme.applyToWindow(this, ui);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        preview = new TextureView(this);
        root.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 取景框
        View frame = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(Theme.dp(this, 18));
        g.setStroke(Theme.dp(this, 2), 0xFFFFFFFF);
        frame.setBackground(g);
        int size = (int) (getResources().getDisplayMetrics().widthPixels * 0.68);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(size, size);
        flp.gravity = Gravity.CENTER;
        root.addView(frame, flp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(0, 0, 0, Theme.dp(this, 44));
        bottom.addView(Widgets.text(this, "把二维码放进框里", 14, 0xFFE6E7EA));
        TextView cancel = Widgets.ghostButton(this, ui, "取消", this::finish);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Theme.dp(this, 16);
        bottom.addView(cancel, clp);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        root.addView(bottom, blp);

        setContentView(root);

        bgThread = new HandlerThread("scan");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_CAMERA && (res.length == 0 || res[0] != PackageManager.PERMISSION_GRANTED)) {
            Widgets.toast(this, "没给相机权限，扫不了");
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) startPreview();
    }

    private void startPreview() {
        if (device != null || !preview.isAvailable()) {
            if (!preview.isAvailable()) {
                preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture s, int w, int h) {
                        openCamera();
                    }
                    @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
                    @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return false; }
                    @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
                });
            }
            return;
        }
        openCamera();
    }

    private void openCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cm == null) {
                Widgets.toast(this, "没有相机服务");
                finish();
                return;
            }
            String id = null;
            for (String cid : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(cid);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    id = cid;
                    break;
                }
            }
            if (id == null && cm.getCameraIdList().length > 0) id = cm.getCameraIdList()[0];
            if (id == null) {
                Widgets.toast(this, "没有可用相机");
                finish();
                return;
            }
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size pick = null;
            if (map != null) {
                Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                for (Size s : sizes) {
                    if (s.getWidth() >= 640 && s.getHeight() >= 480) {
                        if (pick == null || s.getWidth() * s.getHeight() < pick.getWidth() * pick.getHeight()) {
                            pick = s;
                        }
                    }
                }
                if (pick == null && sizes.length > 0) pick = sizes[0];
            }
            if (pick == null) pick = new Size(640, 480);
            reader = ImageReader.newInstance(pick.getWidth(), pick.getHeight(), ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onFrame, bgHandler);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) return;
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(@NonNull CameraDevice cam) {
                    device = cam;
                    startSession();
                }
                @Override public void onDisconnected(@NonNull CameraDevice cam) { cam.close(); }
                @Override public void onError(@NonNull CameraDevice cam, int error) {
                    cam.close();
                    runOnUiThread(() -> {
                        Widgets.toast(ScannerActivity.this, "相机打开失败：" + error);
                        finish();
                    });
                }
            }, bgHandler);
        } catch (Exception e) {
            Widgets.toast(this, "相机不可用：" + e.getMessage());
            finish();
        }
    }

    private void startSession() {
        try {
            SurfaceTexture st = preview.getSurfaceTexture();
            if (st == null) return;
            st.setDefaultBufferSize(preview.getWidth(), preview.getHeight());
            Surface surface = new Surface(st);
            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(surface);
            b.addTarget(reader.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            device.createCaptureSession(Arrays.asList(surface, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(@NonNull CameraCaptureSession s) {
                            session = s;
                            try {
                                s.setRepeatingRequest(b.build(), null, bgHandler);
                            } catch (CameraAccessException ignored) {}
                        }
                        @Override public void onConfigureFailed(@NonNull CameraCaptureSession s) {}
                    }, bgHandler);
        } catch (Exception ignored) {}
    }

    private void onFrame(ImageReader r) {
        if (done || busy) {
            try { r.acquireLatestImage().close(); } catch (Exception ignored) {}
            return;
        }
        busy = true;
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null) return;
            Image.Plane plane = img.getPlanes()[0];
            java.nio.ByteBuffer buf = plane.getBuffer();
            int w = img.getWidth(), h = img.getHeight();
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(
                    data, plane.getRowStride(), h, 0, 0, w, h, false);
            String text = zxing.decodeWithState(new BinaryBitmap(new HybridBinarizer(src))).getText();
            if (text != null && !text.isEmpty()) {
                done = true;
                runOnUiThread(() -> {
                    setResult(RESULT_OK, new Intent().putExtra("text", text));
                    finish();
                });
            }
        } catch (Exception ignored) {
        } finally {
            if (img != null) img.close();
            busy = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (device != null) device.close(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        if (bgThread != null) bgThread.quitSafely();
    }
}
