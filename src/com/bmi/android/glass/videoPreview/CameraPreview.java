package com.bmi.android.glass.videoPreview;

import android.hardware.Camera;

/**
 *  A basic Camera preview class
 *    copied from android camera developer guide
 *      http://developer.android.com/guide/topics/media/camera.html#custom-camera
 */
public class CameraPreview extends android.view.SurfaceView
                        implements android.view.SurfaceHolder.Callback
{
	private static final String TAG = CameraPreview.class.getSimpleName();
	
    private android.view.SurfaceHolder mHolder;
    private android.hardware.Camera mCamera;

    public CameraPreview(android.content.Context context, android.hardware.Camera camera) {
        super(context);
        mCamera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(android.view.SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    public void surfaceCreated(android.view.SurfaceHolder holder) {
        android.util.Log.d(TAG, "surfaceCreated()");
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            Camera.Parameters cp = mCamera.getParameters();
            //cp.setColorEffect(Camera.Parameters.EFFECT_NEGATIVE);
            mCamera.setParameters(cp);
            mCamera.startPreview();
           // startFaceDetection(mCamera.getParameters()); // start face detection feature
        } catch (java.io.IOException e) {
            android.util.Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(android.view.SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
        android.util.Log.d(TAG, "surfaceDestroyed()");
    }

    public void surfaceChanged(android.view.SurfaceHolder holder, int format, int width, int height) {
        android.util.Log.d(TAG, "surfaceChanged()");
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
          // preview surface does not exist
          return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
          // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
			android.hardware.Camera.Parameters params = mCamera.getParameters();
			params.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO);
			params.setPreviewSize(width, height);
						
			//params.setColorEffect(android.hardware.Camera.Parameters.EFFECT_NEGATIVE);
			params.setPreviewFpsRange(30000, 30000); // specifically for the Google glass camera; from a solution found on http://stackoverflow.com/questions/19235477/google-glass-preview-image-scrambled-with-new-xe10-release
			mCamera.setParameters(params);
			
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

            startFaceDetection(params); // start face detection feature
        } catch (Exception e){
            android.util.Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }
    
    
    public void startFaceDetection (){
    	startFaceDetection(mCamera.getParameters());
    }
    
    public void startFaceDetection (final android.hardware.Camera.Parameters params){
        // start face detection only *after* preview has started
        if (params.getMaxNumDetectedFaces() > 0){
            // camera supports face detection, so can start it:
            android.util.Log.d(TAG, "Starting face detection...");
            mCamera.startFaceDetection();
        }
    }    
}