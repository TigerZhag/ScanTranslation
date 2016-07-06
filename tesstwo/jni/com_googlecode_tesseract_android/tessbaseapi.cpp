/*
 * Copyright 2011, Google Inc.
 * Copyright 2011, Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string>
#include <stdio.h>
#include <malloc.h>
//#include "android/bitmap.h"
#include "common.h"
#include "baseapi.h"
#include "allheaders.h"
#include <android/bitmap.h>

static jfieldID field_mNativeData;

struct native_data_t {
  tesseract::TessBaseAPI api;
  PIX *pix;
  void *data;
  bool debug;

  native_data_t() {
    pix = NULL;
    data = NULL;
    debug = false;
  }
};

static inline native_data_t * get_native_data(JNIEnv *env, jobject object) {
  return (native_data_t *) (env->GetIntField(object, field_mNativeData));
}

#ifdef __cplusplus
extern "C" {
#endif

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv *env;

  if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
    LOGE("Failed to get the environment using GetEnv()");
    return -1;
  }

  return JNI_VERSION_1_6;
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeClassInit(JNIEnv* env, 
                                                                       jclass clazz) {

  field_mNativeData = env->GetFieldID(clazz, "mNativeData", "I");
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeConstruct(JNIEnv* env,
                                                                       jobject object) {

  native_data_t *nat = new native_data_t;

  if (nat == NULL) {
    LOGE("%s: out of memory!", __FUNCTION__);
    return;
  }

  env->SetIntField(object, field_mNativeData, (jint) nat);
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeFinalize(JNIEnv* env,
                                                                      jobject object) {

  native_data_t *nat = get_native_data(env, object);

  // Since Tesseract doesn't take ownership of the memory, we keep a pointer in the native
  // code struct. We need to free that pointer when we release our instance of Tesseract or
  // attempt to set a new image using one of the nativeSet* methods.
  if (nat->data != NULL)
    free(nat->data);
  else if (nat->pix != NULL)
    pixDestroy(&nat->pix);
  nat->data = NULL;
  nat->pix = NULL;

  if (nat != NULL)
    delete nat;
}

jboolean Java_com_googlecode_tesseract_android_TessBaseAPI_nativeInit(JNIEnv *env,
                                                                      jobject thiz,
                                                                      jstring dir,
                                                                      jstring lang,
																	  jstring suffix) {
  native_data_t *nat = get_native_data(env, thiz);

  const char *c_dir = env->GetStringUTFChars(dir, NULL);
  const char *c_lang = env->GetStringUTFChars(lang, NULL);
  const char *c_suffix = env->GetStringUTFChars(suffix, NULL);

  jboolean res = JNI_TRUE;

  if (nat->api.Init(c_dir, c_lang,c_suffix)) {
    LOGE("Could not initialize Tesseract API with language=%s c_suffix=%s!", c_lang,c_suffix);
    res = JNI_FALSE;
  } else {
    LOGI("Initialized Tesseract API with language=%s", c_lang);
  }

  env->ReleaseStringUTFChars(dir, c_dir);
  env->ReleaseStringUTFChars(lang, c_lang);
  env->ReleaseStringUTFChars(suffix, c_suffix);

  return res;
}

jboolean Java_com_googlecode_tesseract_android_TessBaseAPI_nativeInitOem(JNIEnv *env, 
                                                                         jobject thiz,
                                                                         jstring dir, 
                                                                         jstring lang,
																	     jstring suffix, 
                                                                         jint mode) {

  native_data_t *nat = get_native_data(env, thiz);

  const char *c_dir = env->GetStringUTFChars(dir, NULL);
  const char *c_lang = env->GetStringUTFChars(lang, NULL);
  const char *c_suffix = env->GetStringUTFChars(suffix, NULL);

  jboolean res = JNI_TRUE;

  if (nat->api.Init(c_dir, c_lang,c_suffix, (tesseract::OcrEngineMode) mode)) {
    LOGE("Could not initialize Tesseract API with language=%s!", c_lang);
    res = JNI_FALSE;
  } else {
    LOGI("Initialized Tesseract API with language=%s", c_lang);
  }

  env->ReleaseStringUTFChars(dir, c_dir);
  env->ReleaseStringUTFChars(lang, c_lang);
  env->ReleaseStringUTFChars(suffix, c_suffix);

  return res;
}

jstring Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetInitLanguagesAsString(JNIEnv *env,
                                                                                         jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);

  const char *text = nat->api.GetInitLanguagesAsString();

  jstring result = env->NewStringUTF(text);

  return result;
}


void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetImageBytes(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jbyteArray data,
                                                                           jint width,
                                                                           jint height,
                                                                           jint bpp,
                                                                           jint bpl) {
  LOGI("IN nativeSetImageBytes");

  jbyte *data_array = env->GetByteArrayElements(data, NULL);
  int count = env->GetArrayLength(data);
  unsigned char* imagedata = (unsigned char *) malloc(count * sizeof(unsigned char));

  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  for (int i = 0; i < count; i++) {
    imagedata[i] = (unsigned char) data_array[i];
  }

  env->ReleaseByteArrayElements(data, data_array, JNI_ABORT);

  native_data_t *nat = get_native_data(env, thiz);
  nat->api.SetImage(imagedata, (int) width, (int) height, (int) bpp, (int) bpl);

  // Since Tesseract doesn't take ownership of the memory, we keep a pointer in the native
  // code struct. We need to free that pointer when we release our instance of Tesseract or
  // attempt to set a new image using one of the nativeSet* methods.
  if (nat->data != NULL)
    free(nat->data);
  else if (nat->pix != NULL)
    pixDestroy(&nat->pix);
  nat->data = imagedata;
  nat->pix = NULL;
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetImagePix(JNIEnv *env,
                                                                         jobject thiz,
                                                                         jint nativePix) {
  LOGI("IN nativeSetImagePix");
  
  if((PIX *)nativePix==NULL)
  {
	  LOGI("IN nativeSetImagePix nativePix==null");
  }

  PIX *pixs = (PIX *) nativePix;
  PIX *pixd = pixClone(pixs);

  native_data_t *nat = get_native_data(env, thiz);
  nat->api.SetImage(pixd);

  // Since Tesseract doesn't take ownership of the memory, we keep a pointer in the native
  // code struct. We need to free that pointer when we release our instance of Tesseract or
  // attempt to set a new image using one of the nativeSet* methods.
  if (nat->data != NULL)
    free(nat->data);
  else if (nat->pix != NULL)
    pixDestroy(&nat->pix);
  nat->data = NULL;
  nat->pix = pixd;
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetImageForFindLines(JNIEnv *env,
																				  jobject thiz,
																				  jint nativePix) {
  LOGI("IN nativeSetImageForFindLines");
  if((PIX *)nativePix==NULL)
  {
	  LOGI("IN nativeSetImageForFindLines nativePix==null");
  }

  PIX *pixs = (PIX *) nativePix;
  PIX *pixd = pixClone(pixs);

  native_data_t *nat = get_native_data(env, thiz);
  nat->api.SetImageForFindLines(pixd);

  // Since Tesseract doesn't take ownership of the memory, we keep a pointer in the native
  // code struct. We need to free that pointer when we release our instance of Tesseract or
  // attempt to set a new image using one of the nativeSet* methods.
  if (nat->data != NULL)
    free(nat->data);
  else if (nat->pix != NULL)
    pixDestroy(&nat->pix);
  nat->data = NULL;
  nat->pix = pixd;
}

jintArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetImageForFindLeft(JNIEnv *env,
																					 jobject thiz,
																					 jobject bmpObj,
																					 jint    RGBDisThreshold)
{
			
  clock_t start_time;
  start_time=clock();

  jintArray ret = env->NewIntArray(4);
  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
  
  int lpiFindLinesResult[4];
  
  lpiFindLinesResult[0]=0;
  lpiFindLinesResult[1]=0;
  lpiFindLinesResult[2]=0;
  lpiFindLinesResult[3]=0;
  
  AndroidBitmapInfo bmpInfo={0};
  if(AndroidBitmap_getInfo(env,bmpObj,&bmpInfo)<0)
  {
	  env->SetIntArrayRegion(ret, 0, 4, lpiFindLinesResult);
	  return ret;
  }
  unsigned char* dataFromBmp=NULL;
  if(AndroidBitmap_lockPixels(env,bmpObj,(void**)&dataFromBmp))
  {
	  env->SetIntArrayRegion(ret, 0, 4, lpiFindLinesResult);
	  return ret;
  }
  
  int bpp=0;
  native_data_t *nat = get_native_data(env, thiz);
  
  LOGD("width=%d",bmpInfo.width);
  LOGD("height=%d",bmpInfo.height);

  if(bmpInfo.format==1)
  {
	  bpp=4;
	  LOGD("bpp=%d",bpp);
	  
	  nat->api.moVectorAutoRectResult.clear();
	  nat->api.SetImageForFindLeft(dataFromBmp,bmpInfo.width,bmpInfo.height,bpp,(int)RGBDisThreshold);
	  if(nat->api.moVectorAutoRectResult.size()>0)
	  {
		  lpiFindLinesResult[0]=nat->api.moVectorAutoRectResult.begin()->LineDividingLeft;
		  lpiFindLinesResult[1]=nat->api.moVectorAutoRectResult.begin()->LineDividingRight;
		  lpiFindLinesResult[2]=nat->api.moVectorAutoRectResult.begin()->LineDividingTop;
		  lpiFindLinesResult[3]=nat->api.moVectorAutoRectResult.begin()->LineDividingBottom;
	  }else
	  {
		  lpiFindLinesResult[0]=0;
		  lpiFindLinesResult[1]=0;
		  lpiFindLinesResult[2]=0;
		  lpiFindLinesResult[3]=0;
		  LOGE("moVectorAutoRectResult IS NULL!!");
	  }
	  LOGD("lpiFindLinesResult[Left]=%d",lpiFindLinesResult[0]);
	  LOGD("lpiFindLinesResult[Right]=%d",lpiFindLinesResult[1]);
	  LOGD("lpiFindLinesResult[Top]=%d",lpiFindLinesResult[2]);
	  LOGD("lpiFindLinesResult[Bottom]=%d",lpiFindLinesResult[3]);
	  
	  LOGD("(TesseractTime)SetImageForFindLeft take: %lf ms",static_cast<double>(clock()-start_time)/CLOCKS_PER_SEC*1000);
  
  }else
  {
	  LOGE("img is not ANDROID_BITMAP_FORMAT_RGBA_8888 ");
  }


  AndroidBitmap_unlockPixels(env,bmpObj);
  
  env->SetIntArrayRegion(ret, 0, 4, lpiFindLinesResult);
  return ret;
}



int Java_com_googlecode_tesseract_android_TessBaseAPI_nativeThreshold(JNIEnv *env,
																		jobject thiz,
																		jbyteArray Data,
																		jint width,
																		jint height,
																		jint bpp,
																		jstring filename) {
  jbyte *Data_array = env->GetByteArrayElements(Data, NULL);
  int Count = env->GetArrayLength(Data);
  unsigned char* SrcImageData = (unsigned char *) malloc(Count * sizeof(unsigned char));
  
  const char *c_dir = env->GetStringUTFChars(filename, NULL);
  
  LOGD("filename=%s",c_dir);
  LOGD("bpp=%d",bpp);
  LOGD("Count=%d",Count);
  LOGD("width=%d",width);
  LOGD("height=%d",height);
  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  memcpy(SrcImageData,Data_array,Count * sizeof(unsigned char));

  env->ReleaseByteArrayElements(Data, Data_array, JNI_ABORT);
  
  native_data_t *nat = get_native_data(env, thiz);
  
  nat->api.Threshold(SrcImageData,(int)width,(int)height,(int)bpp,c_dir);
  
  delete[] SrcImageData;
  
  env->ReleaseStringUTFChars(filename,c_dir);

  return 0;
}

int Java_com_googlecode_tesseract_android_TessBaseAPI_nativeAdaptiveThreshold(JNIEnv *env,
																			jobject thiz,
																			jobject bmpObj,
																			jstring filename,
																			jint    srcheight) {

  AndroidBitmapInfo bmpInfo={0};
  if(AndroidBitmap_getInfo(env,bmpObj,&bmpInfo)<0)
  {
	  return -1;
  }
  //LOGE("bmpInfo.width=%d,bmpInfo.height=%d,bmpInfo.format=%d",bmpInfo.width,bmpInfo.height,bmpInfo.format);
  unsigned char* dataFromBmp=NULL;
  if(AndroidBitmap_lockPixels(env,bmpObj,(void**)&dataFromBmp))
  {
	  return -1;
  }
  
  const char *c_dir = env->GetStringUTFChars(filename, NULL);
  int bpp=0;
  native_data_t *nat = get_native_data(env, thiz);
  if(bmpInfo.format==1)
  {
	  bpp=4;
	 tesseract::TessBaseAPI::AdaptiveThreshold(dataFromBmp,bmpInfo.width,bmpInfo.height,bpp,c_dir,srcheight);
  
  }else
  {
	  LOGE("img is not ANDROID_BITMAP_FORMAT_RGBA_8888 ");
  }

  LOGD("filename=%s",c_dir);
  LOGD("bpp=%d",bpp);
  LOGD("width=%d",bmpInfo.width);
  LOGD("height=%d",bmpInfo.height);

  env->ReleaseStringUTFChars(filename,c_dir);

  AndroidBitmap_unlockPixels(env,bmpObj);

  return 0;

}

jbyteArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetAdaptiveThresholdImg(JNIEnv *env,
																								jobject thiz,
																								jbyteArray Data,
																								jint width,
																								jint height,
																								jint bpp) {

  
  jbyte *Data_array = env->GetByteArrayElements(Data, NULL);
  int Count = env->GetArrayLength(Data);
  unsigned char* SrcImageData = (unsigned char *) malloc(Count * sizeof(unsigned char));
  
  LOGD("bpp=%d",bpp);
  LOGD("Count=%d",Count);
  LOGD("width=%d",width);
  LOGD("height=%d",height);
  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  memcpy(SrcImageData,Data_array,Count * sizeof(unsigned char));

  env->ReleaseByteArrayElements(Data, Data_array, JNI_ABORT);
  
  native_data_t *nat = get_native_data(env, thiz);
  
  int     liWidthStep=0;
  unsigned char* TarImageData=nat->api.GetAdaptiveThresholdImg(SrcImageData,(int)width,(int)height,(int)bpp,liWidthStep);
  unsigned char* TarImageDataFinal = (unsigned char *) malloc(Count * sizeof(unsigned char)/bpp);
  
  for(int i=0;i<height;i++)
  {
	  memcpy(TarImageDataFinal+i*width,TarImageData+liWidthStep*i,width);
  }

  jbyteArray ret = env->NewByteArray(Count * sizeof(unsigned char)/bpp);

  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
  
  env->SetByteArrayRegion(ret, 0, Count * sizeof(unsigned char)/bpp, (jbyte*)TarImageDataFinal);
  
  delete[] SrcImageData;
  delete[] TarImageDataFinal;

  return ret;
}


jbyteArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetAdaptiveThresholdByte(JNIEnv *env,
																								jobject thiz,
																								jbyteArray Data,
																								jint width,
																								jint height,
																								jint bpp) {

  
  jbyte *Data_array = env->GetByteArrayElements(Data, NULL);
  int Count = env->GetArrayLength(Data);
  unsigned char* SrcImageData = (unsigned char *) malloc(Count * sizeof(unsigned char));
  
  LOGD("bpp=%d",bpp);
  LOGD("Count=%d",Count);
  LOGD("width=%d",width);
  LOGD("height=%d",height);
  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  memcpy(SrcImageData,Data_array,Count * sizeof(unsigned char));

  env->ReleaseByteArrayElements(Data, Data_array, JNI_ABORT);
  
  native_data_t *nat = get_native_data(env, thiz);
  
  int     liImgSize=0;
  unsigned char* TarImageData=nat->api.GetAdaptiveThresholdByte(SrcImageData,(int)width,(int)height,(int)bpp,liImgSize);

  jbyteArray ret = env->NewByteArray(liImgSize);

  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
  
  env->SetByteArrayRegion(ret, 0, liImgSize, (jbyte*)TarImageData);
  
  delete[] SrcImageData;

  return ret;
}


int Java_com_googlecode_tesseract_android_TessBaseAPI_nativeNeedToFocus(JNIEnv *env,
																		jobject thiz,
																		jbyteArray Data,
																		jint width,
																		jint height,
																		jint bpp) {

  jbyte *Data_array = env->GetByteArrayElements(Data, NULL);
  int Count = env->GetArrayLength(Data);
  unsigned char* SrcImageData = (unsigned char *) malloc(Count * sizeof(unsigned char));
  
  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  memcpy(SrcImageData,Data_array,Count * sizeof(unsigned char));
  
  env->ReleaseByteArrayElements(Data, Data_array, JNI_ABORT);
  
  native_data_t *nat = get_native_data(env, thiz);
 
  bool lbneedtofocus=nat->api.NeedToFocus(SrcImageData,(int)width,(int)height,(int)bpp);
  
  delete[] SrcImageData;
  return lbneedtofocus?1:0;
}


void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetRectangle(JNIEnv *env,
                                                                          jobject thiz,
                                                                          jint left,
                                                                          jint top,
                                                                          jint width,
                                                                          jint height) {

  native_data_t *nat = get_native_data(env, thiz);

  nat->api.SetRectangle(left, top, width, height);
}

jstring Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetUTF8TextWith(JNIEnv *env,
                                                                                jobject thiz,
																				jint language) {

  native_data_t *nat = get_native_data(env, thiz);

  char *text = nat->api.GetUTF8TextWith((int)language);
 
  jstring result = env->NewStringUTF(text);

  delete[] text;

  return result;
}

jstring Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetUTF8Text(JNIEnv *env,
                                                                            jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);
  nat->api.SetLanguage(0);
  char *text = nat->api.GetUTF8Text();
 
  jstring result = env->NewStringUTF(text);
  
  delete[] text;

  return result;
}

jboolean Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetISLangCorre(JNIEnv *env,
                                                                               jobject thiz) {
  native_data_t *nat = get_native_data(env, thiz);
  return nat->api.GetISLangCorre();
}

jbyteArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetAreaInImage(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jbyteArray SrcData,
                                                                           jbyteArray TarData,
                                                                           jint width,
                                                                           jint height,
                                                                           jint bpp) {
  jbyte *SrcData_array = env->GetByteArrayElements(SrcData, NULL);
  int SrcCount = env->GetArrayLength(SrcData);
  unsigned char* SrcImageData = (unsigned char *) malloc(SrcCount * sizeof(unsigned char));
  
  jbyte *TarData_array = env->GetByteArrayElements(TarData, NULL);
  int TarCount = env->GetArrayLength(TarData);
  unsigned char* TarImageData = (unsigned char *) malloc(TarCount * sizeof(unsigned char));
 
  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  for (int i = 0; i < SrcCount; i++) {
    SrcImageData[i] = (unsigned char) SrcData_array[i];
  }
  
  for (int i = 0; i < TarCount; i++) {
    TarImageData[i] = (unsigned char) TarData_array[i];
  }
  
  env->ReleaseByteArrayElements(SrcData, SrcData_array, JNI_ABORT);
  env->ReleaseByteArrayElements(TarData, TarData_array, JNI_ABORT);

  native_data_t *nat = get_native_data(env, thiz);

  int CountPixChange=nat->api.GetAreaInImage(SrcImageData,TarImageData,(int)width,(int)height,(int)bpp);

  if(!CountPixChange)
  {
	  jbyteArray ret = env->NewByteArray(1);
	  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
	  delete[] TarImageData;
	  return ret;
  }
  
  jbyteArray ret = env->NewByteArray(TarCount);

  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
  
  env->SetByteArrayRegion(ret, 0, TarCount, (jbyte*)TarImageData);
  
  delete[] TarImageData;
  delete[] SrcImageData;
  return ret;

}


jbyteArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetBlockInApply(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jbyteArray SrcData,
                                                                           jbyteArray TarData,
                                                                           jint width,
                                                                           jint height,
                                                                           jint bpp) {
  jbyte *SrcData_array = env->GetByteArrayElements(SrcData, NULL);
  int SrcCount = env->GetArrayLength(SrcData);
  unsigned char* SrcImageData = (unsigned char *) malloc(SrcCount * sizeof(unsigned char));
  
  jbyte *TarData_array = env->GetByteArrayElements(TarData, NULL);
  int TarCount = env->GetArrayLength(TarData);
  unsigned char* TarImageData = (unsigned char *) malloc(TarCount * sizeof(unsigned char));
 
  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  for (int i = 0; i < SrcCount; i++) {
    SrcImageData[i] = (unsigned char) SrcData_array[i];
  }
  
  for (int i = 0; i < TarCount; i++) {
    TarImageData[i] = (unsigned char) TarData_array[i];
  }
  
  env->ReleaseByteArrayElements(SrcData, SrcData_array, JNI_ABORT);
  env->ReleaseByteArrayElements(TarData, TarData_array, JNI_ABORT);

  native_data_t *nat = get_native_data(env, thiz);

  int CountPixChange=nat->api.GetBlockInApply(SrcImageData,TarImageData,(int)width,(int)height,(int)bpp);

  jbyteArray ret = env->NewByteArray(TarCount);

  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
  
  env->SetByteArrayRegion(ret, 0, TarCount, (jbyte*)TarImageData);
  
  delete[] TarImageData;
  delete[] SrcImageData;
  return ret;

}



jbyteArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetImageWithBlocks(JNIEnv *env,
																					   jobject thiz,
																					   jbyteArray Data,
																					   jint width,
																					   jint height,
																					   jint bpp) {
  jbyte *Data_array = env->GetByteArrayElements(Data, NULL);
  int Count = env->GetArrayLength(Data);
  unsigned char* ImageData = (unsigned char *) malloc(Count * sizeof(unsigned char));
  
  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  for (int i = 0; i < Count; i++) {
    ImageData[i] = (unsigned char) Data_array[i];
  }
  
  env->ReleaseByteArrayElements(Data, Data_array, JNI_ABORT);
  
  native_data_t *nat = get_native_data(env, thiz);
  
  nat->api.GetImageWithBlocks(ImageData,(int)width,(int)height,(int)bpp);
  env->ReleaseByteArrayElements(Data, Data_array, JNI_ABORT);
  jbyteArray ret = env->NewByteArray(Count);
  
  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
  
  env->SetByteArrayRegion(ret, 0, Count, (jbyte*)ImageData);
  
  delete[] ImageData;
  
  return ret;
}

jbyteArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeTidy(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jbyteArray TarData,
                                                                           jint width,
                                                                           jint height,
                                                                           jint bpp) {
  jbyte *TarData_array = env->GetByteArrayElements(TarData, NULL);
  int TarCount = env->GetArrayLength(TarData);
  unsigned char* TarImageData = (unsigned char *) malloc(TarCount * sizeof(unsigned char));
 
  for (int i = 0; i < TarCount; i++) {
    TarImageData[i] = (unsigned char) TarData_array[i];
  }
  
  native_data_t *nat = get_native_data(env, thiz);
  nat->api.Tidy(TarImageData,(int)width,(int)height,(int)bpp);
  
  env->ReleaseByteArrayElements(TarData, TarData_array, JNI_ABORT);
  jbyteArray ret = env->NewByteArray(TarCount);
  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
  env->SetByteArrayRegion(ret, 0, TarCount, (jbyte*)TarImageData);
  delete[] TarImageData;
  return ret;

}


jbyteArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeRemoveNoisePix(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jbyteArray SrcData,
                                                                           jbyteArray TarData,
                                                                           jint width,
                                                                           jint height,
                                                                           jint bpp) {
  jbyte *SrcData_array = env->GetByteArrayElements(SrcData, NULL);
  int SrcCount = env->GetArrayLength(SrcData);
  unsigned char* SrcImageData = (unsigned char *) malloc(SrcCount * sizeof(unsigned char));
  
  jbyte *TarData_array = env->GetByteArrayElements(TarData, NULL);
  int TarCount = env->GetArrayLength(TarData);
  unsigned char* TarImageData = (unsigned char *) malloc(TarCount * sizeof(unsigned char));
 
  unsigned char* ReturnImageData = (unsigned char *) malloc(TarCount * sizeof(unsigned char));
 
  // This is painfully slow, but necessary because we don't know
  // how many bits the JVM might be using to represent a byte
  for (int i = 0; i < SrcCount; i++) {
    SrcImageData[i] = (unsigned char) SrcData_array[i];
  }
  
  for (int i = 0; i < TarCount; i++) {
    TarImageData[i] = (unsigned char) TarData_array[i];
  }
  
  for (int i = 0; i < TarCount; i++) {
    ReturnImageData[i] = (unsigned char) 255;
  }
  
  
  env->ReleaseByteArrayElements(SrcData, SrcData_array, JNI_ABORT);
  env->ReleaseByteArrayElements(TarData, TarData_array, JNI_ABORT);

  native_data_t *nat = get_native_data(env, thiz);

  if(!nat->api.GetAreaState(SrcImageData,width,height,bpp))
  {//no area
	  jbyteArray ret = env->NewByteArray(2);
	  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
	  delete[] TarImageData;
      delete[] SrcImageData;
      delete[] ReturnImageData;
	  return ret;
  }

  /*
  int liLeavePixCount=nat->api.RemoveNoisePix(SrcImageData,TarImageData,(int)width,(int)height,(int)bpp);
  if(liLeavePixCount<=3)
  {
	  for (int i = 0; i < TarCount; i++) {
		TarImageData[i] = (unsigned char) TarData_array[i];
	  }
	  LOGE("RemoveNoisePix Fail,Retry with GetAreaInImage!");
	  liLeavePixCount=nat->api.GetAreaInImage(SrcImageData,TarImageData,(int)width,(int)height,(int)bpp);
  }
  */

  if(!nat->api.RemoveNoisePix(SrcImageData,TarImageData,ReturnImageData,(int)width,(int)height,(int)bpp))
  {
	  jbyteArray ret = env->NewByteArray(1);
	  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
	  delete[] TarImageData;
      delete[] SrcImageData;
      delete[] ReturnImageData;
	  return ret;
  }

  jbyteArray ret = env->NewByteArray(TarCount);
  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");
  env->SetByteArrayRegion(ret, 0, TarCount, (jbyte*)ReturnImageData);
  delete[] ReturnImageData;
  delete[] TarImageData;
  delete[] SrcImageData;
  return ret;

}


