package com.example.bvoice;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TranslateActivity extends AppCompatActivity {
    private ImageButton gobackBtn;
    private ImageButton startRecordBtn;
    private boolean isRecording = false;
    private ImageButton flipCamera;
    private boolean cameraFacingFront = false;
    private static final String TAG = "TranslateActivity";
    private ViewGroup viewGroup;

    // Flips the camera-preview frames vertically by default, before sending them into FrameProcessor
    // to be processed in a MediaPipe graph, and flips the processed frames back when they are
    // displayed. This maybe needed because OpenGL represents images assuming the image origin is at
    // the bottom-left corner, whereas MediaPipe in general assumes the image origin is at the
    // top-left corner.
    // NOTE: use "flipFramesVertically" in manifest metadata to override this behavior.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        try {
            System.loadLibrary("opencv_java3");
        } catch (UnsatisfiedLinkError e) {
            // Some example apps (e.g. template matching) require OpenCV 4.
            System.loadLibrary("opencv_java4");
        }
    }

    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    protected FrameProcessor processor;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    protected CameraXPreviewHelper cameraHelper;

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;

    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;

    private int frameIndex = 0;

    // Define the dimensions for your array
    int landmarkCount = 543; // Number of landmarks per frame
    int coordinateCount = 3; // Number of coordinates (x, y, z) per landmark

    // Initialize a list to store the landmarks
