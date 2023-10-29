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
import android.widget.TextView;

import androidx.annotation.NonNull;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.List;

public class TranslateActivity extends AppCompatActivity {
    private ArrayList<String> keywords = new ArrayList<>();
    public static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");
    static OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private TextView sentenceView;
    public static boolean isShowingKeypoints = true;
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
    List<float[][]> holisticLandmarkList = new ArrayList<float[][]>();
    //    List<List<Float>> frameLandmarks = new ArrayList<>();
    float[][] frameLandmarks = new float[landmarkCount][coordinateCount];

    // check landmarks present
    private boolean isPosePresent = false;
    private boolean isLeftHandPresent = false;
    private boolean isRightHandPresent = false;
    private boolean isFacePresent = false;
//    private boolean isAllLandmarksPresent = false;

    private Map<String, Boolean> isAdded = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translate);

        initWithNaN(frameLandmarks);
        Log.d("NaN init", Arrays.toString(frameLandmarks));

        for (String s : new String[]{"face", "left", "pose", "right"}) {
            isAdded.put(s, false);
        }

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
                        applicationInfo.metaData.getString(isShowingKeypoints ? "outputVideoStreamName" : "inputVideoStreamName")
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

        sentenceView = findViewById(R.id.chatgpt_sentence);
        startRecordBtn = findViewById(R.id.record_btn);
        startRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isRecording = !isRecording;
                int record_btn_style = isRecording ? R.drawable.stop_record : R.drawable.start_record;
                startRecordBtn.setImageResource(record_btn_style);

                if (!isRecording) {
                    callChatGPTAPI(keywords);
                    Log.d("Keyword list", keywords.toString());
                    keywords.clear();
                }
//                else {
//                    for (int i = 0; i < 10; i++) {
//                        float[][][] inputArray = ModelClass.generateRandomArray(25, 543, 3);
//                        String prediction = model.predict(inputArray);
//                        Log.d("NHUTHAO_mainactivity", prediction);
//                        keywords.add(prediction);
//                    }
                startCollectingKeypoints();