jint Java_com_googlecode_tesseract_android_TessBaseAPI_nativeIfPhotoTooSmall(JNIEnv *env,
																			 jobject thiz,
																			 jint BlobHigMin,
																			 jint BlobHigMax) {
  native_data_t *nat = get_native_data(env, thiz);
  
  return (nat->api.IfPhotoTooSmall((int)BlobHigMin,(int)BlobHigMax,0));
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeStart(JNIEnv *env, 
                                                                  jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);
  
  nat->api.Start();
}


void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeStop(JNIEnv *env, 
                                                                  jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);
  
  nat->api.Stop();
}



void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeInitBreak(JNIEnv *env, 
                                                                       jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);
  
  nat->api.InitBreak();
}

jint Java_com_googlecode_tesseract_android_TessBaseAPI_nativeMeanConfidence(JNIEnv *env,
                                                                            jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);

  return (jint) nat->api.MeanTextConf();
}

jintArray Java_com_googlecode_tesseract_android_TessBaseAPI_nativeWordConfidences(JNIEnv *env,
                                                                                  jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);

  int *confs = nat->api.AllWordConfidences();

  if (confs == NULL) {
    LOGE("Could not get word-confidence values!");
    return NULL;
  }

  int len, *trav;
  for (len = 0, trav = confs; *trav != -1; trav++, len++)
    ;

  LOG_ASSERT((confs != NULL), "Confidence array has %d elements", len);

  jintArray ret = env->NewIntArray(len);

  LOG_ASSERT((ret != NULL), "Could not create Java confidence array!");

  env->SetIntArrayRegion(ret, 0, len, confs);

  delete[] confs;

  return ret;
}

