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
                    Toast.makeText(MainActivity.this,"无图片", Toast.LENGTH_SHORT).show();
                }

            }
        });

        trans.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (targetImage == null)
                {
                    Toast.makeText(MainActivity.this, "未选图片", Toast.LENGTH_SHORT).show();
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
                        //  创建File对象，用于存储拍照后的图片,命名为output_image.jdp
                        //  存放在手机SD卡的应用关联缓存目录下
                        outputImage = new File(getExternalCacheDir(), "output_image.jpg");
                        if (outputImage.exists()) {
                            outputImage.delete();
                        }
                        try {
                            outputImage.createNewFile();
                            //  如果运行设备的系统高于Android 7.0
                            //  就调用FileProvider的getUriForFile()方法将File对象转换成一个封装过的Uri对象。
                            //  该方法接收3个参数：Context对象， 任意唯一的字符串， 创建的File对象。
                            //  这样做的原因：Android 7.0 开始，直接使用本地真实路径的Uri是被认为是不安全的，会抛出FileUriExposedException异常；
                            //      而FileProvider是一种特殊的ContentProvider，他使用了和ContentProvider类似的机制对数据进行保护，可以选择性地将封装过的Uri共享给外部。
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                imageUri = FileProvider.getUriForFile(MainActivity.this, "com.example.permissiontest.fileprovider", outputImage);
                            } else {
                                //  否则，就调用Uri的fromFile()方法将File对象转换成Uri对象
                                imageUri = Uri.fromFile(outputImage);
                            }
                            //  启动相机
                            Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                            //  指定图片的输出地址,这样拍下的照片会被输出到output_image.jpg中。
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

                            //  decodeStream()可以将output_image.jpg解析成Bitmap对象。
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
        //  将不再需要的Bitmap对象回收
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
     * 保存图片到沙盒目录
     * @param context 上下文
     * @param fileName 文件名
     * @param bitmap 文件
     * @return 路径，为空时表示保存失败
     */
    public static String FileSaveToInside(Context context, String fileName, Bitmap bitmap) {
        FileOutputStream fos = null;
        String path = null;
        try {
            //设置路径 /Android/data/com.panyko.filesave/Pictures/
            File folder = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            //判断目录是否存在
            //目录不存在时自动创建
            if (folder.exists() ||folder.mkdir()) {
                File file = new File(folder, fileName);
                fos = new FileOutputStream(file);
                //写入文件
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                path = file.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            try {
                if (fos != null) {
                    //关闭流
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
        //返回路径
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
            Log.i("PERMISSION","请求权限成功");
        }
    }

}

class Permission {
    public static final int REQUEST_CODE = 5;
    //定义三个权限
    private static final String[] permission = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };
    //每个权限是否已授
    public static boolean isPermissionGranted(Activity activity){
        if(Build.VERSION.SDK_INT >= 23){
            for(int i = 0; i < permission.length;i++) {
                int checkPermission = ContextCompat.checkSelfPermission(activity,permission[i]);
                /***
                 * checkPermission返回两个值
                 * 有权限: PackageManager.PERMISSION_GRANTED
                 * 无权限: PackageManager.PERMISSION_DENIED
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
            //如果没有设置过权限许可，则弹出系统的授权窗口
            ActivityCompat.requestPermissions(activity,permission,REQUEST_CODE);
            return false;
        }
    }
}