//    List<List<List<float[]>>> holisticLandmarkList = new ArrayList<>();
    List<List<List<Float>>> holisticLandmarkList = new ArrayList<>();
    List<List<Float>> frameLandmarks = new ArrayList<>();

    // check landmarks present
    private boolean isPosePresent = false;
    private boolean isLeftHandPresent = false;
    private boolean isRightHandPresent = false;
    private boolean isFacePresent = false;
    private boolean isAllLandmarksPresent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translate);

        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        applicationInfo.metaData.getString("binaryGraphName"),
                        applicationInfo.metaData.getString("inputVideoStreamName"),
                        applicationInfo.metaData.getString("outputVideoStreamName")
                );

        processor
                .getVideoSurfaceOutput()
                .setFlipY(
                        applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));

        PermissionHelper.checkAndRequestCameraPermissions(this);

        gobackBtn = findViewById(R.id.go_back_btn);
        gobackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backToMenu();
            }
        });

        startRecordBtn = findViewById(R.id.record_btn);
        startRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isRecording = !isRecording;
                int record_btn_style = isRecording ? R.drawable.stop_record : R.drawable.start_record;
                startRecordBtn.setImageResource(record_btn_style);
            }
        });

        flipCamera = findViewById(R.id.flip_camera);
        flipCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraFacingFront = !cameraFacingFront;
            }
        });
    }

    // Slide animation when gobackBtn is clicked
    private void backToMenu() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    // Slide animation when return button (the button next to home button) is clicked
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter = new ExternalTextureConverter(eglManager.getContext());
        converter.setFlipY(
                applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
            setupLeftHandCallback();
            setupRightHandCallback();
            setupPoseCallback();
            setupFaceCallback();
        }
    }

    private void setupFaceCallback() {
        Log.d(TAG, "Setting up face callback");
        Runnable resetRunnable = new Runnable() {
            @Override
            public void run() {
                isFacePresent = false;
                Log.d(TAG, "no face landmarks");

            }
        };

        Handler handler = new Handler(Looper.getMainLooper());

        processor.addPacketCallback(
                "face_landmarks",
                (packet) -> {
                    try {
                        // check landmarks presence
                        isFacePresent = true;
                        isAllLandmarksPresent = isLeftHandPresent && isRightHandPresent && isFacePresent && isPosePresent;

                        // handle packet
                        byte[] protoBytes = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList landmarksList = LandmarkProto.NormalizedLandmarkList.parser().parseFrom(protoBytes);
                        if (isAllLandmarksPresent) {
                            addLandMarksToList("face", landmarksList);
                        }

                        // inform handler that landmarks are present
                        handler.removeCallbacksAndMessages(null);
                        handler.postDelayed(resetRunnable, 1000L);
                    } catch (Exception e) {
                        Log.e(TAG, "accessing face_landmarks failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    private void addLandMarksToList(String landmarkType, LandmarkProto.NormalizedLandmarkList landmarksList) {
        switch (landmarkType) {
            case "face":
                Log.d(TAG, "case face, frameLandmark size: " + frameLandmarks.size());
                // only add when the number of landmarks for this frame is empty
                if (frameLandmarks.size() == 0) {
                    for (LandmarkProto.NormalizedLandmark landmark : landmarksList.getLandmarkList()) {
                        List<Float> landmarkValues = new ArrayList<>();
                        landmarkValues.add(landmark.getX());
                        landmarkValues.add(landmark.getY());
                        landmarkValues.add(landmark.getZ());
                        frameLandmarks.add(landmarkValues);
                    }
                    Log.d(TAG, "added face landmarks, frameLandmark size: " + frameLandmarks.size());
                }
                break;

            case "left":
                // only add when the number of landmarks for this frame is 468
                Log.d(TAG, "case left, frameLandmark size: " + frameLandmarks.size());
                if (frameLandmarks.size() == 468) {
                    for (LandmarkProto.NormalizedLandmark landmark : landmarksList.getLandmarkList()) {
                        List<Float> landmarkValues = new ArrayList<>();
                        landmarkValues.add(landmark.getX());
                        landmarkValues.add(landmark.getY());
                        landmarkValues.add(landmark.getZ());
                        frameLandmarks.add(landmarkValues);
                    }
                    Log.d(TAG, "added left hand landmarks, frameLandmark size: " + frameLandmarks.size());
                }
                break;

            case "pose":
                // only add when the number of landmarks for this frame is 522
                Log.d(TAG, "case pose, frameLandmark size: " + frameLandmarks.size());
                if (frameLandmarks.size() == 489) {
                    for (LandmarkProto.NormalizedLandmark landmark : landmarksList.getLandmarkList()) {
                        List<Float> landmarkValues = new ArrayList<>();
                        landmarkValues.add(landmark.getX());
                        landmarkValues.add(landmark.getY());
                        landmarkValues.add(landmark.getZ());
                        frameLandmarks.add(landmarkValues);
                    }
                    Log.d(TAG, "added pose landmarks, frameLandmark size: " + frameLandmarks.size());
                }
                break;

            case "right":
                Log.d(TAG, "case right, frameLandmark size: " + frameLandmarks.size());
                // Only add when the number of landmarks for this frame is 522
                if (frameLandmarks.size() == 522) {
                    for (LandmarkProto.NormalizedLandmark landmark : landmarksList.getLandmarkList()) {
                        List<Float> landmarkValues = new ArrayList<>();
                        landmarkValues.add(landmark.getX());
                        landmarkValues.add(landmark.getY());
                        landmarkValues.add(landmark.getZ());
                        frameLandmarks.add(landmarkValues);
                    }
                    Log.d(TAG, "added right hand landmarks, frameLandmark size: " + frameLandmarks.size());
                    holisticLandmarkList.add(frameLandmarks);
                    frameIndex++;
                    frameLandmarks = new ArrayList<>();
                    Log.d(TAG, "finished adding landmarks for frame " + frameIndex);
                }
                break;

            default:
                Log.d(TAG + " " + "addLandmarkToList", "landmark type not found");
                break;
        }
    }

    private void setupRightHandCallback() {
        Log.d(TAG, "Setting up right hand callback");
        Runnable resetRunnable = new Runnable() {
            @Override
            public void run() {
                isRightHandPresent = false;
                endWord();
                Log.d(TAG, "no right hand landmarks");
            }
        };

        Handler handler = new Handler(Looper.getMainLooper());

        processor.addPacketCallback(
                "right_hand_landmarks",
                (packet) -> {
                    try {
                        // check landmarks presence
                        isRightHandPresent = true;
                        isAllLandmarksPresent = isLeftHandPresent && isRightHandPresent && isFacePresent && isPosePresent;

                        // handle packet
                        byte[] protoBytes = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList landmarksList = LandmarkProto.NormalizedLandmarkList.parser().parseFrom(protoBytes);
                        if (isAllLandmarksPresent) {
                            addLandMarksToList("right", landmarksList);
                        }

                        // inform handler that landmarks are present
                        handler.removeCallbacksAndMessages(null);
                        handler.postDelayed(resetRunnable, 1000L);
                    } catch (Exception e) {
                        Log.e(TAG, "accessing right_hand_landmarks failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    private void endWord() {
        Log.d(TAG, "endWord");
        Log.d(TAG, "holisticLandmarkList size0: " + holisticLandmarkList.size());
        Log.d(TAG, "holisticLandmarkList size1: " + holisticLandmarkList.get(0).size());
        Log.d(TAG, "holisticLandmarkList size2: " + holisticLandmarkList.get(0).get(0).size());
        holisticLandmarkList = new ArrayList<>();
        frameLandmarks = new ArrayList<>();
    }

    private void setupPoseCallback() {
        Log.d(TAG, "Setting up pose callback");
        Runnable resetRunnable = new Runnable() {
            @Override
            public void run() {
                isPosePresent = false;
                Log.d(TAG, "no pose landmarks");
            }
        };

        Handler handler = new Handler(Looper.getMainLooper());

        processor.addPacketCallback(
                "pose_landmarks",
                (packet) -> {
                    try {
                        // check landmarks presence
                        isPosePresent = true;
                        isAllLandmarksPresent = isLeftHandPresent && isRightHandPresent && isFacePresent && isPosePresent;

                        byte[] protoBytes = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList landmarksList = LandmarkProto.NormalizedLandmarkList.parser().parseFrom(protoBytes);
                        addLandMarksToList("pose", landmarksList);

                        handler.removeCallbacksAndMessages(null);
                        handler.postDelayed(resetRunnable, 1000L);
                    } catch (Exception e) {
                        Log.e(TAG, "accessing left_hand_landmarks failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    private void setupLeftHandCallback() {
        Log.d(TAG, "Setting up left hand callback");
        Runnable resetRunnable = new Runnable() {
            @Override
            public void run() {
                isLeftHandPresent = false;
                Log.d(TAG, "no left hand landmarks");
            }
        };

        Handler handler = new Handler(Looper.getMainLooper());

        processor.addPacketCallback(
                "left_hand_landmarks",
                (packet) -> {
                    try {
                        // check landmarks presence
                        isLeftHandPresent = true;
                        isAllLandmarksPresent = isLeftHandPresent && isRightHandPresent && isFacePresent && isPosePresent;

                        byte[] protoBytes = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList landmarksList = LandmarkProto.NormalizedLandmarkList.parser().parseFrom(protoBytes);
                        addLandMarksToList("left", landmarksList);

                        handler.removeCallbacksAndMessages(null);
                        handler.postDelayed(resetRunnable, 1000L);
                    } catch (Exception e) {
                        Log.e(TAG, "accessing left_hand_landmarks failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing =
                cameraFacingFront
                        ? CameraHelper.CameraFacing.FRONT
                        : CameraHelper.CameraFacing.BACK;
        cameraHelper.startCamera(
                this, cameraFacing, /*surfaceTexture=*/ null, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }
}