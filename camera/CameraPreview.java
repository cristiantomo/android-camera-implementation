package com.predixtor.beePic.ui.camera;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * A basic Camera preview class
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private CameraActivity mActivity;
    private String TAG = "CameraPreviewClass";


    public CameraPreview(Context context, Camera camera) {
        super(context);

        mCamera = camera;
        mActivity = (CameraActivity) context;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
            /*// deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);*/
    }

    public void surfaceCreated(SurfaceHolder holder) {

        // Adjusts preview according to device rotation
        setCameraDisplayOrientation(mActivity, mActivity.getCameraId(), mCamera);

        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }

    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // start preview size and make any resize, rotate or
        // reformatting changes here

        // Set focus mode to continuous picture
        try {
            setFocus(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            setPreviewSize(w, h);
        } catch (Exception e) {
            e.printStackTrace();
        }


        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    //----------------------------------------------------------------------------------------------
    private void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        int result = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                result = 90;
                break;
            case Surface.ROTATION_90:
                result = 0;
                break;
            case Surface.ROTATION_180:
                result = 270;
                break;
            case Surface.ROTATION_270:
                result = 180;
                break;
        }

        try {
            camera.setDisplayOrientation(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //----------------------------------------------------------------------------------------------
    private void setFocus(String mParameter) {
        Camera.Parameters camParams = mCamera.getParameters();
        camParams.setFocusMode(mParameter);
        mCamera.setParameters(camParams);
    }

    //----------------------------------------------------------------------------------------------
    private void setPreviewSize(int w, int h) {
        Camera.Parameters camParams = mCamera.getParameters();
        camParams.setPreviewSize(w, h);
        if (h > w) {
            camParams.setPreviewSize(h, w);
        }
        mCamera.setParameters(camParams);
    }


}