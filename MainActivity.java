package com.example.jagadish.motion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.jagadish.motion.R;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Video.OnVideoClickListener {

    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private Camera camera;
    private MediaRecorder mediaRecorder;
    private SurfaceView cameraPreview;
    private SurfaceHolder surfaceHolder;
    private boolean isRecording = false;
    private RecyclerView capturedVideosRecyclerView;
    private Video capturedVideoAdapter;
    private List<String> capturedVideos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraPreview = findViewById(R.id.camera_preview);
        surfaceHolder = cameraPreview.getHolder();
        surfaceHolder.addCallback(this);

        capturedVideosRecyclerView = findViewById(R.id.capturedVideosRecyclerView);
        capturedVideos = new ArrayList<>();
        capturedVideoAdapter = new Video(this, capturedVideos, this);
        capturedVideosRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        capturedVideosRecyclerView.setAdapter(capturedVideoAdapter);

        if (allPermissionsGranted()) {
            initializeCamera();
        } else {
            requestPermissions();
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initializeCamera();
            } else {
                Toast.makeText(this, "All permissions are required for this app to function properly.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeCamera() {
        try {
            camera = Camera.open();
            if (camera == null) {
                Toast.makeText(this, "Failed to open camera. Please restart the app.", Toast.LENGTH_LONG).show();
                return;
            }
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            simulateMotionDetection();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error setting up camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (allPermissionsGranted()) {
            initializeCamera();
        } else {
            requestPermissions();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }

        try {
            if (camera != null) {
                camera.stopPreview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (camera != null) {
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error restarting camera preview: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    private void startRecording() {
        if (isRecording) {
            return;
        }

        if (prepareVideoRecorder()) {
            mediaRecorder.start();
            isRecording = true;
        } else {
            releaseMediaRecorder();
            Toast.makeText(this, "Failed to start recording.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder.stop();
                releaseMediaRecorder();
                if (camera != null) {
                    camera.lock();
                }
                isRecording = false;
                String videoPath = getOutputMediaFile().getAbsolutePath();
                capturedVideos.add(0, videoPath);
                capturedVideoAdapter.notifyItemInserted(0);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error stopping recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean prepareVideoRecorder() {
        if (camera == null) {
            Toast.makeText(this, "Camera is not available.", Toast.LENGTH_SHORT).show();
            return false;
        }

        camera.unlock();

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setCamera(camera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);

        // Set default video size and frame rate
        mediaRecorder.setVideoSize(640, 480);
        mediaRecorder.setVideoFrameRate(30);

        try {
            Camera.Parameters parameters = camera.getParameters();
            if (parameters != null) {
                List<Camera.Size> supportedVideoSizes = parameters.getSupportedVideoSizes();
                if (supportedVideoSizes != null && !supportedVideoSizes.isEmpty()) {
                    Camera.Size size = supportedVideoSizes.get(0);
                    mediaRecorder.setVideoSize(size.width, size.height);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error setting video size. Using default size.", Toast.LENGTH_SHORT).show();
        }

        File outputFile = getOutputMediaFile();
        if (outputFile == null) {
            Toast.makeText(this, "Failed to create output file.", Toast.LENGTH_SHORT).show();
            return false;
        }
        mediaRecorder.setOutputFile(outputFile.toString());
        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());

        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to prepare MediaRecorder: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
            return false;
        }

        return true;
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            if (camera != null) {
                camera.lock();
            }
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "CCTV");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Toast.makeText(this, "Failed to create directory for storing videos.", Toast.LENGTH_SHORT).show();
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
    }

    private void simulateMotionDetection() {
        // This is a placeholder for motion detection logic
        // In a real application, you would implement actual motion detection here

        cameraPreview.postDelayed(new Runnable() {
            @Override
            public void run() {
                startRecording();
                // Stop recording after 5 seconds
                cameraPreview.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopRecording();
                        // Simulate next motion detection
                        simulateMotionDetection();
                    }
                }, 5000);
            }
        }, 10000); // Simulate motion detection every 15 seconds (10 seconds wait + 5 seconds recording)
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            if (camera == null) {
                initializeCamera();
            }
        } else {
            requestPermissions();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();
        releaseCamera();
    }

    @Override
    public void onVideoClick(String videoPath) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(videoPath), "video/mp4");
        startActivity(intent);
    }
}

