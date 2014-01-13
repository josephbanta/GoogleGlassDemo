package com.bmi.android.glass.videoPreview;


import java.util.List;

import com.google.android.glass.timeline.TimelineManager;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;

/**
 * This activity manages the options menu that appears when the user taps on the compass's live
 * card.
 */
public class PreviewForegroundActivity extends Activity {
    private static final String TAG = PreviewForegroundActivity.class.getSimpleName();

    public static final String VIDEO_PREVIEW_STOPPED_ACTION = "com.bmi.android.glass.videoPreview.stopped";

	private static final int REQUEST_VIDEO_CAPTURE = 1,
			                 REQUEST_IMAGE_CAPTURE = 2;
	
	private static final int STATE__NONE                                 = 0,
                             STATE__VIDEO_PREVIEW                        = 2,
	                         STATE__CAPTURING_VIDEO                      = 1,
	                         STATE__STILL_CAPTURED_PENDING_USER_APPROVAL = 3,
	    	                 STATE__VIDEO_CAPTURE_ONGOING                = 4,
	    	                 STATE__VIDEO_CAPTURED_PENDING_USER_APPROVAL = 5,
	    	    	         STATE__IN_OPTIONS_MENU                      = 6;

    private boolean                                            mResumed;
    private boolean                                            mPreviewStopMessageReceived;
    private boolean                                            mLongCameraKeyPress;
    private com.google.android.glass.touchpad.GestureDetector  mGestureDetector;
    private int                                                mState = STATE__NONE;

    private android.widget.RelativeLayout                      mainView;
    //private android.view.SurfaceView                           mSurfaceView;
    private CameraPreview                                      mPreview;
    //private android.view.SurfaceHolder                         mSurfaceHolder;
    //private android.view.SurfaceHolder.Callback                mSurfaceHolderCallback;
    private int                                                mSurfaceWidth;
    private int                                                mSurfaceHeight;
    private android.hardware.Camera                            mCamera = null;
    private java.util.Hashtable                                mCameraParametersWhenPaused = null;
    private boolean                                            mPreviewRunning = false;
    private byte[]                                             mCapturedImageData;
    private java.util.Date                                     mCapturedImageTimestamp;
    private com.google.android.glass.timeline.TimelineManager  mTimelineManager;
    private android.media.MediaRecorder                        mMediaRecorder;
    private float                                              mScrollVelocity;
    private boolean                                            mPreviewIsZooming;
    private java.util.Date                                     mLastZoomInvocationTime;
    private String                                             mCurrentOptionsParentMenu;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
		android.util.Log.e(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // the screen still dims after 10 seconds with this in -- there must be something else
      
        mainView = new android.widget.RelativeLayout(this);
        //mainView.setAlpha(0.0f);
        
        getWindow().setContentView(mainView);

        mGestureDetector = createGestureDetector(this);

        mTimelineManager = TimelineManager.from(this);
    }

    /** A safe way to get an instance of the Camera object. */
    public static android.hardware.Camera getCameraInstance(){
    	android.hardware.Camera c = null;
    	for (int numAttempts=0; numAttempts<10; numAttempts++) {
    		try {
    			c = android.hardware.Camera.open(); // attempt to get a Camera instance
    		}
    		catch (Exception e){
    			// Camera is not available (in use or does not exist); sleep 100 ms and try again
    			//   this approach was added because (as of SDK preview version from 2013/12/20), apparently when
    			//   this activity is started from a voice command, it takes some time for the system to release the
    			//   microphone and (for some reason) the camera; waiting a few hundred milliseconds seems to fix the
    			//   problem (see documented issue https://code.google.com/p/google-glass-api/issues/detail?id=259)
    			if ((numAttempts+1) < 10) {
    				try { java.lang.Thread.sleep(100); } catch (java.lang.InterruptedException e1) {}
    			}
    		}
    		
    		if (c != null) {
    			numAttempts = (numAttempts+1); // for the sake of the log message
    			android.util.Log.d(TAG, "PreviewForegroundActivity.getCameraInstance() - camera acquired on " + numAttempts + ((numAttempts==1) ? "st" : ((numAttempts==2) ? "nd" : (numAttempts==3) ? "rd" : "th")) + " attempt");
    			break;
    		}
    	}
    	
		if (c == null) {
			android.util.Log.e(TAG, "PreviewForegroundActivity.getCameraInstance() - camera could not be acquired.");
		}
        return c; // returns null if camera is unavailable
    }
    