jboolean Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetVariable(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jstring var,
                                                                             jstring value) {

  native_data_t *nat = get_native_data(env, thiz);

  const char *c_var = env->GetStringUTFChars(var, NULL);
  const char *c_value = env->GetStringUTFChars(value, NULL);

  jboolean set = nat->api.SetVariable(c_var, c_value) ? JNI_TRUE : JNI_FALSE;

  env->ReleaseStringUTFChars(var, c_var);
  env->ReleaseStringUTFChars(value, c_value);

  return set;
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeClear(JNIEnv *env,
                                                                   jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);

  nat->api.Clear();

  // Call between pages or documents etc to free up memory and forget adaptive data.
  nat->api.ClearAdaptiveClassifier();

  // Since Tesseract doesn't take ownership of the memory, we keep a pointer in the native
  // code struct. We need to free that pointer when we release our instance of Tesseract or
  // attempt to set a new image using one of the nativeSet* methods.
  if (nat->data != NULL)
    free(nat->data);
  else if (nat->pix != NULL)
    pixDestroy(&nat->pix);
  nat->data = NULL;
  nat->pix = NULL;
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeEnd(JNIEnv *env,
                                                                 jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);

  //nat->api.End();

  // Since Tesseract doesn't take ownership of the memory, we keep a pointer in the native
  // code struct. We need to free that pointer when we release our instance of Tesseract or
  // attempt to set a new image using one of the nativeSet* methods.
  if (nat->data != NULL)
    free(nat->data);
  else if (nat->pix != NULL)
    pixDestroy(&nat->pix);
  nat->data = NULL;
  nat->pix = NULL;
  
  if (nat != NULL)
    delete nat;
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetDebug(JNIEnv *env,
                                                                      jobject thiz,
                                                                      jboolean debug) {

    native_data_t *nat = get_native_data(env, thiz);
    
    nat->debug = (debug == JNI_TRUE) ? TRUE : FALSE;
    string BoolDebug;
    if(debug == JNI_TRUE)
    {
      BoolDebug =  "true";
	  nat->api.baseapi_debug=true;
    }else{
      BoolDebug = "false";
	  nat->api.baseapi_debug=false;
    }

	nat->api.SetDebugVariable("use_cjk_fp_model_","true");
	nat->api.SetDebugVariable("tessedit_adaption_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("chop_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("tessedit_debug_quality_metrics",BoolDebug.c_str());
    nat->api.SetDebugVariable("tessedit_bigram_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("tessedit_timing_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("tessedit_debug_fonts",BoolDebug.c_str());
    nat->api.SetDebugVariable("tessedit_debug_doc_rejection",BoolDebug.c_str());
    nat->api.SetDebugVariable("tessedit_debug_block_rejection",BoolDebug.c_str());
    nat->api.SetDebugVariable("crunch_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("tessedit_rejection_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_tabfind",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_images",BoolDebug.c_str());
    nat->api.SetDebugVariable("wordrec_debug_blamer",BoolDebug.c_str());
    nat->api.SetDebugVariable("superscript_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("poly_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("print_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("classify_debug_character_fragments",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_pitch_test",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_printable",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_bugs",BoolDebug.c_str());
    nat->api.SetDebugVariable("devanagari_split_debuglevel",BoolDebug.c_str());
    nat->api.SetDebugVariable("devanagari_split_debugimage",BoolDebug.c_str());
    nat->api.SetDebugVariable("gapmap_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_xheights",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_oldbl_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("edges_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_blob",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_baselines",BoolDebug.c_str());
    nat->api.SetDebugVariable("display_if_debugging",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_debug_pitch_metric",BoolDebug.c_str());
    nat->api.SetDebugVariable("textord_noise_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("chop_debug",BoolDebug.c_str());
    nat->api.SetDebugVariable("applybox_debug",BoolDebug.c_str());
	
    BoolDebug =  "true";
	if(0)
	{
		nat->api.SetDebugVariable("tessedit_adaption_debug","true");
		nat->api.SetDebugVariable("chop_debug","true");
		nat->api.SetDebugVariable("tessedit_debug_quality_metrics","true");
		nat->api.SetDebugVariable("tessedit_bigram_debug","true");
		nat->api.SetDebugVariable("tessedit_timing_debug","true");
		nat->api.SetDebugVariable("tessedit_debug_fonts","true");
		nat->api.SetDebugVariable("tessedit_debug_doc_rejection","true");
		nat->api.SetDebugVariable("tessedit_debug_block_rejection","true");
		nat->api.SetDebugVariable("crunch_debug","true");
		nat->api.SetDebugVariable("tessedit_rejection_debug","true");
		nat->api.SetDebugVariable("textord_debug_tabfind","true");
		nat->api.SetDebugVariable("textord_debug_images","true");
		nat->api.SetDebugVariable("wordrec_debug_blamer","true");
		nat->api.SetDebugVariable("superscript_debug","true");
		//nat->api.SetDebugVariable("poly_debug","true");
		nat->api.SetDebugVariable("print_debug","true");
		nat->api.SetDebugVariable("classify_debug_character_fragments","true");
		nat->api.SetDebugVariable("textord_debug_pitch_test","true");
		nat->api.SetDebugVariable("textord_debug_printable","true");
		nat->api.SetDebugVariable("textord_debug_bugs","true");
		nat->api.SetDebugVariable("devanagari_split_debuglevel","true");
		nat->api.SetDebugVariable("devanagari_split_debugimage","true");
		nat->api.SetDebugVariable("gapmap_debug","true");
		nat->api.SetDebugVariable("textord_debug_xheights","true");
		//nat->api.SetDebugVariable("textord_oldbl_debug","true");
		nat->api.SetDebugVariable("edges_debug","true");
		nat->api.SetDebugVariable("textord_debug_blob","true");
		nat->api.SetDebugVariable("textord_debug_baselines","true");
		nat->api.SetDebugVariable("display_if_debugging","true");
		nat->api.SetDebugVariable("textord_debug_pitch_metric","true");
		nat->api.SetDebugVariable("textord_noise_debug","true");
		nat->api.SetDebugVariable("chop_debug","true");
		nat->api.SetDebugVariable("applybox_debug","true");
	}
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetPageSegMode(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jint mode) {

  native_data_t *nat = get_native_data(env, thiz);

  nat->api.SetPageSegMode((tesseract::PageSegMode) mode);
}

jint Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetRegions(JNIEnv *env,
                                                                        jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);;
  PIXA *pixa = NULL;
  BOXA *boxa;

  boxa = nat->api.GetRegions(&pixa);

  boxaDestroy(&boxa);

  return reinterpret_cast<jint>(pixa);
}

jint Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetTextlines(JNIEnv *env,
                                                                          jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);;
  PIXA *pixa = NULL;
  BOXA *boxa;

  boxa = nat->api.GetTextlines(&pixa, NULL);

  boxaDestroy(&boxa);

  return reinterpret_cast<jint>(pixa);
}

jint Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetStrips(JNIEnv *env,
                                                                       jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);;
  PIXA *pixa = NULL;
  BOXA *boxa;

  boxa = nat->api.GetStrips(&pixa, NULL);

  boxaDestroy(&boxa);

  return reinterpret_cast<jint>(pixa);
}

jint Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetWords(JNIEnv *env,
                                                                      jobject thiz) {

  native_data_t *nat = get_native_data(env, thiz);;
  PIXA *pixa = NULL;
  BOXA *boxa;

  boxa = nat->api.GetWords(&pixa);

  boxaDestroy(&boxa);

  return reinterpret_cast<jint>(pixa);
}

jint Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetResultIterator(JNIEnv *env,
    jobject thiz) {
  native_data_t *nat = get_native_data(env, thiz);

  return (jint) nat->api.GetIterator();
}

jstring Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetHOCRText(JNIEnv *env,
                                                                            jobject thiz, jint page) {

  native_data_t *nat = get_native_data(env, thiz);

  char *text = nat->api.GetHOCRText(page);

  jstring result = env->NewStringUTF(text);

  free(text);

  return result;
}

jstring Java_com_googlecode_tesseract_android_TessBaseAPI_nativeGetBoxText(JNIEnv *env,
                                                                           jobject thiz, jint page) {

  native_data_t *nat = get_native_data(env, thiz);

  char *text = nat->api.GetBoxText(page);

  jstring result = env->NewStringUTF(text);

  free(text);

  return result;
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetInputName(JNIEnv *env,
                                                                          jobject thiz,
                                                                          jstring name) {
  native_data_t *nat = get_native_data(env, thiz);
  const char *c_name = env->GetStringUTFChars(name, NULL);
  nat->api.SetInputName(c_name);
  env->ReleaseStringUTFChars(name, c_name);
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeSetOutputName(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jstring name) {
  native_data_t *nat = get_native_data(env, thiz);
  const char *c_name = env->GetStringUTFChars(name, NULL);
  nat->api.SetOutputName(c_name);
  env->ReleaseStringUTFChars(name, c_name);
}

void Java_com_googlecode_tesseract_android_TessBaseAPI_nativeReadConfigFile(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jstring fileName) {
  native_data_t *nat = get_native_data(env, thiz);
  const char *c_file_name = env->GetStringUTFChars(fileName, NULL);
  nat->api.ReadConfigFile(c_file_name);
  env->ReleaseStringUTFChars(fileName, c_file_name);
}
#ifdef __cplusplus
}
#endif

