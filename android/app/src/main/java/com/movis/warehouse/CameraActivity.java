package com.movis.warehouse;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** In-app rear-camera capture. There is no gallery import, pinch zoom or zoom control. */
@SuppressWarnings("deprecation")
public class CameraActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private SurfaceView preview;
    private FrameLayout frame;
    private Button shutter;
    private TextView status;
    private boolean taking=false;
    private SurfaceHolder ready;
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle s){
        super.onCreate(s);new File(getCacheDir(),"capture.jpg").delete();getWindow().setStatusBarColor(0xff152c36);getWindow().setNavigationBarColor(0xff152c36);getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff152c36);
        root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});
        TextView title=new TextView(this);title.setText("Take a photo");title.setTextSize(18);title.setTextColor(0xffffffff);title.setGravity(Gravity.CENTER);title.setPadding(dp(16),dp(18),dp(16),dp(8));root.addView(title);
        status=new TextView(this);status.setText("Fit the sacks in the frame. Keep each sack visible.");status.setTextColor(0xffc4d9cd);status.setGravity(Gravity.CENTER);status.setPadding(dp(16),dp(8),dp(16),dp(16));root.addView(status);
        frame=new FrameLayout(this);frame.setBackgroundColor(0xff000000);preview=new SurfaceView(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(24),dp(14),dp(24),dp(20));TextView hint=new TextView(this);hint.setText("Review the detections before saving your transaction.");hint.setTextColor(0xffc4d9cd);hint.setGravity(Gravity.CENTER);hint.setPadding(0,0,0,dp(12));controls.addView(hint);
        shutter=new Button(this);shutter.setText("Capture photo");shutter.setAllCaps(false);shutter.setTextSize(17);shutter.setTextColor(0xffffffff);android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(0xff087f70);shape.setCornerRadius(dp(18));shutter.setBackground(shape);controls.addView(shutter,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView cancel=new TextView(this);cancel.setText("Cancel");cancel.setTextColor(0xffffffff);cancel.setTextSize(15);cancel.setGravity(Gravity.CENTER);cancel.setPadding(0,dp(16),0,dp(6));cancel.setOnClickListener(v->{if(!taking)finish();});controls.addView(cancel);root.addView(controls);setContentView(root);root.requestApplyInsets();
        shutter.setEnabled(false);preview.getHolder().addCallback(this);preview.setOnTouchListener((v,event)->true);
        shutter.setOnClickListener(v->{if(camera==null||taking)return;taking=true;shutter.setEnabled(false);status.setText("Saving photo…");try{camera.takePicture(null,null,(bytes,c)->{try(FileOutputStream out=new FileOutputStream(new File(getCacheDir(),"capture.jpg"))){out.write(bytes);setResult(RESULT_OK);finish();}catch(IOException e){status.setText("Capture failed. Try again.");taking=false;try{camera.startPreview();shutter.setEnabled(true);}catch(RuntimeException ignored){release();}}});}catch(RuntimeException e){status.setText("Camera capture failed. Reopen the camera.");taking=false;release();}});
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},7);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==7){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){if(ready!=null)start(ready);}else{status.setText("Camera permission is required to take new MOVIS images.");}}}
    public void surfaceCreated(SurfaceHolder h){ready=h;if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)start(h);}
    public void surfaceChanged(SurfaceHolder h,int format,int w,int height){}
    public void surfaceDestroyed(SurfaceHolder h){ready=null;release();}
    void start(SurfaceHolder h){if(camera!=null)return;try{int id=-1;Camera.CameraInfo info=new Camera.CameraInfo();for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){id=i;break;}}if(id<0)throw new IOException("No rear camera available");camera=Camera.open(id);Camera.Parameters p=camera.getParameters();if(p.isZoomSupported())p.setZoom(0);List<String> modes=p.getSupportedFocusModes();if(modes!=null&&modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);Camera.Size size=null;for(Camera.Size s:p.getSupportedPictureSizes()){if(s.width*s.height<=4000000&&(size==null||s.width*s.height>size.width*size.height))size=s;}if(size!=null)p.setPictureSize(size.width,size.height);double targetRatio=size==null?4.0/3.0:size.width/(double)size.height;Camera.Size chosenPreview=null;double bestRatio=Double.MAX_VALUE;for(Camera.Size candidate:p.getSupportedPreviewSizes()){double diff=Math.abs(candidate.width/(double)candidate.height-targetRatio);if(diff<bestRatio){chosenPreview=candidate;bestRatio=diff;}}if(chosenPreview!=null){p.setPreviewSize(chosenPreview.width,chosenPreview.height);final double ratio=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT?chosenPreview.height/(double)chosenPreview.width:chosenPreview.width/(double)chosenPreview.height;frame.post(()->{int w=frame.getWidth(),height=frame.getHeight();if(w<=0||height<=0)return;int viewWidth=w,viewHeight=(int)(w/ratio);if(viewHeight>height){viewHeight=height;viewWidth=(int)(height*ratio);}preview.setLayoutParams(new FrameLayout.LayoutParams(viewWidth,viewHeight,Gravity.CENTER));});}Camera.getCameraInfo(id,info);int rotation=getWindowManager().getDefaultDisplay().getRotation(),degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;camera.setDisplayOrientation((info.orientation-degrees+360)%360);p.setRotation((info.orientation+degrees)%360);camera.setParameters(p);camera.setPreviewDisplay(h);camera.startPreview();taking=false;status.setText("Fit the sacks in the frame. Keep each sack visible.");shutter.setEnabled(true);}catch(Exception e){status.setText("Camera unavailable. Close other camera apps and try again.");release();}}
    @Override protected void onPause(){super.onPause();release();}
    @Override protected void onResume(){super.onResume();if(ready!=null&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)start(ready);}
    void release(){taking=false;if(camera!=null){camera.release();camera=null;}if(shutter!=null)shutter.setEnabled(false);}
}
