/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bmi.android.glass.videoPreview;

import com.bmi.android.glass.videoPreview.model.Landmarks;
import com.bmi.android.glass.videoPreview.model.Place;
import com.bmi.android.glass.videoPreview.util.MathUtils;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;
import com.google.android.glass.timeline.TimelineManager;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.view.View;

import java.util.List;

/**
 * The main application service that manages the lifetime of the compass live card and the objects
 * that help out with orientation tracking and landmarks.
 */
public class PreviewService extends Service {

	public static final String TAG = "preview service";
	
	public static final String LIVE_CARD_HIDE_REQUESTED = "com.bmi.android.glass.videoPreview.hideLiveCard",
                               LIVE_CARD_HIDDEN         = "com.bmi.android.glass.videoPreview.liveCardHidden",
                               LIVE_CARD_SHOW_REQUESTED = "com.bmi.android.glass.videoPreview.showLiveCard",
	                           LIVE_CARD_SHOWN          = "com.bmi.android.glass.videoPreview.liveCardShown",
	    	                   CAPTURE_STILL_REQUESTED  = "com.bmi.android.glass.videoPreview.captureStill",
	    	    	           STILL_CAPTURED           = "com.bmi.android.glass.videoPreview.stillCaptured",
	    	    	           CAPTURE_VIDEO_REQUESTED  = "com.bmi.android.glass.videoPreview.captureVideo",
	    	    	    	   CAPTURE_STOP_REQUESTED   = "com.bmi.android.glass.videoPreview.captureVideoStop",
	    	    	    	   VIDEO_CAPTURE_STARTED    = "com.bmi.android.glass.videoPreview.videoCaptureStarted",
	    	    	    	   VIDEO_CAPTURED           = "com.bmi.android.glass.videoPreview.videoCaptured",
	    	    	    	   CAPTURED_IMAGE_ACCEPTED  = "com.bmi.android.glass.videoPreview.imageAccepted",
	    	    	    	   CAPTURED_IMAGE_REJECTED  = "com.bmi.android.glass.videoPreview.imageRejected",
	    	    	    	   CAPTURED_VIDEO_ACCEPTED  = "com.bmi.android.glass.videoPreview.videoAccepted",
	    	    	    	   CAPTURED_VIDEO_REJECTED  = "com.bmi.android.glass.videoPreview.videoRejected";

    private static final String LIVE_CARD_ID = "video preview";

    /**
     * A binder that gives other components access to the speech capabilities provided by the
     * service.
     */
    public class CompassBinder extends Binder {
        /**
         * Read the current heading aloud using the text-to-speech engine.
         */
        public void readHeadingAloud() {
            float heading = mOrientationManager.getHeading();

            Resources res = getResources();
            String[] spokenDirections = res.getStringArray(R.array.spoken_directions);
            String directionName = spokenDirections[MathUtils.getHalfWindIndex(heading)];

            int roundedHeading = Math.round(heading);
            int headingFormat;
            if (roundedHeading == 1) {
                headingFormat = R.string.spoken_heading_format_one;
            } else {
                headingFormat = R.string.spoken_heading_format;
            }

            String headingText = res.getString(headingFormat, roundedHeading, directionName);
            mSpeech.speak(headingText, TextToSpeech.QUEUE_FLUSH, null);
        }
    }
    
