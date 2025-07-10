package com.genzopia.Instagame.Post;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.genzopia.Instagame.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class ShortsFragment extends Fragment {
    private static final String TAG = "ShortsFragment";
    private static final int MAX_RECORDING_TIME = 30000; // 30 seconds

    private PreviewView previewView;
    private ImageButton recordButton;
    private ImageButton switchCameraButton;
    private ImageButton closeButton;
    private ImageButton torchButton;
    private ProgressBar recordProgressBar;

    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private boolean isRecording = false;
    private Executor cameraExecutor;
    private Handler progressHandler;
    private Uri lastRecordedVideoUri;
    private CameraSelector currentCameraSelector;
    private Camera camera;
    private boolean isTorchOn = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = true;
                        for (Boolean granted : result.values()) {
                            if (!granted) {
                                allGranted = false;
                                break;
                            }
                        }
                        if (allGranted) {
                            startCamera();
                        } else {
                            Toast.makeText(requireContext(), 
                                "Permissions required to use camera", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           ViewGroup container,
                           Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_short, container, false);

        // Initialize views
        previewView = view.findViewById(R.id.previewView);
        recordButton = view.findViewById(R.id.recordButton);
        switchCameraButton = view.findViewById(R.id.switchCameraButton);
        closeButton = view.findViewById(R.id.closeButton);
        torchButton = view.findViewById(R.id.torchButton);
        recordProgressBar = view.findViewById(R.id.recordProgressBar);

        // Initialize camera executor and progress handler
        cameraExecutor = ContextCompat.getMainExecutor(requireContext());
        progressHandler = new Handler(Looper.getMainLooper());
        currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        // Set up click listeners
        setupRecordButton();
        setupSwitchCameraButton();
        setupCloseButton();
        setupTorchButton();

        // Check permissions and start camera
        checkPermissionsAndStart();

        return view;
    }

    private void setupRecordButton() {
        recordButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Start recording on press
                    if (!isRecording) {
                        startRecording();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Stop recording on release
                    if (isRecording) {
                        stopRecording();
                    }
                    return true;
            }
            return false;
        });

        recordButton.setOnClickListener(v -> {
            // Toggle recording on click
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
    }

    private void setupSwitchCameraButton() {
        switchCameraButton.setOnClickListener(v -> {
            currentCameraSelector = (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) ?
                    CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
            isTorchOn = false; // Reset torch when switching camera
            startCamera();
        });
    }

    private void setupCloseButton() {
        closeButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            }
            requireActivity().onBackPressed();
        });
    }

    private void setupTorchButton() {
        torchButton.setOnClickListener(v -> {
            if (camera != null && currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                isTorchOn = !isTorchOn;
                camera.getCameraControl().enableTorch(isTorchOn);
                updateTorchButton();
            }
        });
    }

    private void updateTorchButton() {
        if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            torchButton.setVisibility(View.VISIBLE);
            torchButton.setImageResource(R.drawable.ic_torch);
        } else {
            torchButton.setVisibility(View.GONE);
        }
    }

    private void checkPermissionsAndStart() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Set up video capture use case
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                // Unbind all use cases and bind new ones
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this,
                        currentCameraSelector,
                        preview,
                        videoCapture
                );

                updateTorchButton();

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
            }
        }, cameraExecutor);
    }

    @SuppressLint("MissingPermission")
    private void startRecording() {
        if (videoCapture == null) return;

        // Show progress bar
        recordProgressBar.setVisibility(View.VISIBLE);
        recordProgressBar.setProgress(0);

        // Create video capture options
        String filename = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis()) + ".mp4";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/InstaGameShorts");
        }

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        // Start recording
        currentRecording = videoCapture.getOutput()
                .prepareRecording(requireContext(), options)
                .withAudioEnabled()
                .start(cameraExecutor, event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        isRecording = true;
                        updateRecordingUI(true);
                        startProgressUpdate();
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        isRecording = false;
                        updateRecordingUI(false);
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;
                        if (finalizeEvent.hasError()) {
                            String msg = "Video capture failed: " + finalizeEvent.getError();
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            Log.e(TAG, msg);
                        } else {
                            lastRecordedVideoUri = finalizeEvent.getOutputResults().getOutputUri();
                            showVideoPreview();
                        }
                    }
                });
    }

    private void stopRecording() {
        if (currentRecording != null && isRecording) {
            currentRecording.stop();
            currentRecording = null;
            stopProgressUpdate();
        }
    }

    private void updateRecordingUI(boolean isRecording) {
        recordButton.setSelected(isRecording);
        switchCameraButton.setEnabled(!isRecording);
        // Allow torch control during recording
        torchButton.setEnabled(true);
    }

    private void startProgressUpdate() {
        recordProgressBar.setProgress(0);
        progressHandler.postDelayed(new Runnable() {
            int progress = 0;
            @Override
            public void run() {
                if (isRecording && progress < MAX_RECORDING_TIME) {
                    progress += 100; // Update every 100ms
                    recordProgressBar.setProgress(progress);
                    progressHandler.postDelayed(this, 100);
                } else if (progress >= MAX_RECORDING_TIME) {
                    stopRecording();
                }
            }
        }, 100);
    }

    private void stopProgressUpdate() {
        progressHandler.removeCallbacksAndMessages(null);
        recordProgressBar.setVisibility(View.INVISIBLE);
    }

    private void showVideoPreview() {
        if (lastRecordedVideoUri != null) {
            Intent intent = new Intent(requireContext(), VideoPreviewActivity.class);
            intent.putExtra("video_uri", lastRecordedVideoUri.toString());
            intent.putExtra("is_recorded_video", true);
            startActivity(intent);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isRecording) {
            stopRecording();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        progressHandler.removeCallbacksAndMessages(null);
    }
}
