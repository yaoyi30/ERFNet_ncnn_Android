package com.example.change_back;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int SELECT_IMAGE = 1;
    private Bitmap bitmap = null;
    private Bitmap yourSelectedImage = null;
    private ImageView org_img;
    private Button choose_img;
    private Button btnTakePhoto;
    private Button trans;
    private static final int takePhoto = 3;
    private Uri imageUri;
    private int current_color = 0;
    private File outputImage;
    private Bitmap targetImage = null;
    private ERFNetncnn personSeg = new ERFNetncnn();
    private Spinner spinnerModel;
    private Bitmap styledImage = null;
    private ImageView save_img;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        choose_img = findViewById(R.id.xuanzetupian);
        org_img = findViewById(R.id.showimg);
        btnTakePhoto = findViewById(R.id.photo);
        trans = findViewById(R.id.tran);
        save_img = (ImageView) findViewById(R.id.saveimg);

        initView();

        choose_img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent i = new Intent(Intent.ACTION_PICK);
                i.setType("image/*");
                startActivityForResult(i, SELECT_IMAGE);
            }
        });

        spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                current_color = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        save_img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                if (targetImage != null)
                {
                    String path = FileSaveToInside(MainActivity.this,"result.jpg",styledImage);
                    Toast.makeText(MainActivity.this, path, Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Toast.makeText(MainActivity.this,"?????????", Toast.LENGTH_SHORT).show();
                }

            }
        });

        trans.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (targetImage == null)
                {
                    Toast.makeText(MainActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    new Thread(new Runnable() {
                        public void run() {
                            styledImage = runStyleTransfer(true);
                            org_img.setImageBitmap(styledImage);
                        }
                    }).start();
                }
            }
        });

        btnTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    public void run() {
                        //  ??????File???????????????????????????????????????,?????????output_image.jdp
                        //  ???????????????SD?????????????????????????????????
                        outputImage = new File(getExternalCacheDir(), "output_image.jpg");
                        if (outputImage.exists()) {
                            outputImage.delete();
                        }
                        try {
                            outputImage.createNewFile();
                            //  ?????????????????????????????????Android 7.0
                            //  ?????????FileProvider???getUriForFile()?????????File?????????????????????????????????Uri?????????
                            //  ???????????????3????????????Context????????? ??????????????????????????? ?????????File?????????
                            //  ?????????????????????Android 7.0 ??????????????????????????????????????????Uri???????????????????????????????????????FileUriExposedException?????????
                            //      ???FileProvider??????????????????ContentProvider??????????????????ContentProvider????????????????????????????????????????????????????????????????????????Uri??????????????????
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                imageUri = FileProvider.getUriForFile(MainActivity.this, "com.example.permissiontest.fileprovider", outputImage);
                            } else {
                                //  ??????????????????Uri???fromFile()?????????File???????????????Uri??????
                                imageUri = Uri.fromFile(outputImage);
                            }
                            //  ????????????
                            Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                            //  ???????????????????????????,????????????????????????????????????output_image.jpg??????
                            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                            startActivityForResult(intent, takePhoto);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            }

        });

    }

    private void initView() {

        new Thread(new Runnable() {
            public void run() {
                boolean ret_init = personSeg.Init(getAssets());
                if (!ret_init)
                {
                    Log.e("MainActivity", "makeup Init failed");
                }
            }
        }).start();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case SELECT_IMAGE:
                if (resultCode == RESULT_OK && null != data) {
                    Uri selectedImage = data.getData();

                    try
                    {
                        if (requestCode == SELECT_IMAGE) {
                            bitmap = decodeUri(selectedImage);

                            targetImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                            org_img.setImageBitmap(bitmap);
                        }
                    }
                    catch (FileNotFoundException e)
                    {
                        Log.e("MainActivity", "FileNotFoundException");
                    }
                }

            case takePhoto:
                if (resultCode == RESULT_OK) {
                    try {
                        if (requestCode == takePhoto) {

                            //  decodeStream()?????????output_image.jpg?????????Bitmap?????????
                            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                            targetImage = rotateIfRequired(bitmap);
                            org_img.setImageBitmap(targetImage);
                        }


                    }catch (FileNotFoundException e) {
                        Log.e("MainActivity", "FileNotFoundException");
                        return;
                    }
                }
            default:
                break;
        }


    }

    private Bitmap rotateIfRequired(Bitmap bitmap) {
        try {
            ExifInterface exifInterface = new ExifInterface(outputImage.getPath());
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) return rotateBitmap(bitmap, 90);
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) return rotateBitmap(bitmap, 180);
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) return rotateBitmap(bitmap, 270);
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate((float)degree);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        //  ??????????????????Bitmap????????????
        bitmap.recycle();
        return rotatedBitmap;
    }


    private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException
    {
        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o);

        // The new size we want to scale to
        final int REQUIRED_SIZE = 640;

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = o.outWidth, height_tmp = o.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp / 2 < REQUIRED_SIZE
                    || height_tmp / 2 < REQUIRED_SIZE) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o2);

        // Rotate according to EXIF
        int rotate = 0;
        try
        {
            ExifInterface exif = new ExifInterface(getContentResolver().openInputStream(selectedImage));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
        }
        catch (IOException e)
        {
            Log.e("MainActivity", "ExifInterface IOException");
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotate);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap runStyleTransfer(boolean use_gpu)
    {
        Bitmap InputImage = targetImage.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap OutputImage = targetImage.copy(Bitmap.Config.ARGB_8888, true);
        personSeg.Process(InputImage,OutputImage,current_color, use_gpu);
        return OutputImage;
    }

    /**
     * ???????????????????????????
     * @param context ?????????
     * @param fileName ?????????
     * @param bitmap ??????
     * @return ????????????????????????????????????
     */
    public static String FileSaveToInside(Context context, String fileName, Bitmap bitmap) {
        FileOutputStream fos = null;
        String path = null;
        try {
            //???????????? /Android/data/com.panyko.filesave/Pictures/
            File folder = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            //????????????????????????
            //??????????????????????????????
            if (folder.exists() ||folder.mkdir()) {
                File file = new File(folder, fileName);
                fos = new FileOutputStream(file);
                //????????????
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                path = file.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            try {
                if (fos != null) {
                    //?????????
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        try {
            MediaStore.Images.Media.insertImage(context.getContentResolver(),
                    path,path, null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,  Uri.parse("file://"
                + Environment.getExternalStorageDirectory())));
        //????????????
        return path;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Permission.checkPermission(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(Permission.isPermissionGranted(this)) {
            Log.i("PERMISSION","??????????????????");
        }
    }

}

class Permission {
    public static final int REQUEST_CODE = 5;
    //??????????????????
    private static final String[] permission = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };
    //????????????????????????
    public static boolean isPermissionGranted(Activity activity){
        if(Build.VERSION.SDK_INT >= 23){
            for(int i = 0; i < permission.length;i++) {
                int checkPermission = ContextCompat.checkSelfPermission(activity,permission[i]);
                /***
                 * checkPermission???????????????
                 * ?????????: PackageManager.PERMISSION_GRANTED
                 * ?????????: PackageManager.PERMISSION_DENIED
                 */
                if(checkPermission != PackageManager.PERMISSION_GRANTED){
                    return false;
                }
            }
            return true;
        }else{
            return true;
        }
    }

    public static boolean checkPermission(Activity activity){
        if(isPermissionGranted(activity)) {
            return true;
        } else {
            //??????????????????????????????????????????????????????????????????
            ActivityCompat.requestPermissions(activity,permission,REQUEST_CODE);
            return false;
        }
    }
}