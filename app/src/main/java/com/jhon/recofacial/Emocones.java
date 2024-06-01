package com.jhon.recofacial;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.android.Utils;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Emocones extends AppCompatActivity {

    private PreviewView previewView;
    private FaceOverlayView faceOverlayView;
    private ExecutorService executor;
    private Interpreter tflite;
    private String[] labels;
    private TextView emotionTextView;
    private ImageAnalysis imageAnalysis;
    private Button buttonFlipCamera;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private CascadeClassifier faceCascade;
    private boolean isUsingFrontCamera = true;
    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emocones);

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV");
        } else {
            Log.d("OpenCV", "OpenCV loaded");
        }

        previewView = findViewById(R.id.previewView);
        faceOverlayView = findViewById(R.id.faceOverlayView);
        emotionTextView = findViewById(R.id.emotionTextView);
        executor = Executors.newSingleThreadExecutor();
        buttonFlipCamera = findViewById(R.id.button_flip_camera);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        try {
            tflite = new Interpreter(loadModelFile(), new Interpreter.Options());
            labels = loadLabels();
            loadHaarCascade();
        } catch (IOException e) {
            e.printStackTrace();
        }
        buttonFlipCamera.setOnClickListener(v -> flipCamera());
    }
    private void flipCamera() {
        isUsingFrontCamera = !isUsingFrontCamera;
        startCamera();
    }
    private void loadHaarCascade() throws IOException {
        InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
        File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");
        FileOutputStream os = new FileOutputStream(mCascadeFile);

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        is.close();
        os.close();

        faceCascade = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        if (faceCascade.empty()) {
            Log.e("CascadeClassifier", "Failed to load cascade classifier");
            faceCascade = null;
        } else {
            Log.d("CascadeClassifier", "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
        }
        cascadeDir.delete();
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT) // Usar cámara frontal
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(224, 224))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(executor, this::analyzeImage);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    @ExperimentalGetImage
    private void analyzeImage(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            Bitmap bitmap = toBitmap(mediaImage);
            if (bitmap != null) {
                // Detect faces in the bitmap
                List<Rect> faceRects = detectFaces(bitmap);

                // Set the detected face rectangles to the overlay view
                runOnUiThread(() -> faceOverlayView.setFaceRects(faceRects));

                // Ensure the bitmap is resized to the correct dimensions for the model input
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

                // Prepare the input tensor buffer
                TensorBuffer inputBuffer = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
                inputBuffer.loadBuffer(convertBitmapToByteBuffer(resizedBitmap));

                // Prepare the output tensor buffer
                TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, labels.length}, DataType.FLOAT32);

                // Run inference
                tflite.run(inputBuffer.getBuffer(), outputBuffer.getBuffer().rewind());

                // Process the output probabilities
                float[] probabilities = outputBuffer.getFloatArray();
                showResult(probabilities);
            }
        }
        imageProxy.close();
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 50, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.rewind();

        int[] intValues = new int[224 * 224];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int pixelValue : intValues) {
            byteBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f);
            byteBuffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);
            byteBuffer.putFloat((pixelValue & 0xFF) / 255.0f);
        }
        return byteBuffer;
    }

    private List<Rect> detectFaces(Bitmap bitmap) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);

        MatOfRect faces = new MatOfRect();
        faceCascade.detectMultiScale(gray, faces);

        List<Rect> faceRects = new ArrayList<>();
        for (org.opencv.core.Rect face : faces.toArray()) {
            faceRects.add(new Rect(face.x, face.y, face.width, face.height));
        }

        return faceRects;
    }

    private void showResult(float[] probabilities) {
        if (probabilities == null || probabilities.length == 0) {
            return;
        }

        int maxIndex = 0;
        float maxProbability = -1.0f;

        for (int i = 0; i < probabilities.length; i++) {
            if (!Float.isNaN(probabilities[i]) && probabilities[i] > maxProbability) {
                maxProbability = probabilities[i];
                maxIndex = i;
            }
        }

        if (maxProbability != -1.0f) {
            @SuppressLint("DefaultLocale") String emotionText = labels[maxIndex] + ": " + String.format("%.2f", maxProbability * 100) + "%";
            runOnUiThread(() -> {
                emotionTextView.setText(emotionText);
                emotionTextView.setTextColor(Color.rgb(new Random().nextInt(256), new Random().nextInt(256), new Random().nextInt(256)));
            });
        } else {
            Log.e("Probabilities", "All values are NaN");
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private String[] loadLabels() throws IOException {
        InputStream is = getAssets().open("labels.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        List<String> labelList = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList.toArray(new String[0]);
    }
}