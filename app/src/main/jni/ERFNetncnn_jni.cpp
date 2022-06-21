// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2020 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "res.id.h"
#include <jni.h>

#include <string>
#include <vector>

// ncnn
#include "layer.h"
#include "net.h"
#include "benchmark.h"
#include <jni.h>
#include <jni.h>

static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;

static ncnn::Net personSeg;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "MobileUnetncnn", "JNI_OnLoad");

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "MobileUnetncnn", "JNI_OnUnload");

    ncnn::destroy_gpu_instance();
}

// public native boolean Init(AssetManager mgr);
JNIEXPORT jboolean JNICALL Java_com_tencent_MobileUnet_MobileUnetncnn_Init(JNIEnv* env, jobject thiz, jobject assetManager)
{
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;
    opt.use_packing_layout = true;

    // use vulkan compute
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    personSeg.opt = opt;

    // init param
    {
        int ret = personSeg.load_param_bin(mgr, "res.param.bin");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3plusNcnn", "load_param failed");
            return JNI_FALSE;
        }
    }

    // init bin
    {
        int ret = personSeg.load_model(mgr, "res.bin");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3Ncnn", "load_model failed");
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}

// public native Obj[] Detect(Bitmap bitmap, boolean use_gpu);
JNIEXPORT void JNICALL Java_com_tencent_MobileUnet_MobileUnetncnn_Detect(JNIEnv* env, jobject thiz, jobject bitmap, jobject output, jboolean use_gpu)
{
    if (use_gpu == JNI_TRUE && ncnn::get_gpu_count() == 0)
    {
        return ;
        //return env->NewStringUTF("no vulkan capable gpu");
    }

    double start_time = ncnn::get_current_time();

    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return ;

    // ncnn from bitmap
//    const int target_size = 448;

    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_RGB,600,800);
    ncnn::Mat image = in.clone();

    // deeplabv3plus
    {
//        const float mean_vals[3] = {0.45734706f * 255.f, 0.43338275f * 255.f, 0.40058118f*255.f};
//        const float norm_vals[3] = {1/0.23965294/255.f, 1/0.23532275/255.f, 1/0.2398498/255.f};
        const float mean_vals[3] = {0, 0, 0};
        const float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
        in.substract_mean_normalize(mean_vals, norm_vals);

        ncnn::Extractor ex = personSeg.create_extractor();

        ex.set_vulkan_compute(use_gpu);

//        ex.input("input", in);
//        ncnn::Mat output;
//        ex.extract("output", output);

        ex.input(res_param_id::LAYER_input,in);
        ncnn::Mat output;
        ex.extract(res_param_id::BLOB_output,output);

//        int w = out.w;
//        int h = out.h;
//        for (int row = 0; row < h; row++) {
//            for (int col = 0; col < w; col++) {
//                cv::Vec3b bgr = image.at<Vec3b>(row, col);
//                bgr[0] = 255 - bgr[0];
//                bgr[1] = 255 - bgr[1];
//                bgr[2] = 255 - bgr[2];
//                image.at<Vec3b>(row, col) = bgr;
//            }
//        }



        const float* pCls0 = output.channel(0);

        for (int c = 0; c < 3; c++)
        {
            float* pImage = image.channel(c);
            for (int i = 0; i < 600*800; i++){
                pImage[i] = pCls0[i] > 0.6?pImage[i]*1:pImage[i]*0;
            }
        }
    }

    image.to_android_bitmap(env, output, ncnn::Mat::PIXEL_RGB2RGBA);


    double elasped = ncnn::get_current_time() - start_time;
    __android_log_print(ANDROID_LOG_DEBUG, "MobileUnetNcnn", "%.2fms   detect", elasped);

}

}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_change_1back_ERFNetncnn_Init(JNIEnv *env, jobject thiz, jobject assetManager) {
    // TODO: implement Init()
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;
    opt.use_packing_layout = true;

    // use vulkan compute
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    personSeg.opt = opt;

    // init param
    {
        int ret = personSeg.load_param_bin(mgr, "res.param.bin");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3plusNcnn", "load_param failed");
            return JNI_FALSE;
        }
    }

    // init bin
    {
        int ret = personSeg.load_model(mgr, "res.bin");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3Ncnn", "load_model failed");
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_change_1back_ERFNetncnn_Process(JNIEnv *env, jobject thiz, jobject input_img,
                                                 jobject output_img, jint color,
                                                 jboolean use_gpu) {
    // TODO: implement Process()
    if (use_gpu == JNI_TRUE && ncnn::get_gpu_count() == 0)
    {
        return ;
    }

    double start_time = ncnn::get_current_time();

    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, input_img, &info);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return ;

    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, input_img, ncnn::Mat::PIXEL_RGB,600,800);
    ncnn::Mat image = in.clone();

    {

        const float mean_vals[3] = {0, 0, 0};
        const float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
        in.substract_mean_normalize(mean_vals, norm_vals);

        ncnn::Extractor ex = personSeg.create_extractor();

        ex.set_vulkan_compute(use_gpu);


        ex.input(res_param_id::LAYER_input,in);
        ncnn::Mat output;
        ex.extract(res_param_id::BLOB_output,output);


        const float* pCls0 = output.channel(0);

        if (color == 0)
        {
            for (int c = 0; c < 3; c++)
            {
                float* pImage = image.channel(c);
                for (int i = 0; i < 600*800; i++){
                    pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0;
                }
            }
        }

        if (color == 1)
        {
            for (int c = 0; c < 3; c++)
            {
                if (c==0){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+255;
                    }
                }
                if (c==1){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0;
                    }
                }
                if (c==2){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0;
                    }
                }

            }
        }

        if (color == 2)
        {
            for (int c = 0; c < 3; c++)
            {
                if (c==0){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+255;
                    }
                }
                if (c==1){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+255;
                    }
                }
                if (c==2){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+255;
                    }
                }

            }
        }

        if (color == 3)
        {
            for (int c = 0; c < 3; c++)
            {
                if (c==0){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+67;
                    }
                }
                if (c==1){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+142;
                    }
                }
                if (c==2){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+219;
                    }
                }

            }
        }

        if (color == 4)
        {
            for (int c = 0; c < 3; c++)
            {
                if (c==0){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+10;
                    }
                }
                if (c==1){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+154;
                    }
                }
                if (c==2){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+56;
                    }
                }

            }
        }

        if (color == 5)
        {
            for (int c = 0; c < 3; c++)
            {
                if (c==0){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+255;
                    }
                }
                if (c==1){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+255;
                    }
                }
                if (c==2){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0;
                    }
                }

            }
        }

        if (color == 6)
        {
            for (int c = 0; c < 3; c++)
            {
                if (c==0){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+128;
                    }
                }
                if (c==1){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0;
                    }
                }
                if (c==2){
                    float* pImage = image.channel(c);
                    for (int i = 0; i < 600*800; i++){
                        pImage[i] = pCls0[i] > 0.8?pImage[i]*1:pImage[i]*0+128;
                    }
                }

            }
        }




    }

    image.to_android_bitmap(env, output_img, ncnn::Mat::PIXEL_RGB2RGBA);


    double elasped = ncnn::get_current_time() - start_time;
    __android_log_print(ANDROID_LOG_DEBUG, "MobileUnetNcnn", "%.2fms   detect", elasped);
}