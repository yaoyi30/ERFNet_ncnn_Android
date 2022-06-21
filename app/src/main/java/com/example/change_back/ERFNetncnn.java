package com.example.change_back;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class ERFNetncnn {
    public native boolean Init(AssetManager mgr);

    public native void Process(Bitmap input, Bitmap output, int color, boolean use_gpu);

    static {
        System.loadLibrary("ERFNetncnn");
    }

}
