package com.example.bvoice;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

//import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class ModelClass {

    //Interpreter to load model
    public Interpreter interpreter;
    // store all label
    private List<String> labelList;

    private Map<Integer, String> p2s_map;
    private float[] output = new float[250];

    //initialize gpu on app
    private GpuDelegate gpuDelegate;

    ModelClass(AssetManager assetManager, String modelPath, String labelPath) throws IOException {
        //Init options
        Interpreter.Options options = new Interpreter.Options();
        gpuDelegate = new GpuDelegate();
        p2s_map = new HashMap<>();


        //load model
        interpreter = new Interpreter(loadModelFile(assetManager, modelPath), options);

        readJsonFile(assetManager, "sign_to_prediction_index_map.json");
        Log.d("NHUTHAO_p2s", p2s_map.get(1));


    }


    private void readJsonFile(AssetManager assetManager, String filePath) throws IOException {
//         = getAssets();
        InputStream inputStream = assetManager.open(filePath);


        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line);

        }

        String[] pairs = content.toString().split(", ");

        for (String pair : pairs) {
            String[] keyValue = pair.split(": ");

            String key_str = keyValue[1].trim().replaceAll("\"", "").replace("{", "").replace("}", "");
            String value = keyValue[0].trim().replaceAll("\"", "").replace("{", "").replace("}", "").toLowerCase();

            int key = Integer.parseInt(key_str);
            p2s_map.put(key, value);
        }

        inputStream.close();


    }


    private ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        //use to get description of file
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();


        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    public static float[][][] generateRandomArray(int height, int width, int channels) {
        float[][][] randomArray = new float[height][width][channels];
        Random random = new Random();

        // Fill the array with random float values
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                for (int c = 0; c < channels; c++) {
                    randomArray[i][j][c] = random.nextFloat();
                }
            }
        }

        return randomArray;
    }

    public static float[][] generateRandomArray(int height, int width) {
        float[][] randomArray = new float[height][width];
        Random random = new Random();

        // Fill the array with random float values
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                randomArray[i][j] = random.nextFloat();
            }
        }
        return randomArray;
    }

    private static int findMaxIndex(float[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array must not be null or empty");
        }

        int maxIndex = 0;
        float maxValue = array[0];

        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    public static float[][][] convert(List<float[][]> inputList) {
        float[][][] result = new float[inputList.size()][543][3];

        for (int i = 0; i < inputList.size(); i++) {
            float [][] item = inputList.get(i);
            for (int j = 0; j < 543; j++) {
                for (int k = 0; k < 3; k++) {
                    result[i][j][k] = item[j][k];
                }
            }
        }

        return result;
    }

    public String predict(List<float[][]> inputList) {
        float[][][] input = convert(inputList);

        float[] output = new float[250];
        interpreter.run(input, output);
        int idx = findMaxIndex(output);
        return p2s_map.get(idx);
    }


}