//                }
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

    private void startCollectingKeypoints() {
        CollectKeypointsRunnable runnable = new CollectKeypointsRunnable();
        new Thread(runnable).start();
    }

    private void initWithNaN(float[][] frameLandmarks) {
        // Fill each row with NaN
        for (float[] row : frameLandmarks)
            Arrays.fill(row, Float.NaN);
    }

    public void addSubtitle(String sentence) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sentenceView.setText(sentence);
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
//                isFacePresent = false;
                isAdded.put("face", true);
                Log.d(TAG, "no face landmarks");

            }
        };

        Handler handler = new Handler(Looper.getMainLooper());

        processor.addPacketCallback(
                "face_landmarks",
                (packet) -> {
                    try {
                        // check landmarks presence
//                        isFacePresent = true;
//                        isAllLandmarksPresent = isLeftHandPresent && isRightHandPresent && isFacePresent && isPosePresent;

                        // handle packet
                        byte[] protoBytes = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList landmarksList = LandmarkProto.NormalizedLandmarkList.parser().parseFrom(protoBytes);
//                        if (isAllLandmarksPresent) {
                        addLandMarksToList("face", landmarksList);
//                        }

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
                for (int i = 0; i < 468; i++) {
                    for (LandmarkProto.NormalizedLandmark landmark : landmarksList.getLandmarkList()) {
                        frameLandmarks[i][0] = landmark.getX();
                        frameLandmarks[i][1] = landmark.getY();
                        frameLandmarks[i][2] = landmark.getZ();
                    }
                }
                break;

            case "left":
                for (int i = 468; i < 489; i++) {
                    for (LandmarkProto.NormalizedLandmark landmark : landmarksList.getLandmarkList()) {
                        frameLandmarks[i][0] = landmark.getX();
                        frameLandmarks[i][1] = landmark.getY();
                        frameLandmarks[i][2] = landmark.getZ();
                    }
                }
                break;

            case "pose":
                for (int i = 489; i < 522; i++) {
                    for (LandmarkProto.NormalizedLandmark landmark : landmarksList.getLandmarkList()) {
                        frameLandmarks[i][0] = landmark.getX();
                        frameLandmarks[i][1] = landmark.getY();
                        frameLandmarks[i][2] = landmark.getZ();
                    }
                }
                break;

            case "right":
                for (int i = 522; i < 543; i++) {
                    for (LandmarkProto.NormalizedLandmark landmark : landmarksList.getLandmarkList()) {
                        frameLandmarks[i][0] = landmark.getX();
                        frameLandmarks[i][1] = landmark.getY();
                        frameLandmarks[i][2] = landmark.getZ();
                    }
                }
                break;

            default:
                Log.d(TAG + " " + "addLandmarkToList", "landmark type not found");
                break;
        }
        isAdded.put(landmarkType, true);
    }

    private void setupRightHandCallback() {
        Log.d(TAG, "Setting up right hand callback");
        Runnable resetRunnable = new Runnable() {
            @Override
            public void run() {
                isRightHandPresent = false;
                isAdded.put("right", true);
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
//                        isAllLandmarksPresent = isLeftHandPresent && isRightHandPresent && isFacePresent && isPosePresent;

                        // handle packet
                        byte[] protoBytes = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList landmarksList = LandmarkProto.NormalizedLandmarkList.parser().parseFrom(protoBytes);
//                        if (isAllLandmarksPresent) {
                        addLandMarksToList("right", landmarksList);
//                        }

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
        Log.d("Testing", "Sequence Size: " + String.valueOf(holisticLandmarkList.size()));

        String predictedWord = MainActivity.model.predict(holisticLandmarkList);
        Log.d("predicted word", predictedWord);
        keywords.add(predictedWord);

        addSubtitle(keywords.toString());

//        Log.d(TAG, "holisticLandmarkList size0: " + holisticLandmarkList.size());
//        Log.d(TAG, "holisticLandmarkList size1: " + holisticLandmarkList.get(0).size());
//        Log.d(TAG, "holisticLandmarkList size2: " + holisticLandmarkList.get(0).get(0).size());
        holisticLandmarkList.clear();
        initWithNaN(frameLandmarks);
    }

    private void setupPoseCallback() {
        Log.d(TAG, "Setting up pose callback");
        Runnable resetRunnable = new Runnable() {
            @Override
            public void run() {
//                isPosePresent = false;
                isAdded.put("pose", true);
                Log.d(TAG, "no pose landmarks");
            }
        };

        Handler handler = new Handler(Looper.getMainLooper());

        processor.addPacketCallback(
                "pose_landmarks",
                (packet) -> {
                    try {
                        // check landmarks presence
//                        isPosePresent = true;
//                        isAllLandmarksPresent = isLeftHandPresent && isRightHandPresent && isFacePresent && isPosePresent;

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
                isAdded.put("left", true);
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
//                        isAllLandmarksPresent = isLeftHandPresent && isRightHandPresent && isFacePresent && isPosePresent;

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

    private static String readAuthorizationFromFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line = reader.readLine();
            if (line != null) {
                return line.trim(); // Trim any leading/trailing whitespace
            } else {
                // Handle the case where the file is empty or doesn't exist
                throw new IOException("Authorization file is empty or not found.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return filePath;
    }

    private void callChatGPTAPI(ArrayList<String> question) {

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "gpt-3.5-turbo");

            JSONArray messageArr = new JSONArray();
            JSONObject obj = new JSONObject();
            obj.put("role", "user");
            obj.put("content", "Transfer these keywords extracted from ASL into a complete text sentence: " + question.toString() + ". Provide only the sentence");
            messageArr.put(obj);

            jsonBody.put("messages", messageArr);


        } catch (JSONException e) {
            e.printStackTrace();
        }
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        String authorization = readAuthorizationFromFile("authorization.txt");
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", authorization)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d("Chatgpt Prompt", "Failed to load response due to " + e.getMessage());
                addSubtitle(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        JSONArray jsonArray = jsonObject.getJSONArray("choices");
                        String result = jsonArray.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                        Log.d("Chatgpt Prompt", result.trim());
                        addSubtitle(result.trim());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d("Chatgpt Prompt", "Failed to load response due to " + response.body().string());
                    addSubtitle(response.body().string());
                }
            }
        });
    }

    class CollectKeypointsRunnable implements Runnable {
        @Override
        public void run() {
//            while (isRecording) {
//                float[][][] inputArray = ModelClass.generateRandomArray(25, 543, 3);
//                String prediction = MainActivity.model.predict(inputArray);
//                Log.d("NHUTHAO_mainactivity", prediction);
//                keywords.add(prediction);
//
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
            while (isRecording) {
                Log.d("Testing", "hands: " + String.valueOf(isLeftHandPresent) + " " + String.valueOf(isRightHandPresent));
                while (isLeftHandPresent || isRightHandPresent) {
//                    while ( !(isAdded.get("face") && isAdded.get("left") && isAdded.get("pose") && isAdded.get("right")) ) {
//
//                    }

                    holisticLandmarkList.add(frameLandmarks);
                    Log.d("Testing", "adding to sequence");
                    initWithNaN(frameLandmarks);

                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    if (holisticLandmarkList.size() > 200) {
                        break;
                    }

//                    for (String s : new String[]{"face", "left", "pose", "right"}) {
//                        isAdded.put(s, false);
//                    }
                }
                if (holisticLandmarkList.size() != 0 && !isLeftHandPresent && !isRightHandPresent) {
                    endWord();
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}