    private android.content.BroadcastReceiver mBroadcastReceiver = new android.content.BroadcastReceiver() {
    	@Override
        public void onReceive(final android.content.Context context, Intent intent) {    		
            android.util.Log.d("preview service", "PreviewService.mBroadcastReceiver.onReceive() - action=" + intent.getAction());
        	if (intent.getAction().equals(LIVE_CARD_HIDE_REQUESTED)) {
        		pausePreview();
        	}
        	else if (intent.getAction().equals(LIVE_CARD_SHOW_REQUESTED)) {
        		try {
        			showLiveCard();
        			//resumePreview();
        		} catch (java.lang.Exception exception) {
		            android.util.Log.d("preview service", "Exception calling resumePreview() - " + exception);
        		}
        	}
        	else if (intent.getAction().equals(CAPTURE_STILL_REQUESTED)) {
        		if (mCapturedImageData != null) {
        			// making a request for another still while another image is pending approval is treated an implicit approval of the previous image
        			saveCapturedImage();
        		}
        		
/*        		mRenderer.mCamera.takePicture(null, null, null, new android.hardware.Camera.PictureCallback () {
        				public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
        		            android.util.Log.d("preview service", "PreviewService.onPictureTaken() - data length = " + data.length);

        		            mCapturedImageData = data;
        		            mCapturedImageTimestamp = new java.util.Date();
        		            
        		            Intent broadcast = new Intent();
        		            broadcast.setAction(PreviewService.STILL_CAPTURED);
        		            sendBroadcast(broadcast);
        				}
        			});*/
        	}
        	else if (intent.getAction().equals(CAPTURED_IMAGE_ACCEPTED)) {
        		//mTimelineManager.;
        		try {
        			saveCapturedImage();
        		} catch (java.lang.Exception exception) {
        	        android.util.Log.d("preview service", "onReceive() -- exception saving image: " + exception);        			
        		}
        	    //image.
        	    // Save a file: path for use with ACTION_VIEW intents
        	    //mCurrentPhotoPath = "file:" + image.getAbsolutePath();
        		//mRenderer.mCamera.startPreview();        		
        	}
        	else if (intent.getAction().equals(CAPTURED_IMAGE_REJECTED)) {
        		//.mCamera.startPreview();
        	}
        	
        	else if (intent.getAction().equals(CAPTURE_VIDEO_REQUESTED)) {
        		if (prepareVideoRecorder()) {
                    // Camera is available and unlocked, MediaRecorder is prepared,
                    // now you can start recording
                    mMediaRecorder.start();

                    // inform the user that recording has started
                    //setCaptureButtonText("Stop");
                    //isRecording = true;
		            Intent broadcast = new Intent();
		            broadcast.setAction(PreviewService.VIDEO_CAPTURE_STARTED);
		            sendBroadcast(broadcast);
                } else {
                    // prepare didn't work, release the camera
                    releaseMediaRecorder();
                    // inform user
                }
        	}
        	else if (intent.getAction().equals(CAPTURE_STOP_REQUESTED)) {
                // stop recording and release camera
                mMediaRecorder.stop();    // stop the recording
                releaseMediaRecorder();   // release the MediaRecorder object
                //mRenderer.mCamera.lock(); // take camera access back from MediaRecorder        		
        	}
        }
    };


    private final CompassBinder mBinder = new CompassBinder();

    private OrientationManager mOrientationManager;
    private Landmarks mLandmarks;
    private TextToSpeech mSpeech;

    private TimelineManager mTimelineManager;
    private LiveCard mLiveCard;
//    private PreviewRenderer mRenderer;
    private byte[] mCapturedImageData = null;
    private java.util.Date mCapturedImageTimestamp = null;
    private android.media.MediaRecorder mMediaRecorder = null;
    private java.lang.Object synchronizer = new java.lang.Object();

