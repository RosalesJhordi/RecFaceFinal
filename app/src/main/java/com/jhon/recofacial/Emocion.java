package com.jhon.recofacial;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.media.FaceDetector;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

public class Emocion extends AppCompatActivity {
    ImageView imageView;
    Button btnCamera;
    TextView tv;
    Bitmap croppedbmp;
    String result = "EMOCION NO DETECTADA";
    int count = 0;
    ColorMatrix matrix = new ColorMatrix();
    private final int mInputSize = 224;
    private String mModelPath = "converted_model2.tflite";
    private String mLabelPath = "labels.txt";
    private Clasificador clasificador;
    private static final int MAX_FACES = 4;
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_emocion);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        try {
            initClassifier();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initClassifier() throws IOException
    {
        clasificador = new Clasificador(getAssets(), mModelPath, mLabelPath, mInputSize);
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initViews() {
        btnCamera = (Button)findViewById(R.id.btnCamera);
        imageView = (ImageView)findViewById(R.id.imageView);
        tv = (TextView)findViewById(R.id.textview1);

        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count++;
                imageView.setImageDrawable(getResources().getDrawable(R.drawable.defaultpic));
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent,0);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bitmap bitmap = (Bitmap)data.getExtras().get("data");
        Bitmap imgmood = bitmap;

        imageView.setImageBitmap(imgmood);

        int fcount;
        Bitmap facedet = convert(bitmap, Bitmap.Config.RGB_565);
        fcount = setFace(facedet);

        if(fcount==1)
        {
            detect();
        }
        if(fcount>1)
        {
            tv.setText("EMOCION NO DETECTADA");
        }
        if(fcount==0)
        {
            tv.setText("EMOCION NO DETECTADA");
        }

    }

    private Bitmap convert(Bitmap bitmap, Bitmap.Config config) {
        Bitmap convertedBitmap = Bitmap.createBitmap(bitmap.getWidth()+1, bitmap.getHeight(), config);
        Canvas canvas = new Canvas(convertedBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return convertedBitmap;
    }

    public int setFace(Bitmap b) {
        Bitmap mFaceBitmap = b;
        FaceDetector fd;
        FaceDetector.Face[] faces = new FaceDetector.Face[MAX_FACES];
        int count = 0;
        int mFaceHeight = mFaceBitmap.getHeight();
        int mFaceWidth = mFaceBitmap.getWidth();
        try {
            fd = new FaceDetector(mFaceWidth, mFaceHeight, MAX_FACES);
            count = fd.findFaces(mFaceBitmap, faces);
            imageView.invalidate();

        } catch (Exception e) {
            Log.e("FaceDetection - ", "setFace(): " + e.toString());
        }

        PointF midpoint = new PointF();
        int [] fpx = null;
        int [] fpy = null;
        int i = 0,width=0,height=0;
        int myEyesDistance = 0;
        if (count > 0) {
            fpx = new int[count];
            fpy = new int[count];

            for (i = 0; i < count; i++) {
                try {
                    faces[i].getMidPoint(midpoint);
                    fpx[i] = (int) midpoint.x;
                    fpy[i] = (int) midpoint.y;
                    myEyesDistance = (int) faces[i].eyesDistance();
                    imageView.invalidate();
                } catch (Exception e) {
                    Log.e("Cropped image - ", "setFace(): face " + i + ": " + e.toString());
                }
            }

            int x = fpx[0] - myEyesDistance*2;
            int y = fpy[0] - myEyesDistance*2;
            int x2 = fpx[0] + myEyesDistance*2;
            int y2 = fpy[0] + myEyesDistance*2;
            width = x2 - x;
            height = y2 - y;

            if((width + x) >b.getWidth())
            {
                x = fpx[0] - myEyesDistance;
                x2 = fpx[0] + myEyesDistance;
                width = x2 - x;
            }
            if((height + y) >b.getHeight())
            {
                y = fpy[0] - myEyesDistance;
                y2 = fpy[0] + myEyesDistance;
                height = y2 - y;

            }

            if(x<0 || y<0 || x2<0 || y2<0) {

            }
            else {
                croppedbmp = Bitmap.createBitmap(b, x, y, width, height);
                imageView.setImageBitmap(croppedbmp);
            }
        }
        return count;

    }

    private void detect()
    {
        Bitmap bitmap = ((BitmapDrawable)((ImageView)imageView).getDrawable()).getBitmap();
        result = clasificador.recognizemood(bitmap);
        tv.setText(result);
    }
}