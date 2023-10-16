package com.chivumarius.selfiesegmentationwithmlkit;



import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;


public class MainActivity extends AppCompatActivity {


    private static final int RESULT_LOAD_IMAGE = 123;
    public static final int IMAGE_CAPTURE_CODE = 654;
    private static final int PERMISSION_CODE = 321;


    // ▼ "DECLARATION" OF "WIDGET ID" ▼
    ImageView innerImage;


    private Uri image_uri;


    // (STEP 1-1 → "SELFIE SEGMENTATION") "DECLARATION" OF "SEGMENTER":
    Segmenter segmenter;



    // ▬ "ON CREATE()" METHOD ▬
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // ▼ "INSTANTIALIZATION" OF "WIDGET ID" ▼
        innerImage = findViewById(R.id.imageView2);

        // ▼ SETTING "ON CLICK LISTENER()" METHOD → ON "INNER IMAGE" ▼
        innerImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ▼ OPEN "GALLERY" TO "CHOOSE" AN "IMAGE" ▼
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, RESULT_LOAD_IMAGE);
            }
        });



        // ▼ SETTING "ON LONG CLICK LISTENER()" METHOD → ON "INNER IMAGE" ▼
        innerImage.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_DENIED){
                        String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                        requestPermissions(permission, PERMISSION_CODE);
                    }
                    else {

                        // ▼ "CALLING" THE "METHOD" TO "OPEN" THE "CAMERA" ▼
                        openCamera();
                    }
                }

                else {
                    // ▼ "CALLING" THE "METHOD" TO "OPEN" THE "CAMERA" ▼
                    openCamera();
                }

                return false;
            }
        });




        // (STEP 1-2 → "SELFIE SEGMENTATION") "INITIALIZATION" OF "SELFIE SEGMENTATION"
        //        → FOR "SINGLE IMAGE MODE":
        SelfieSegmenterOptions options =
                new SelfieSegmenterOptions.Builder()
                        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
//                        .enableRawSizeMask()
                        .build();


        // "SEGMENTER"
        segmenter = Segmentation.getClient(options);

    }




    // ▬ "ON REQUEST PERMISSIONS RESULT()" METHOD ▬
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            //TODO show live camera footage
            openCamera();
        } else {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show();
        }
    }




    // ▬ "ON CLICK()" METHOD ▬
    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE);
    }




    // ▬ "ON ACTIVITY RESULT()" METHOD ▬
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null){
            image_uri = data.getData();
            innerImage.setImageURI(image_uri);
            doSegmentation();
        }

        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK){
            innerImage.setImageURI(image_uri);
            doSegmentation();
        }
    }





    // ▬ "ON SEGMENTATION()" METHOD
    //      → PERFORM SELFIE SEGMENTATION ▬
    public void doSegmentation(){
        Bitmap inputImage = uriToBitmap(image_uri);
        final Bitmap mutableBmp = inputImage.copy(Bitmap.Config.ARGB_8888,true);
        Bitmap rotated = rotateBitmap(mutableBmp);


        // (STEP 2 → "SELFIE SEGMENTATION") "PREPARING" THE "INPUT IMAGE":
        InputImage image = InputImage.fromBitmap(rotated, 0);



        // (STEP 3 → "SELFIE SEGMENTATION") "PROCESS" THE "IMAGE":
        Task<SegmentationMask> result =
                segmenter.process(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<SegmentationMask>() {
                                    @Override
                                    public void onSuccess(SegmentationMask segmentationMask) {

                                        // (STEP 4 → "SELFIE SEGMENTATION") "SEGMENTATION MASK"
                                        //       → "SEPARATION" OF "BACKGROUND" FROM "PERSON":

                                        // ▼ GETTING "BUFFER", "WIDTH", "HEIGHT"
                                        //      → OF "SEGMENTATION MASK"
                                        //      → THE "SIZE" OF "MASK"
                                        //      → IS THE "SIZE" OF THE "IMAGE" ▼
                                        ByteBuffer mask = segmentationMask.getBuffer();
                                        int maskWidth = segmentationMask.getWidth();
                                        int maskHeight = segmentationMask.getHeight();


                                        // ▼ "CREATING" A "NEW BITMAP" ▼
                                        Bitmap resultBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888);


                                        //▼ LOOPING OVER "SEGMENTATION MASK"
                                        //      → FOM "0" → TO "MASK HEIGHT" ▼
                                        for (int y = 0; y < maskHeight; y++) {

                                            //▼ LOOPING OVER "SEGMENTATION MASK"
                                            //      → FOM "0" → TO "MASK WIDTH" ▼
                                            for (int x = 0; x < maskWidth; x++) {

                                                // ▼ GETS THE "CONFIDENCE" OF THE "(X, Y) PIXEL "
                                                //      → IN THE "MASK" BEING IN THE "FOREGROUND" ▼
                                                float foregroundConfidence = mask.getFloat();


                                                // ▼ CHECKS IF "CONFIDENCE" IS "GREATER" THAN "0.5" ▼
                                                if (foregroundConfidence >= 0.5) {
                                                    // Add your code here
                                                    resultBitmap.setPixel(x, y, rotated.getPixel(x, y));
                                                }
                                            }
                                        }

                                        // ▼ SHOWING  THIS "RESULT BITMAP"
                                        //      → "INSIDE" OF AN "IMAGE VIEW" ▼
                                        innerImage.setImageBitmap(resultBitmap);
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                        // ...
                                    }
                                });
    }





    // ▬ "ON DESTROY()" METHOD ▬
    @Override
    protected void onDestroy() {
        super.onDestroy();

    }




    // ▬ "ROTATE BITMAP()" METHOD
    //      → TO "ROTATE IMAGE" IF "IMAGE" IS "CAPTURED" ON "SAMSUNG DEVICE "▬
    public Bitmap rotateBitmap(Bitmap input){
        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
        Cursor cur = getContentResolver().query(image_uri, orientationColumn, null, null, null);
        int orientation = -1;
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
        }

        Log.d("tryOrientation",orientation+"");
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(orientation);
        Bitmap cropped = Bitmap.createBitmap(input,0,0, input.getWidth(), input.getHeight(), rotationMatrix, true);
        return cropped;
    }




    // ▬ "URI TO BITMAP()" METHOD
    //      → IT "TAKES URI" OF THE "IMAGE" AND RETURNS "BITMAP" ▬
    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);

            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  null;
    }

}