    @Override
    public void onCreate() {
        android.util.Log.d("preview service", "onCreate()");
        super.onCreate();

        mTimelineManager = TimelineManager.from(this);

        // Even though the text-to-speech engine is only used in response to a menu action, we
        // initialize it when the application starts so that we avoid delays that could occur
        // if we waited until it was needed to start it up.
        mSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                // Do nothing.
            }
        });

        SensorManager sensorManager =
                (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mOrientationManager = new OrientationManager(sensorManager, locationManager);
        mLandmarks = new Landmarks(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.d("preview service", "onStartCommand() -- mLiveCard " + ((mLiveCard == null) ? "==" : "!=") + " NULL");
        if (mLiveCard == null) {
            mLiveCard = mTimelineManager.createLiveCard(LIVE_CARD_ID);
            //mRenderer = new PreviewRenderer(this, mOrientationManager, mLandmarks);

            mLiveCard.setDirectRenderingEnabled(true);
            //mLiveCard.getSurfaceHolder().addCallback(mRenderer);

            // Display the options menu when the live card is tapped.
            Intent menuIntent = new Intent(this, PreviewMenuActivity.class);
            menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));

            
            //mLiveCard.publish(PublishMode.REVEAL);
            showLiveCard();
            
            Intent foregroundIntent = new Intent(getBaseContext(), PreviewForegroundActivity.class);
            foregroundIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplication().startActivity(foregroundIntent);

        
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction(LIVE_CARD_HIDE_REQUESTED);
            filter.addAction(LIVE_CARD_SHOW_REQUESTED);
            filter.addAction(CAPTURE_STILL_REQUESTED);
            filter.addAction(CAPTURED_IMAGE_ACCEPTED);
            filter.addAction(CAPTURED_IMAGE_REJECTED);
            filter.addAction(CAPTURE_VIDEO_REQUESTED);
            filter.addAction(CAPTURE_STOP_REQUESTED);
            filter.addAction(CAPTURED_VIDEO_ACCEPTED);
            filter.addAction(CAPTURED_VIDEO_REJECTED);
            registerReceiver(mBroadcastReceiver, filter);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        android.util.Log.d("preview service", "onDestroy() -- mLiveCard " + ((mLiveCard == null) ? "==" : "!=") + " NULL");
        pausePreview();
        mLiveCard = null;

        mSpeech.shutdown();
        unregisterReceiver(mBroadcastReceiver);

        mSpeech = null;
        mOrientationManager = null;
        mLandmarks = null;

        super.onDestroy();
    }
    
    private void showLiveCard() {
        if (mLiveCard != null && !(mLiveCard.isPublished()) ) {
            //mLiveCard.getSurfaceHolder().addCallback(mRenderer);
            mLiveCard.publish(PublishMode.REVEAL);

            Intent broadcast = new Intent();
            broadcast.setAction(PreviewService.LIVE_CARD_SHOWN);
            sendBroadcast(broadcast);
        }
    }
    
    private void pausePreview() {
        if (mLiveCard != null && mLiveCard.isPublished()) {
            mLiveCard.unpublish();
        	//mRenderer.mCamera.stopPreview();
        	//mRenderer.mCamera.unlock();
            //mLiveCard.getSurfaceHolder().removeCallback(mRenderer);
            
            Intent broadcast = new Intent();
            broadcast.setAction(PreviewService.LIVE_CARD_HIDDEN);
            sendBroadcast(broadcast);
            
            //mRenderer.repaint();
        }
    }

    private void resumePreview() {
        if (mLiveCard != null && mLiveCard.isPublished()) {
        	//mRenderer.mCamera.lock();
        	//mRenderer.mCamera.startPreview();
        	
            //mLiveCard.getSurfaceHolder().addCallback(mRenderer);

            Intent broadcast = new Intent();
            broadcast.setAction(PreviewService.LIVE_CARD_SHOWN);
            sendBroadcast(broadcast);
        }
    }
    
    private void saveCapturedImage() {
    	final byte[] finalImageData = mCapturedImageData;
		final java.util.Date finalImageTimestamp = mCapturedImageTimestamp;
			
		mCapturedImageData = null;

    	new java.lang.Thread(new java.lang.Runnable() {
    		private byte[] m_runnable_imageData = finalImageData;
    		private java.util.Date m_runnable_imageTimestamp = finalImageTimestamp;
    			
    		public void run() {
    			try {
    	    		// Save the image to a temporary file
    	    		String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(m_runnable_imageTimestamp);
    	    		String imageFilenamePrefix = /*"JPEG_" + */timeStamp + "_";
    	    		java.io.File storageDir = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), "Camera"); //.DIRECTORY_PICTURES);
    	    		java.io.File imageFile = java.io.File.createTempFile(
    	    			imageFilenamePrefix, // prefix
    	        		".jpg",              // suffix
    	        		storageDir );        // directory

    	    		java.io.FileOutputStream imageStream = new java.io.FileOutputStream(imageFile);
    	    		imageStream.write(m_runnable_imageData, 0, m_runnable_imageData.length);
    	    		imageStream.close();

    	    		android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(m_runnable_imageData, 0, m_runnable_imageData.length);
    	    		//android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap (bitmap, 640, 360, false);
    	    		android.graphics.Bitmap scaledBitmap = android.media.ThumbnailUtils.extractThumbnail(bitmap, 640, 360);
    	    		android.util.Log.d("preview service", "saveCapturedImage() -- image = " + bitmap.getWidth() + "x" + bitmap.getHeight());
    	    		android.util.Log.d("preview service", "saveCapturedImage() -- scaled image = " + scaledBitmap.getWidth() + "x" + scaledBitmap.getHeight());
    	    		
    	    		
    	    		String scaledImageFileName = timeStamp + "_SCALED_";
    	    		java.io.File scaledImageFile = java.io.File.createTempFile(
    	    				scaledImageFileName,  // prefix
    	        		".jpg",         // suffix
    	        		android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES) );   // directory
    	    		java.io.FileOutputStream scaledImageStream = (new java.io.FileOutputStream(scaledImageFile));
    	    		scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, // Bitmap.CompressFormat format -- The format of the compressed image; e.g., JPEG, PNG, WEBP 
    	    				              50, // int quality -- Hint to the compressor, 0-100. 0 meaning compress for small size, 100 meaning compress for max quality.
    	    				              scaledImageStream ); // OutputStream stream -- The outputstream to write the compressed data.
    	    		scaledImageStream.close();
    	    		
    	    		// add the image to the device gallery
    	    	    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
    	    	    java.io.File f = new java.io.File("file:" + imageFile.getAbsolutePath());
    	            android.util.Log.d("preview service", "saveCapturedImage() -- saving image '" + f + "' to gallery");
    	    	    android.net.Uri contentUri = android.net.Uri.fromFile(f);
    	    	    mediaScanIntent.setData(contentUri);
    	    	    sendBroadcast(mediaScanIntent);

    	    	    com.google.android.glass.app.Card timelineCard = new com.google.android.glass.app.Card(PreviewService.this);
    	    	    android.net.Uri scaledImageUri = android.net.Uri.fromFile(scaledImageFile);
    	    	    timelineCard.setImageLayout(com.google.android.glass.app.Card.ImageLayout.FULL);
    	    	    timelineCard.addImage(scaledImageUri);
    	            android.util.Log.d("preview service", "saveCapturedImage() -- scaled image '" + scaledImageUri + "' added to timeline");
    	    	    //timelineCard.addImage(android.net.Uri.parse("http://photos4.meetupstatic.com/photos/member/a/6/e/2/member_147102722.jpeg"));
    	    	    timelineCard.setFootnote("" + mCapturedImageTimestamp);
    	    	    
    	    	    timelineCard.toView().setOnClickListener(
    	    	    	new View.OnClickListener() {
    	    	    		@Override
    	    	    		public void onClick(View v) {
    	    	    			android.util.Log.d("preview service", "timeline card clicked");
    	    	    		}
    	    	    	} );
    	    	    timelineCard.toView().setOnTouchListener(
    	    		    	new View.OnTouchListener() {
    	    		    		@Override
    	    		    		public boolean onTouch(View v, android.view.MotionEvent motionEvent) {
    	    		    			android.util.Log.d("preview service", "timeline card clicked");
    	    		    			return true;
    	    		    		}
    	    		    	} );
    	    	    
    	    	    mTimelineManager.insert(timelineCard);    				
    	       	} catch (java.lang.Exception exception) {
    				android.util.Log.d("preview service", "error saving captured image");
    	    	}
   			}
   		}).start();
    }

    private boolean prepareVideoRecorder(){
        mMediaRecorder = new android.media.MediaRecorder();
        android.hardware.Camera.CameraInfo inf = new android.hardware.Camera.CameraInfo();
        final int numCameras=android.hardware.Camera.getNumberOfCameras();
		android.util.Log.d("preview service", numCameras + " cameras");
        for(int i=0; i<numCameras; i++) {
        	android.hardware.Camera.getCameraInfo(i, inf);
			android.util.Log.d("preview service", "camera " + i+ ": facing=" + ((inf.facing==android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) ? "CAMERA_FACING_FRONT" : "CAMERA_FACING_BACK"));
            //if(inf.facing==android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)
            //{
            //    return Camera.open(i);
            //}
        }

        // Step 1: Unlock and set camera to MediaRecorder
        //mRenderer.mCamera.unlock();
/*
        mMediaRecorder.setCamera(mRenderer.mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(android.media.MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        android.util.Log.d("preview service", "setting profile...");
        //mMediaRecorder.setProfile(android.media.CamcorderProfile.get(0, android.media.CamcorderProfile.QUALITY_720P));//.QUALITY_HIGH));
        //mMediaRecorder.setProfile(android.media.CamcorderProfile.get(0, android.media.CamcorderProfile.QUALITY_HIGH));
        mMediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.DEFAULT);
        android.util.Log.d("preview service", "setting framerate...");
        mMediaRecorder.setVideoFrameRate(30);
*/        
        
        // Step 4: Set output file
        //mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
		String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(mCapturedImageTimestamp = new java.util.Date());
		String videoFilenamePrefix = /*"JPEG_" + */timeStamp + "_";
		java.io.File storageDir = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), "Camera"); //.DIRECTORY_PICTURES);
        try {
        	java.io.File videoFile = java.io.File.createTempFile( // we call createTempFile to ensure that filename is unique
				videoFilenamePrefix, // prefix
	    		".mp4",              // suffix
	    		storageDir );        // directory
        	videoFile.delete();
/*        	
        	mMediaRecorder.setOutputFile(videoFile.toString());

        	// Step 5: Set the preview output
        	mMediaRecorder.setPreviewDisplay(mRenderer.mHolder.getSurface());

        	// Step 6: Prepare configured MediaRecorder
            mMediaRecorder.prepare();
*/
  //      	mMediaRecorder.setCamera(mRenderer.mCamera);
        	mMediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
        	mMediaRecorder.setVideoSource(android.media.MediaRecorder.VideoSource.CAMERA);
        	android.util.Log.i(TAG, "a");

//            mMediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
//            mMediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.DEFAULT);
//            mMediaRecorder.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.H263);
        	mMediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
        	mMediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
        	mMediaRecorder.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.MPEG_4_SP);
            android.util.Log.i(TAG, "b");

            mMediaRecorder.setMaxDuration(30000); // set to 20000

//            String uniqueOutFile = OUTPUT_FILE + System.currentTimeMillis() + ".3gp";
//            File outFile = new File(uniqueOutFile);
//            if (outFile.exists()) {
//                outFile.delete();
//            }
            android.util.Log.i(TAG, "setting output file to '" + videoFile.getAbsolutePath() + "'");
            mMediaRecorder.setOutputFile(videoFile.getAbsolutePath());
            //mMediaRecorder.setVideoFrameRate(20); // set to 20
            //mMediaRecorder.setVideoSize(640, 360);
            android.util.Log.i(TAG, "c");

            //mMediaRecorder.setPreviewDisplay(mRenderer.mHolder.getSurface());
            mMediaRecorder.setMaxFileSize(50000); // set to 50000
            mMediaRecorder.prepare();
            android.util.Log.i(TAG, "d");
        } catch (IllegalStateException e) {
            android.util.Log.d("preview service", "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (java.io.IOException e) {
        	android.util.Log.d("preview service", "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }
    
    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            //mRenderer.mCamera.lock();           // lock camera for later use
        }
    }
}