    @Override
    protected void onResume() {
		android.util.Log.d(TAG, "PreviewForegroundActivity.onResume()");
try {
        super.onResume();
        if (mCamera == null) {
        	mainView.removeAllViews();

            // Create a Camera instance of Camera
            if ((mCamera = getCameraInstance()) == null) {
            	finish();
            }
        }
        
        if (mCamera != null) {
    		android.util.Log.d(TAG, "PreviewForegroundActivity.onResume() - camera = " + ((mCamera == null) ? "null" : "non-null"));
            mCamera.setZoomChangeListener(
            	new android.hardware.Camera.OnZoomChangeListener() {
            		public void onZoomChange(int zoomValue, boolean zoomingIsStopped, android.hardware.Camera camera) {
            			mPreviewIsZooming = !zoomingIsStopped;
            		}
            	} );
            if (mCamera != null) {
            	if (this.mCameraParametersWhenPaused != null) {
            		android.hardware.Camera.Parameters cameraParams = mCamera.getParameters();
            		loadCameraParameters(cameraParams, this.mCameraParametersWhenPaused);
            		this.mCameraParametersWhenPaused = null;
            		mCamera.setParameters(cameraParams);
            	}
            	mPreview = new CameraPreview(this, mCamera);
            	mCamera.setFaceDetectionListener(
            			new android.hardware.Camera.FaceDetectionListener() {
            				@Override
            				public void onFaceDetection(android.hardware.Camera.Face[] faces, android.hardware.Camera camera) {
            					if (faces.length > 0) {
            						android.util.Log.d(TAG, "face detected: "+ faces.length +
            								" Face 1 Location X: " + faces[0].rect.centerX() +
            								"Y: " + faces[0].rect.centerY() );
            					}
            				}
            			} );
            
            	mainView.addView( mPreview,
            	                  new android.widget.RelativeLayout.LayoutParams( android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            		        		                                              android.view.ViewGroup.LayoutParams.MATCH_PARENT ));
            }
        }
} catch (java.lang.Exception e) {
	android.util.Log.d(TAG, "onResume() - Exception: " + e);
	e.printStackTrace();
}
    }

