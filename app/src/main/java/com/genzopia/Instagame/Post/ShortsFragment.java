package com.genzopia.Instagame.Post;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.video.VideoRecordEvent.Finalize;
import androidx.camera.video.Recording;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.genzopia.Instagame.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class ShortsFragment extends Fragment {

    private static final String TAG = "ShortsFragment";

    private PreviewView previewView;
    private FloatingActionButton recordButton;

    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private boolean isRecording = false;
    private Executor cameraExecutor;

    // Launcher to request permissions
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        // Log each permission
                        result.forEach((perm, granted) ->
                                Log.d(TAG, perm + " granted? " + granted)
                        );
                        // If all needed are granted, start camera
                        if (result.values().stream().allMatch(Boolean::booleanValue)) {
                            startCamera();
                        } else {
                            Log.w(TAG, "Required permissions not granted");
                        }
                    }
            );

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View v = inflater.inflate(R.layout.fragment_shorts_upload, container, false);

        previewView   = v.findViewById(R.id.previewView);
        recordButton  = v.findViewById(R.id.record_button);
        cameraExecutor = ContextCompat.getMainExecutor(requireContext());

        recordButton.setOnClickListener(btn -> {
            if (isRecording) stopRecording();
            else startRecording();
        });

        checkPermissionsAndStart();
        return v;
    }

    private void checkPermissionsAndStart() {
        // On Android Q+ we only need CAMERA + RECORD_AUDIO for MediaStoreOutputOptions
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // on Android 9 and below, still need WRITE_EXTERNAL_STORAGE
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        // Check each
        boolean allGranted = true;
        for (String p : perms) {
            boolean granted = ContextCompat.checkSelfPermission(requireContext(), p)
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, p + " granted? " + granted);
            if (!granted) allGranted = false;
        }

        if (allGranted) {
            Log.d(TAG, "All permissions already granted, starting camera");
            startCamera();
        } else {
            Log.d(TAG, "Requesting permissions");
            requestPermissionLauncher.launch(perms.toArray(new String[0]));
        }
    }

    private void startCamera() {
        ProcessCameraProvider
                .getInstance(requireContext())
                .addListener(() -> {
                    try {
                        ProcessCameraProvider provider =
                                ProcessCameraProvider.getInstance(requireContext()).get();

                        // Preview use-case
                        Preview preview = new Preview.Builder().build();
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        // VideoCapture use-case
                        Recorder recorder = new Recorder.Builder()
                                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                                .build();
                        videoCapture = VideoCapture.withOutput(recorder);

                        // Bind to lifecycle
                        provider.unbindAll();
                        provider.bindToLifecycle(
                                this,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                videoCapture
                        );

                        Log.d(TAG, "Camera bound (preview + video)");

                    } catch (ExecutionException | InterruptedException e) {
                        Log.e(TAG, "startCamera failed", e);
                    }
                }, cameraExecutor);
    }

    @SuppressLint("MissingPermission")
    private void startRecording() {
        if (videoCapture == null) return;

        String filename = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis()) + ".mp4";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/InstaGameShorts"
            );
        }

        MediaStoreOutputOptions options = new MediaStoreOutputOptions
                .Builder(
                requireContext().getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
                .setContentValues(contentValues)
                .build();

        currentRecording = videoCapture.getOutput()
                .prepareRecording(requireContext(), options)
                .withAudioEnabled()
                .start(cameraExecutor, event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        isRecording = true;
                        recordButton.setImageResource(R.drawable.ic_accept);
                    } else if (event instanceof Finalize) {
                        isRecording = false;
                        recordButton.setImageResource(R.drawable.ic_reject);
                        Finalize f = (Finalize) event;
                        if (f.hasError()) {
                            Log.e(TAG, "Recording error: " + f.getError());
                        }
                    }
                });
    }

    private void stopRecording() {
        if (currentRecording != null && isRecording) {
            currentRecording.stop();
            currentRecording = null;
        }
    }
}
