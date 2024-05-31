package com.jhon.recofacial;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Registro extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 101;

    private ImageView imageView;
    private Uri imageUri;

    EditText name;
    Button btn_register;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_SHORT).show();
            return;
        }

        imageView = findViewById(R.id.imageView);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            openCamera();
        }

        btn_register = findViewById(R.id.btn_register);
        name = findViewById(R.id.name);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        btn_register.setOnClickListener(v -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                File tempFile = File.createTempFile("Temp_img", ".jpg", getCacheDir());

                try (OutputStream os = new FileOutputStream(tempFile)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                String tempFilePath = tempFile.getAbsolutePath();

                callPythonMethodWithTempFile(tempFilePath);


            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void callPythonMethodWithTempFile(String tempFilePath) {
        Python python = Python.getInstance();
        PyObject pythonFile = python.getModule("entrenamiento");
        String nm = name.getText().toString();
        pythonFile.callAttr("model", tempFilePath, nm);

        PyObject result = pythonFile.callAttr("model", tempFilePath, nm);

        String resultString = result.toString();
        Toast.makeText(this, resultString, Toast.LENGTH_SHORT).show();
        //startActivity(new Intent(Registro.this,Login.class));
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageURI(imageUri);
        }
    }
}