    @Override
    protected void onPause() {
		android.util.Log.d(TAG, "PreviewForegroundActivity.onPause()");
		
		try {
			this.mCameraParametersWhenPaused = saveCameraParameters(mCamera.getParameters()); // at the time of this writing, invoking parameters.flatten() yields "java.lang.StringIndexOutOfBoundsException: length=0; index=-1" -- if you want something done right, you have to do it yourself
		} catch (java.lang.Exception e) {
			android.util.Log.d(TAG, "PreviewForegroundActivity.onPause() - exception saving camera parameters: " + e);
			
		}
        super.onPause();
        mResumed = false;

        // from http://developer.android.com/guide/topics/media/camera.html#custom-camera
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
        mainView.removeView(mPreview);
    }

    
    @Override
    protected void onDestroy() {
		android.util.Log.d(TAG, "PreviewForegroundActivity.onDestroy()");

//		unregisterReceiver(mBroadcastReceiver);
//        unbindService(mConnection);
		//mCamera.release();

        super.onDestroy();
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }
    
    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            //mRenderer.mCamera.lock();           // lock camera for later use
        }
    }


    @Override
    public void openOptionsMenu() {
            super.openOptionsMenu();
    }

    @Override
   public boolean onCreateOptionsMenu(Menu menu) {
    	getMenuInflater().inflate(R.menu.preview, menu);
    	
        return true;
    }

    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
    	// dynamically construct the video effects menu based upon the effects supported on this device
    	String effectsTitleString = getString(R.string.video_effects);
    	for (int i=0, menuSize=menu.size(); i<menuSize; i++) {
    		MenuItem item = menu.getItem(i);
			//android.util.Log.d(TAG, "onCreateOptionsMenu() - menu item " + i + " = '" + item.getTitle() + "' - comparing to '" + settingsTitleString + "'");
    		if (item.getTitle().equals(effectsTitleString)) {
    			//android.util.Log.d(TAG, "onCreateOptionsMenu() - adding items to menu item '" + item.getTitle() + "'");
    			android.view.SubMenu subMenu = item.getSubMenu();
    			subMenu.clear();

    			android.hardware.Camera.Parameters cameraParameters = mCamera.getParameters();
    			android.view.SubMenu subSubMenu = subMenu.addSubMenu("Color effects");
    			java.util.List<String> supportedEffects = cameraParameters.getSupportedColorEffects();
    			for (String effect : supportedEffects) {
    				MenuItem menuItem = subSubMenu.add(effect);
    				menuItem.setCheckable(true);
    			}
    			
    			subSubMenu = subMenu.addSubMenu("Antibanding effects");
    			supportedEffects = cameraParameters.getSupportedAntibanding();
    			for (String effect : supportedEffects) {
    				MenuItem menuItem = subSubMenu.add(effect);
    				menuItem.setCheckable(true);
    			}
    			
    			subSubMenu = subMenu.addSubMenu("Flash modes");
    			supportedEffects = cameraParameters.getSupportedFlashModes();
    			for (String effect : supportedEffects) {
    				MenuItem menuItem = subSubMenu.add(effect);
    				menuItem.setCheckable(true);
    			}
    			
    			subSubMenu = subMenu.addSubMenu("Focus modes");
    			supportedEffects = cameraParameters.getSupportedFocusModes();
    			for (String effect : supportedEffects) {
    				MenuItem menuItem = subSubMenu.add(effect);
    				menuItem.setCheckable(true);
    			}
    			
    			subSubMenu = subMenu.addSubMenu("Scene modes");
    			supportedEffects = cameraParameters.getSupportedSceneModes();
    			for (String effect : supportedEffects) {
    				MenuItem menuItem = subSubMenu.add(effect);
    				menuItem.setCheckable(true);
    			}
    			
    			subSubMenu = subMenu.addSubMenu("White balance");
    			supportedEffects = cameraParameters.getSupportedWhiteBalance();
    			for (String effect : supportedEffects) {
    				MenuItem menuItem = subSubMenu.add(effect);
    				menuItem.setCheckable(true);
    			}
    			
    			if (cameraParameters.isVideoStabilizationSupported()) {
    				subSubMenu = subMenu.addSubMenu("Video Stabilization");
    				boolean currentState = cameraParameters.getVideoStabilization();
    				subSubMenu.add("Turn " + (currentState ? "OFF" : "ON"));
    			}
    		}
    	}
    	
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	boolean result = false;
    	
        switch (item.getItemId()) {
            case R.id.read_aloud:
//            	mPreviewService.readHeadingAloud();
            	result = true;
            case R.id.stop:
            	finish();
            	result = true;

            default:
    			android.util.Log.d(TAG, "onOptionsItemSelected() - item = " + item);
    			if (mCurrentOptionsParentMenu != null) {
    				if (mCurrentOptionsParentMenu.equals("Color effects")) {
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - setting color effect to " + item);
    	    			//mCamera.stopPreview();
    					android.hardware.Camera.Parameters cameraParams = mCamera.getParameters();
    					cameraParams.setColorEffect(item.getTitle().toString());
    					
    					//try {java.lang.Thread.sleep(1000);} catch (java.lang.Exception e) {}
    					mCamera.setParameters(cameraParams);
    					//mCamera.startPreview();
    					result = true;
    				}
    				else if (mCurrentOptionsParentMenu.equals("Antibanding effects")) {
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - setting antibanding to " + item);
    					android.hardware.Camera.Parameters cameraParams = mCamera.getParameters();
    					cameraParams.setAntibanding(item.getTitle().toString());
    					mCamera.setParameters(cameraParams);
    					result = true;
    				}
    				else if (mCurrentOptionsParentMenu.equals("Flash modes")) {
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - setting flash mode to " + item);
    					android.hardware.Camera.Parameters cameraParams = mCamera.getParameters();
    					cameraParams.setFlashMode(item.getTitle().toString());
    					mCamera.setParameters(cameraParams);
    					result = true;
    				}
    				else if (mCurrentOptionsParentMenu.equals("Focus modes")) {
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - setting focus mode to " + item);
    					android.hardware.Camera.Parameters cameraParams = mCamera.getParameters();
    					cameraParams.setFocusMode(item.getTitle().toString());
    					mCamera.setParameters(cameraParams);
    					result = true;
    				}
    				else if (mCurrentOptionsParentMenu.equals("Scene modes")) {
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - setting scene mode to " + item);
    					android.hardware.Camera.Parameters cameraParams = mCamera.getParameters();
    					cameraParams.setSceneMode(item.getTitle().toString());
    					mCamera.setParameters(cameraParams);
    					result = true;
    				}
    				else if (mCurrentOptionsParentMenu.equals("White balance")) {
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - setting white balance to " + item);
    					android.hardware.Camera.Parameters cameraParams = mCamera.getParameters();
    					cameraParams.setWhiteBalance(item.getTitle().toString());
    					mCamera.setParameters(cameraParams);
    					result = true;
    				}
    				else if (mCurrentOptionsParentMenu.equals("Video Stabilization")) {
    	    			String action = item.getTitle().toString();
    					boolean newState = action.equals("Turn ON") ? true : false;
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - turning video stabilization " + (newState ? "ON" : "OFF"));
    	    			
    					android.hardware.Camera.Parameters cameraParams = mCamera.getParameters();
    					cameraParams.setVideoStabilization(newState);
    					mCamera.setParameters(cameraParams);
    					
    					cameraParams = mCamera.getParameters();
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - after turning video stabilization, state=" + cameraParams.getVideoStabilization());
    					result = true;
    				}
    			}
    			
    			if (item.hasSubMenu()) {
    				mCurrentOptionsParentMenu = item.getTitle().toString();
    				if (mCurrentOptionsParentMenu.equals("Video Stabilization")) {
    					item.getSubMenu().clear();
    					String newItem= mCamera.getParameters().getVideoStabilization() ? "Turn OFF" : "Turn ON";
    	    			android.util.Log.d(TAG, "onOptionsItemSelected() - adding item '" + newItem + "' to parent menu");
    					item.getSubMenu().add(newItem);    					
    				}
    			}
    			else {
    				mCurrentOptionsParentMenu = null;
    			}
// menuInfo = item.getMenuInfo();
//            	item.
            	//if (item.getMenuInfo()) {
            		
            	//}
        }

        if (result == false) {
        	result = super.onOptionsItemSelected(item);
        }
        return result;
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);

        mState = STATE__VIDEO_PREVIEW;
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {    	

    	boolean result = false;
        if (keyCode == android.view.KeyEvent.KEYCODE_CAMERA) {
            // Stop the preview and release the camera.
            // Execute your logic as quickly as possible
            // so the capture happens quickly.
        	if (event.isLongPress()) {
        		// capture video on long press down
        		android.util.Log.d(TAG, "PreviewForegroundActivity.onKeyDown() - long press down");
        		mLongCameraKeyPress = true;
        		        		
        		/**/
            	android.media.AudioManager audio = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
            	audio.playSoundEffect(com.google.android.glass.media.Sounds.SUCCESS);
            	
            	if (true) {
            		// invoke the standard video recorder app
            		this.mCapturedImageTimestamp = new java.util.Date();
            		Intent takeVideoIntent = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
            	    if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            	        startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
            	    }
        			mState = STATE__CAPTURING_VIDEO;
            	}
            	else {
            		// create a custom media recorder using the android APIs
            		if (prepareVideoRecorder()) {
            			// Camera is available and unlocked, MediaRecorder is prepared,
            			// now you can start recording
            			mMediaRecorder.start();
            			mState = STATE__CAPTURING_VIDEO;
            		} else {
            			// prepare didn't work, release the camera
            			releaseMediaRecorder();
            			// inform user
            		}
            	}
        	}
        	result = true;
        }
        else {
        	result = super.onKeyDown(keyCode, event);
        }
        
        return result;
    }
    
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
    	final int KEY_SWIPE_DOWN = 4;
    	
    	boolean result = false;
        if (keyCode == android.view.KeyEvent.KEYCODE_CAMERA) {
            // Stop the preview and release the camera.
            // Execute your logic as quickly as possible
            // so the capture happens quickly.
        	if (mLongCameraKeyPress) { //(event.isLongPress()) {
        		// capture video on long press
        		//android.util.Log.d(TAG, "PreviewForegroundActivity.onKeyUp() - long press up");
        	}
        	else {
        		// capture still on short (normal duration) press up
        		android.util.Log.d(TAG, "PreviewForegroundActivity.onKeyUp() - short press up");

            	android.media.AudioManager audio = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
            	audio.playSoundEffect(com.google.android.glass.media.Sounds.SUCCESS);
            	
        		if (mCapturedImageData != null) {
        			// making a request for another still while another image is pending approval is treated an implicit approval of the previous image
        			saveCapturedImage();
        		}
        		
        		//mCamera.takePicture(null, null, null,
        		mCamera.takePicture(null, null,
        			new android.hardware.Camera.PictureCallback () {
        				public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
        		            android.util.Log.d(TAG, "onPictureTaken() - data length = " + data.length);

        		            mCapturedImageData = data;
        		            mCapturedImageTimestamp = new java.util.Date();
        		            
        		            mState = STATE__STILL_CAPTURED_PENDING_USER_APPROVAL;
        				}
        			} );
                
        	}
        	mLongCameraKeyPress = false;
        	result = true;
        }
        else if (keyCode == KEY_SWIPE_DOWN) {
        	android.media.AudioManager audio = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
        	audio.playSoundEffect(com.google.android.glass.media.Sounds.DISMISSED);
        	 
        	// there was a swipe down event
    		android.util.Log.d(TAG, "PreviewForegroundActivity.onKeyUp() - swipe down");
        	if (mState == STATE__STILL_CAPTURED_PENDING_USER_APPROVAL) {
        		this.mCapturedImageData = null;
    			mCamera.startPreview();
                
        		mState = STATE__NONE;
        	}
        	else if (mState == STATE__IN_OPTIONS_MENU) {
        		this.closeOptionsMenu();
        	}
        	else {
        		// by default handle a swipe down as activity exit
        		finish();
        	}
        	result = true;
        }
        else {
        	result = super.onKeyUp(keyCode, event);
        }

        
        return result;
    }
    
    private com.google.android.glass.touchpad.GestureDetector createGestureDetector(android.content.Context context) {
    	com.google.android.glass.touchpad.GestureDetector gestureDetector = new com.google.android.glass.touchpad.GestureDetector(context);
    	    //Create a base listener for generic gestures
    	    gestureDetector.setBaseListener( new com.google.android.glass.touchpad.GestureDetector.BaseListener() {
    	        @Override
    	        public boolean onGesture(com.google.android.glass.touchpad.Gesture gesture) {
    	            android.util.Log.d(TAG, gesture.name());
    	            if (gesture == com.google.android.glass.touchpad.Gesture.TAP) {
    	                // do something on tap
    	            	android.media.AudioManager audio = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
    	            	audio.playSoundEffect(com.google.android.glass.media.Sounds.TAP);

    	            	if (mState == STATE__STILL_CAPTURED_PENDING_USER_APPROVAL) {
    	        			saveCapturedImage();
    	        			mCamera.startPreview();
    	                    
    	            		mState = STATE__NONE;
    	            	}
    	            	else if (mState == STATE__VIDEO_CAPTURE_ONGOING) {
    	                    Intent broadcast = new Intent();
    	                    broadcast.setAction(PreviewService.CAPTURE_STOP_REQUESTED);
    	                    sendBroadcast(broadcast);
    	                    
    	            		mState = STATE__NONE;
    	            	}
    	            	else if (mPreviewIsZooming == true) {
    	            		mCamera.stopSmoothZoom();
    	            	}
    	            	else {
        	            	openOptionsMenu();
        	            	mState = STATE__IN_OPTIONS_MENU;
    	            	}
    	            	
    	                return true;
    	            } 
    	            else if (gesture == com.google.android.glass.touchpad.Gesture.LONG_PRESS) {
    	            	if (mPreviewIsZooming == true) {
    	            		mCamera.stopSmoothZoom();
        	            	return true;
    	            	}
    	            } 
    	            else if (gesture == com.google.android.glass.touchpad.Gesture.TWO_TAP) {
    	                // do something on two finger tap
    	                return true;
    	            } 
    	            else if (gesture == com.google.android.glass.touchpad.Gesture.SWIPE_RIGHT) {
    	            	mScrollVelocity = 0;
    	            } 
    	            else if (gesture == com.google.android.glass.touchpad.Gesture.SWIPE_LEFT) {
    	            	mScrollVelocity = 0;
    	            } 
    	            else if (gesture == com.google.android.glass.touchpad.Gesture.TWO_SWIPE_UP) {
    	            }
    	            else if (gesture == com.google.android.glass.touchpad.Gesture.TWO_SWIPE_DOWN) {
    	            }
    	            return false;
    	        }
    	    });
    	    gestureDetector.setFingerListener(new com.google.android.glass.touchpad.GestureDetector.FingerListener() {
    	        @Override
    	        public void onFingerCountChanged(int previousCount, int currentCount) {
    	            android.util.Log.d(TAG, "onFingerCountChanged(" + previousCount + "," + currentCount+ ")");
    	          // do something on finger count changes
    	        }
    	    });
    	    gestureDetector.setScrollListener(new com.google.android.glass.touchpad.GestureDetector.ScrollListener() {
    	        @Override
    	        public boolean onScroll(float displacement, float delta, float velocity) {
    	            // do something on scrolling
    	            //android.util.Log.d(TAG, "onScroll(" + displacement + "," + delta  + "," + velocity + ")");

    	            java.util.Date now = new java.util.Date();
    	            if (mLastZoomInvocationTime != null) {
    	            	//android.util.Log.d(TAG, "onScroll() - time since last zoom: " + (now.getTime() - mLastZoomInvocationTime.getTime()));
    	            }
        			if ( ( (mLastZoomInvocationTime == null)
    	                || ((now.getTime() - mLastZoomInvocationTime.getTime()) > 200) ) // wait at least 200 ms between zoom invocations; more frequent causes inexplicable crashes
    	              && ( (mScrollVelocity == 0)                  // if this is the first scroll velocity measured...
    	                || ((velocity * mScrollVelocity) < 0)      // ...or if the velocity is a different direction from the last measured velocity
    	        	    || ((velocity/mScrollVelocity) > 1.5f) ) ) // ... or the new velocity is significantly greater than the last measured velocity
    	            {
    	            	mLastZoomInvocationTime = now;
    	            	if (mPreviewIsZooming) {
    	            		mCamera.stopSmoothZoom();
    	            	}
    	            	else {
    	            		// we look for velocity numbers between about -20 and +20; normalize the value to be -1 to +1
    	            		float normalizedVelocity = java.lang.Math.min(java.lang.Math.max(velocity, -20), 20)/20.0f;
    	            
    	            		android.hardware.Camera.Parameters cameraParameters = mCamera.getParameters();
    	            		int currentZoomLevel = cameraParameters.getZoom(),
    	            		    maxZoomLevel = cameraParameters.getMaxZoom(),
    	            		    nextZoom = java.lang.Math.min(maxZoomLevel, java.lang.Math.max(currentZoomLevel+(int)(normalizedVelocity*maxZoomLevel), 0));
    	            		if (currentZoomLevel != nextZoom) {
    	            			android.util.Log.d(TAG, "onScroll() - current zoom = " + currentZoomLevel + ", zooming to " + nextZoom);
    	            			try {
    	            				mCamera.startSmoothZoom(nextZoom);
    	            			} catch (java.lang.RuntimeException re) {
    	            				android.util.Log.d(TAG, "onScroll() - startSmoothZoom failed");
    	            			}
    	            		}
    	            	}
    	            }
	            	mScrollVelocity = velocity;
	            	
	            	return true;
    	        }
    	    });
    	    return gestureDetector;
    	}
    
    /*
     * Send generic motion events to the gesture detector
     */
    @Override
    public boolean onGenericMotionEvent(android.view.MotionEvent event) {
    	boolean result = false;
        if (mGestureDetector != null) {
        	result = mGestureDetector.onMotionEvent(event);
        }
        return result;
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
 /*
    	    		android.graphics.Bitmap scaledBitmap = android.media.ThumbnailUtils.extractThumbnail(bitmap, 640, 360);
    	    		android.util.Log.d(TAG, "saveCapturedImage() -- image = " + bitmap.getWidth() + "x" + bitmap.getHeight());
    	    		android.util.Log.d(TAG, "saveCapturedImage() -- scaled image = " + scaledBitmap.getWidth() + "x" + scaledBitmap.getHeight());
    	    		
    	    		
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
    	            android.util.Log.d(TAG, "saveCapturedImage() -- saving image '" + f + "' to gallery");
    	    	    android.net.Uri contentUri = android.net.Uri.fromFile(f);
    	    	    mediaScanIntent.setData(contentUri);
    	    	    sendBroadcast(mediaScanIntent);

    	    	    com.google.android.glass.app.Card timelineCard = new com.google.android.glass.app.Card(PreviewForegroundActivity.this);
    	    	    android.net.Uri scaledImageUri = android.net.Uri.fromFile(scaledImageFile);
    	    	    timelineCard.setImageLayout(com.google.android.glass.app.Card.ImageLayout.FULL);
    	    	    timelineCard.addImage(scaledImageUri);
    	            android.util.Log.d(TAG, "saveCapturedImage() -- scaled image '" + scaledImageUri + "' added to timeline");
    	    	    //timelineCard.addImage(android.net.Uri.parse("http://photos4.meetupstatic.com/photos/member/a/6/e/2/member_147102722.jpeg"));
    	    	    timelineCard.setFootnote("" + mCapturedImageTimestamp);
    	    	    
    	    	    timelineCard.toView().setOnClickListener(
    	    	    	new View.OnClickListener() {
    	    	    		@Override
    	    	    		public void onClick(View v) {
    	    	    			android.util.Log.d(TAG, "timeline card clicked");
    	    	    		}
    	    	    	} );
    	    	    timelineCard.toView().setOnTouchListener(
    	    		    	new View.OnTouchListener() {
    	    		    		@Override
    	    		    		public boolean onTouch(View v, android.view.MotionEvent motionEvent) {
    	    		    			android.util.Log.d(TAG, "timeline card clicked");
    	    		    			return true;
    	    		    		}
    	    		    	} );
    	    	    
    	    	    mTimelineManager.insert(timelineCard); 
*/
    	    		saveBitmapToTimeline(bitmap, timeStamp);
    	       	} catch (java.lang.Exception exception) {
    				android.util.Log.d(TAG, "error saving captured image");
    	    	}
   			}
   		}).start();
    }

    private void saveBitmapToTimeline ( android.graphics.Bitmap bitmap,
    		                            String filenamePrefix )
    {
		try {
    		android.graphics.Bitmap scaledBitmap = android.media.ThumbnailUtils.extractThumbnail(bitmap, 640, 360);
    		android.util.Log.d(TAG, "saveBitmapToTimeline() -- image = " + bitmap.getWidth() + "x" + bitmap.getHeight());
    		android.util.Log.d(TAG, "saveBitmapToTimeline() -- scaled image = " + scaledBitmap.getWidth() + "x" + scaledBitmap.getHeight());
    		
    		
    		String scaledImageFileName = filenamePrefix + "_SCALED_";
    		java.io.File scaledImageFile = java.io.File.createTempFile(
    				scaledImageFileName,  // prefix
        		".jpg",         // suffix
        		android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES) );   // directory
    		java.io.FileOutputStream scaledImageStream = (new java.io.FileOutputStream(scaledImageFile));
    		scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, // Bitmap.CompressFormat format -- The format of the compressed image; e.g., JPEG, PNG, WEBP 
    				              50, // int quality -- Hint to the compressor, 0-100. 0 meaning compress for small size, 100 meaning compress for max quality.
    				              scaledImageStream ); // OutputStream stream -- The outputstream to write the compressed data.
    		scaledImageStream.close();
    		
    		/*
    		// add the image to the device gallery
    	    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
    	    java.io.File f = new java.io.File("file:" + imageFile.getAbsolutePath());
            android.util.Log.d(TAG, "saveCapturedImage() -- saving image '" + f + "' to gallery");
    	    android.net.Uri contentUri = android.net.Uri.fromFile(f);
    	    mediaScanIntent.setData(contentUri);
    	    sendBroadcast(mediaScanIntent);
    	    */

    	    com.google.android.glass.app.Card timelineCard = new com.google.android.glass.app.Card(PreviewForegroundActivity.this);
    	    android.net.Uri scaledImageUri = android.net.Uri.fromFile(scaledImageFile);
    	    timelineCard.setImageLayout(com.google.android.glass.app.Card.ImageLayout.FULL);
    	    timelineCard.addImage(scaledImageUri);
            android.util.Log.d(TAG, "saveCapturedImage() -- scaled image '" + scaledImageUri + "' added to timeline");
    	    //timelineCard.addImage(android.net.Uri.parse("http://photos4.meetupstatic.com/photos/member/a/6/e/2/member_147102722.jpeg"));
    	    timelineCard.setFootnote("" + mCapturedImageTimestamp);
    	    
    	    timelineCard.toView().setOnClickListener(
    	    	new View.OnClickListener() {
    	    		@Override
    	    		public void onClick(View v) {
    	    			android.util.Log.d(TAG, "timeline card clicked");
    	    		}
    	    	} );
    	    timelineCard.toView().setOnTouchListener(
    		    	new View.OnTouchListener() {
    		    		@Override
    		    		public boolean onTouch(View v, android.view.MotionEvent motionEvent) {
    		    			android.util.Log.d(TAG, "timeline card clicked");
    		    			return true;
    		    		}
    		    	} );
    	    
    	    mTimelineManager.insert(timelineCard);    				
       	} catch (java.lang.Exception exception) {
			android.util.Log.d(TAG, "error saving captured image");
    	}
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
        mCamera.unlock();     
        
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

        	mMediaRecorder.setCamera(mCamera);
        	mMediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
        	mMediaRecorder.setVideoSource(android.media.MediaRecorder.VideoSource.CAMERA);
        	android.util.Log.i(TAG, "a");

        	mMediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
        	mMediaRecorder.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.MPEG_4_SP);
        	mMediaRecorder.setVideoEncodingBitRate(5000000); // 0x4c4b40 = 5000000
            mMediaRecorder.setVideoFrameRate(30);
            mMediaRecorder.setVideoSize(1280, 720);//640, 360);
            
            mMediaRecorder.setAudioChannels(2);
        	mMediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setAudioEncodingBitRate(96000); // 0x17700 = 96000
            mMediaRecorder.setAudioSamplingRate(44100); // 0xac44 = 44100
            android.util.Log.i(TAG, "b");

            mMediaRecorder.setMaxDuration(30000); // set to 20000

            android.util.Log.i(TAG, "setting output file to '" + videoFile.getAbsolutePath() + "'");
            mMediaRecorder.setOutputFile(videoFile.getAbsolutePath());
            android.util.Log.i(TAG, "c");

        	// Step 5: Set the preview output
//            mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
            //mMediaRecorder.setMaxFileSize(50000); // set to 50000

            // Step 6: Prepare configured MediaRecorder
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
    

    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if ( ((requestCode == REQUEST_VIDEO_CAPTURE) || (requestCode == REQUEST_IMAGE_CAPTURE))
          && (resultCode == RESULT_OK) )
        {
            android.util.Log.d(TAG, "onActivityResult(" + ((requestCode == REQUEST_VIDEO_CAPTURE) ? "REQUEST_VIDEO_CAPTURE" : "REQUEST_IMAGE_CAPTURE") + ", RESULT_OK)");

            final String filePath = intent.getStringExtra(com.google.android.glass.media.CameraManager.EXTRA_VIDEO_FILE_PATH);
            final String screenshotPath = intent.getStringExtra(com.google.android.glass.media.CameraManager.EXTRA_THUMBNAIL_FILE_PATH);
            android.util.Log.d(TAG, "onActivityResult() - Camera video location : " + filePath);
            android.util.Log.d(TAG, "onActivityResult() - Camera screenshot location : " + screenshotPath);

            java.io.File videoFile = new java.io.File(filePath);
            if (videoFile.exists()) {
                addVideoThumbnailToTimeline(filePath);
            }
            else {
                Log.d(TAG, "onActivityResult() - Waiting for screenshot file to be written.");
                // wait for file to be written, then do stuff
                android.os.FileObserver fileObs = new android.os.FileObserver(videoFile.getParentFile().getAbsolutePath(), android.os.FileObserver.CLOSE_WRITE)
                	{
                		@Override
                		public void onEvent(int event, String path) {
                			//doStuff(filePath, screenshotPath);
                            android.util.Log.d(TAG, "onActivityResult() - video file written");
                            addVideoThumbnailToTimeline(filePath);
                        }
                	};
                fileObs.startWatching();
            }

        }
    }
    
    private void addVideoThumbnailToTimeline(final String filePath)
    {
    	android.graphics.Bitmap videoThumbnail = android.media.ThumbnailUtils.createVideoThumbnail(filePath, android.provider.MediaStore.Images.Thumbnails.MINI_KIND);
    	this.saveBitmapToTimeline(videoThumbnail, (new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")).format(this.mCapturedImageTimestamp));
    }
    
    private java.util.Hashtable saveCameraParameters(android.hardware.Camera.Parameters params) {
    	java.util.Hashtable result = new java.util.Hashtable();
    	
    	java.lang.Class paramsClass = android.hardware.Camera.Parameters.class;
    	java.lang.reflect.Method[] method = paramsClass.getMethods();
    	for (int i=0; i<method.length; i++) {
    		String methodName = method[i].getName();
    		if ( methodName.startsWith("get")
    		  && (method[i].getParameterTypes().length == 0) )
    		{
    			try {
    				Object methodResult = method[i].invoke(params, null);
    				String propertyName = methodName.substring(3);
                    //Log.d(TAG, "saveCameraParameters() - '" + propertyName + "' = " + methodResult);
    				result.put(propertyName, methodResult);
    			} catch (java.lang.Exception e) {}
    		}
    	}
    	
    	return result;
    }
    
    private void loadCameraParameters(android.hardware.Camera.Parameters params, java.util.Hashtable paramValues) {
    	java.lang.Class paramsClass = android.hardware.Camera.Parameters.class;
    	
    	java.lang.reflect.Method[] method = paramsClass.getMethods();
    	for (int i=0; i<method.length; i++) {
    		String methodName = method[i].getName();
    		if ( methodName.startsWith("set")
    	      && (method[i].getParameterTypes().length == 1) )
    	    {
    			String propertyName = methodName.substring(3);
    			if (paramValues.containsKey(propertyName)) {
    	    		try {
    	    			Object paramValue = paramValues.get(propertyName);
	                    //Log.d(TAG, "loadCameraParameters() - '" + propertyName + "' = " + paramValue);
    	    			method[i].invoke(params, paramValue);
    	    		} catch (java.lang.Exception e) {
	                    //Log.d(TAG, "loadCameraParameters() - error saving property '" + propertyName + "' : " + e);
    	    		}
    			}
    	    }
    	}    	
    }

}
