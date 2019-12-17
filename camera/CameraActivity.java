package com.predixtor.beePic.ui.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;

import com.bumptech.glide.Glide;
import com.predixtor.beePic.R;
import com.predixtor.beePic.data.database.camera.MyCamera;
import com.predixtor.beePic.data.database.mediafile.MediaFile;
import com.predixtor.beePic.data.storage.DeviceStorage;
import com.predixtor.beePic.ui.media_file_display.MediaFileDisplay;
import com.predixtor.beePic.utils.DatabaseOperations;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static android.view.OrientationEventListener.ORIENTATION_UNKNOWN;
import static com.predixtor.beePic.utils.CreateAudioFileKt.createAudioFile;

public class CameraActivity extends AppCompatActivity {


    // Camera used to start image capture settings, start/stop preview, snap pictures, and retrieve
    // frames for encoding for video.
    private Camera mCamera;
    private Camera.Parameters mCameraParameters;
    private int mCameraFlashState = 0;

    // Customize object for saving camera states in db
    private MyCamera mMyCamera;

    // Instance of CameraPreview class
    private CameraPreview mPreview;

    // Constants representing the cameras of the Android device
    private final int CAMERA_FACING_FRONT_ID = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private final int CAMERA_FACING_BACK_ID = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int mCameraId;

    // Camera's action buttons
    private ImageButton mSwitchCameraButton;
    private ImageButton mTakePhotoButton;
    private ImageButton mRecordVideoButton;
    private ImageButton mFlashButton;

    // Activity layouts
    private FrameLayout mPreviewLayout;

    // Video's chronometer
    private Chronometer mChronometer;

    // Saves taken picture
    private Camera.PictureCallback mPicture;

    {
        mPicture = new Camera.PictureCallback() {

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {

                TakePhotoAsyncTaks at = new TakePhotoAsyncTaks();
                at.execute(data);

            }
        };
    }

    private class TakePhotoAsyncTaks extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... bytes) {

            mImageFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            String TAG = "TakePhoto";

            try {
                // Save picture
                FileOutputStream fos = new FileOutputStream(mImageFile);
                fos.write(bytes[0]);
                fos.close();

            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            // Add photo to SQL Lite database
            addMediaFileToDb(MEDIA_TYPE_IMAGE);

            // Re-start camera preview after picture is taken
            mCamera.startPreview();

            // Update pictuare preview (located at bottom right of screen)
            setPreviewThumbnail(mImageFile);

            mOpenMediaFileIntent = getOpenMediaFileIntent(mImageFile, mFolder);

            // Enables activity buttons after taking photo
            enableOrDisableButtons(false);

            // Shows file in the gallery of the device
            sendBroadcast(new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                    Uri.fromFile(new File(mImageFile.getAbsolutePath()))));
        }
    }

    private ImageView mPicturePreview;

    // Tracks the orientation of the device
    private OrientationEventListener mOrientationEventListener;
    private int mDeviceOrientation = 0;

    // Detects zoom gesture
    private ScaleGestureDetector mScaleGestureDetector;

    // View model for the activity
    private CameraActivityViewModel mViewModel;

    // Path of the folder where photo must be saved
    private String mFolderPath;
    private File mFolder;
    private File mImageFile;
    private File mVideoFile;

    private MediaRecorder mMediaRecorder;

    // Keeps recording state
    private boolean mIsRecording = false;

    // Constants for multimedia files
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;

    // Media file extensions
    private static final String IMAGE_FILE_EXTENSION = DeviceStorage.IMAGE_FILE_EXTENSION[0];
    private static final String VIDEO_FILE_EXTENSION = DeviceStorage.VIDEO_FILE_EXTENSION;

    // Intent to open media file from preview view located at the bottom right corner of the activity
    private Intent mOpenMediaFileIntent;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private static int COUNT_OF_FILES = 0;

    public static final String WORD_FILE_EXTENSION = DeviceStorage.WORD_FILE_EXTENSION;

    private DatabaseOperations mediaFileDatabase;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        makeActivityFullScreen();

        setContentView(R.layout.activity_camera);

        // Initializing object members
        mSwitchCameraButton = findViewById(R.id.image_button_switch_camera);
        mTakePhotoButton = findViewById(R.id.image_button_camera_icon);
        mRecordVideoButton = findViewById(R.id.image_view_video_icon);
        mFlashButton = findViewById(R.id.image_button_camera_flash);
        mChronometer = findViewById(R.id.video_chronometer);
        mPreviewLayout = findViewById(R.id.camera_preview);
        mCameraId = CAMERA_FACING_BACK_ID;
        mViewModel = ViewModelProviders.of(this).get(CameraActivityViewModel.class);
        mFolderPath = getIntent().getExtras().getString("subFolderPath");
        mPicturePreview = findViewById(R.id.image_view_picture_preview);
        mediaFileDatabase = new DatabaseOperations(getApplicationContext());


        // Gets device orientation and use it to start camera orientation
        setOrientationEventListener();

        // Creates camera instance and start it up
        setCamera();

        // Creates and sets camera preview
        setPreview();

        // Listeners for the action buttons
        mSwitchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SwitchCameraAsynTask switchCameraAsynTask;
                switchCameraAsynTask = new SwitchCameraAsynTask();
                switchCameraAsynTask.execute();
            }
        });
        mFlashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCameraFlashState = mCameraFlashState + 1;
                if (mCameraFlashState > 2) {
                    mCameraFlashState = 0;
                }
                setCameraFlash();
            }
        });
        mTakePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (evaluateTrialVersion()) {
                    showTrialVersionPopupWindow();
                    return;
                }

                try {
                    // Disables activity buttons while taking photo
                    enableOrDisableButtons(true);
                    mCamera.takePicture(null, null, mPicture);
                } catch (Exception e) {
                    enableOrDisableButtons(false);
                }
            }
        });
        mRecordVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (evaluateTrialVersion() && !mIsRecording) {
                    showTrialVersionPopupWindow();
                    return;
                }

                if (!checkRecordingPermission()) {
                    requestRecordingPermissions();
                    return;
                }

                if (mIsRecording) {

                    // stop recording and release camera
                    mMediaRecorder.stop();  // stop the recording
                    releaseMediaRecorder(); // release the MediaRecorder object
                    mCamera.lock();         // take camera access back from MediaRecorder

                    // Informs the user that recording has stopped
                    setChronometer(mIsRecording);
                    mRecordVideoButton.setImageResource(R.mipmap.video_icon);

                    mIsRecording = false;

                    // Updates video preview (located at the bottom right of screen)
                    setPreviewThumbnail(mVideoFile);

                    // Adds media file to the SQL Lite database
                    addMediaFileToDb(MEDIA_TYPE_VIDEO);

                    mOpenMediaFileIntent = getOpenMediaFileIntent(mVideoFile, mFolder);

                    // Shows file in the gallery of the device
                    sendBroadcast(new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                            Uri.fromFile(new File(mVideoFile.getAbsolutePath()))));

                } else {
                    // initialize video camera
                    if (prepareVideoRecorder()) {

                        // Camera is available and unlocked, MediaRecorder is prepared,
                        // now you can start recording
                        mMediaRecorder.start();

                        // inform the user that recording has started
                        setChronometer(mIsRecording);
                        mRecordVideoButton.setImageResource(R.mipmap.video_icon_busy);

                        mIsRecording = true;
                    } else {
                        // prepare didn't work, release the camera
                        releaseMediaRecorder();
                    }
                }

                disableUiElementsWhileRecording();
            }
        });
        mPicturePreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Opens media file shown in preview (small image at the bottom right of the activity)
                startActivity(mOpenMediaFileIntent);
            }
        });
        mPreviewLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mScaleGestureDetector.onTouchEvent(motionEvent);
                return true;
            }
        });
        mScaleGestureDetector = new ScaleGestureDetector(getApplicationContext(),
                new ScaleGestureDetector.OnScaleGestureListener() {

                    float cumulativeScaleFactor = 0.0f;
                    float zoom_float;
                    float scaleFactor;
                    int zoom_int;

                    @Override
                    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {

                        // Sets zoom of camera
                        Camera.Parameters camParams = mCamera.getParameters();
                        boolean isZoomSupported = camParams.isZoomSupported();

                        if (isZoomSupported) {

                            scaleFactor = scaleGestureDetector.getScaleFactor();
                            int maxZoom = camParams.getMaxZoom();
                            cumulativeScaleFactor = cumulativeScaleFactor + (scaleFactor - 1.0f);
                            zoom_float = cumulativeScaleFactor * maxZoom;

                            if (zoom_float >= maxZoom) {
                                zoom_float = (float) maxZoom;
                                cumulativeScaleFactor = 1f;
                            } else if (zoom_float <= 0) {
                                zoom_float = 0f;
                                cumulativeScaleFactor = 0f;
                            }

                            zoom_int = (int) zoom_float;
                            camParams.setZoom(zoom_int);
                            mCamera.setParameters(camParams);
                        }
                        return true;
                    }

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
                    }
                });

        test();

    }

    //----------------------------------------------------------------------------------------------
    private void requestRecordingPermissions() {
        if (!isPlatformLessThan23()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    //----------------------------------------------------------------------------------------------
    private boolean isPlatformLessThan23() {
        return Build.VERSION.SDK_INT < 23;
    }

    //----------------------------------------------------------------------------------------------
    private boolean checkRecordingPermission() {
        if (!isPlatformLessThan23()) {
            if (!(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.
                    PERMISSION_GRANTED)) {
                return false;
            }
        }
        return true;
    }

    //----------------------------------------------------------------------------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(),
                            getString(R.string.record_permission_conceded),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(),
                            getString(R.string.audio_record_camera_rationale),
                            Toast.LENGTH_LONG).show();
                }
        }
    }

    //----------------------------------------------------------------------------------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaRecorder();
        releaseCamera();
        mOrientationEventListener.disable();
    }

    //----------------------------------------------------------------------------------------------
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    //----------------------------------------------------------------------------------------------
    private Intent getOpenMediaFileIntent(File mediaFile, File folder) {

        Intent openMediaFileIntent = new Intent(CameraActivity.this, MediaFileDisplay.class);
        openMediaFileIntent.putExtra("filePath", mediaFile.getAbsolutePath());
        openMediaFileIntent.putExtra("rootFile", folder.getAbsolutePath());

        return openMediaFileIntent;
    }

    //----------------------------------------------------------------------------------------------
    private void setPreviewThumbnail(File mediaFile) {
        // Updates pictuare preview (located at bottom right of screen)
        Glide.with(CameraActivity.this).load(mediaFile).centerCrop()
                .into(mPicturePreview);
        if (mPicturePreview.getVisibility() == View.GONE) {
            mPicturePreview.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Creates a Toast
     *
     * @param text string that is shown by the Toast
     */
    private void getToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /**
     * Check if device has a camera
     */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    private static Camera getCameraInstance(int cameraId) {
        Camera c = null;
        try {
            c = Camera.open(cameraId); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            e.printStackTrace();
        }
        return c; // returns null if camera is unavailable
    }

    /**
     * Gets camera instance and sets up basic camera parameters
     */
    private void setCamera() {
        mCamera = getCameraInstance(mCameraId);
        mCameraParameters = mCamera.getParameters();

        boolean isCameraInDB;

        // for android versions 8 and 9 it is appearing and room persistance error related to
        // data integrity, it does not happend in earlier versions of BeePic. The solution
        // is to delete user data if the problem appears, this deletes sql. It is put just in the
        // camera activy not in subfolder activity to avoid losing data of users that already have
        // info
        try {
            isCameraInDB = mViewModel.isMyCameraAddedToDb(mCameraId);
        } catch (IllegalStateException e) {

/*            ((ActivityManager)getApplicationContext().getSystemService(ACTIVITY_SERVICE))
                    .clearApplicationUserData();*/

            isCameraInDB = mViewModel.isMyCameraAddedToDb(mCameraId);
            e.printStackTrace();
        }

        // Sets selected camera in database
        if (isCameraInDB) {
            mMyCamera = mViewModel.getMyCameraFromDb(mCameraId);
            mCameraFlashState = mMyCamera.getFlashState();
        } else {
            mMyCamera = new MyCamera(mCameraId, mCameraFlashState);
            mViewModel.InsertMyCamerainDb(mMyCamera);
        }

        setCameraOrientation();
        setCameraFlash();
        setPictureSize();
        try {
            setCameraFocus(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates preview and sets it as the ui content of the activity.
     */
    private void setPreview() {
        mPreview = new CameraPreview(this, mCamera);
        try {
            mPreviewLayout.removeAllViews();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mPreviewLayout.addView(mPreview);
    }

    /**
     * Starts a chronometer when the camera is recording a video and makes is visible.
     *
     * @param isRecording indicates if the camera is recording a video
     */
    private void setChronometer(boolean isRecording) {
        if (isRecording) {
            mChronometer.stop();
            mChronometer.setVisibility(View.INVISIBLE);
        } else {
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.setVisibility(View.VISIBLE);
            mChronometer.start();
        }
    }

    //----------------------------------------------------------------------------------------------
    public int getCameraId() {
        return mCameraId;
    }

    //----------------------------------------------------------------------------------------------
    private void makeActivityFullScreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    //----------------------------------------------------------------------------------------------
    //Rotates camera to match the orientation of the device
    private void setCameraOrientation() {

        int rotation = getCameraRotation(mDeviceOrientation);
        mCameraParameters.setRotation(rotation);
        mCamera.setParameters(mCameraParameters);

    }

    private int getCameraRotation(int deviceOrientation) {
        if (deviceOrientation == ORIENTATION_UNKNOWN) return 0;
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(getCameraId(), info);

        int rotation;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - deviceOrientation + 360) % 360;
        } else {  // back-facing camera
            rotation = (info.orientation + deviceOrientation) % 360;
        }

        return rotation;
    }

    /**
     * Gets device orientation and use it to start camera orientation
     */
    private void setOrientationEventListener() {

        mOrientationEventListener = new OrientationEventListener(CameraActivity.this) {

            @Override
            public void onOrientationChanged(int i) {

                // Reduces variability of orientation
                int rotation;
                if (i >= 45 && i < 135) {
                    rotation = 90;
                } else if (i >= 135 && i < 225) {
                    rotation = 180;
                } else if (i >= 225 && i < 315) {
                    rotation = 270;
                } else {
                    rotation = 0;
                }

                if (rotation != mDeviceOrientation) {
                    mDeviceOrientation = rotation;
                    setCameraOrientation();
                    setPictureSize();
                    setPreviewSize();
                    try {
                        setCameraFocus(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        String TAG = "orientationList";
        if (mOrientationEventListener.canDetectOrientation()) {
            Log.v(TAG, "Can detect orientation");
            mOrientationEventListener.enable();
        } else {
            Log.v(TAG, "Cannot detect orientation");
            mOrientationEventListener.disable();
        }
    }

    //----------------------------------------------------------------------------------------------
    private class SwitchCameraAsynTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            // stop preview before making changes
            try {
                mCamera.stopPreview();
                mCamera.release();
            } catch (Exception e) {
                // ignore: tried to stop a non-existent preview
                e.printStackTrace();
            }
            switch (mCameraId) {
                case CAMERA_FACING_BACK_ID:
                    mCameraId = CAMERA_FACING_FRONT_ID;
                    break;
                case CAMERA_FACING_FRONT_ID:
                    mCameraId = CAMERA_FACING_BACK_ID;
                    break;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (mCameraId == CAMERA_FACING_FRONT_ID) {
                mSwitchCameraButton.setImageResource(R.drawable.switch_to_back_camera);
            } else if (mCameraId == CAMERA_FACING_BACK_ID) {
                mSwitchCameraButton.setImageResource(R.drawable.switch_to_frontal_camera);
            }

            setCamera();
            setPreview();
        }
    }

    //----------------------------------------------------------------------------------------------
    private void setCameraFlash() {

        if (hasFlash()) {
            switch (mCameraFlashState) {
                case 0:
                    mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                    mFlashButton.setImageResource(R.drawable.flash_auto_icon);
                    break;
                case 1:
                    mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    mFlashButton.setImageResource(R.drawable.flash_on_icon);
                    break;
                case 2:
                    mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    mFlashButton.setImageResource(R.drawable.flash_off_icon);
                    break;
            }
            mCamera.setParameters(mCameraParameters);
            mMyCamera.setFlashState(mCameraFlashState);
            mViewModel.updateMyCameraInDb(mMyCamera);
        } else {
            mFlashButton.setImageResource(R.drawable.flash_off_icon);
        }

    }

    //----------------------------------------------------------------------------------------------
    private boolean verifyDimensionsAccordingToRotation(int width, int height) {
        if (mDeviceOrientation == 0 || mDeviceOrientation == 180) {
            return width > height;
        } else {
            return width < height;
        }
    }

    //----------------------------------------------------------------------------------------------
    private Camera.Size getOptimalPictureSize() {

        Camera.Parameters camParams = mCamera.getParameters();

        //------------------------------------------------------------------------------------------
        Display display = getWindowManager().getDefaultDisplay();
        Point displaySize = new Point();
        display.getSize(displaySize);
        int displayWidth = displaySize.x;
        int displayHeight = displaySize.y;

        //------------------------------------------------------------------------------------------
        if (verifyDimensionsAccordingToRotation(displayWidth, displayHeight)) {
            displayWidth = displaySize.y;
            displayHeight = displaySize.x;
        }
        float targetRatio = (float) displayWidth / displayHeight;

        //------------------------------------------------------------------------------------------
        List<Camera.Size> cameraSizes;
        cameraSizes = camParams.getSupportedPictureSizes();

        if (cameraSizes == null) {
            return null;
        }
        // sort available picture sizes from highest to lowest
        Collections.sort(cameraSizes, new Comparator<Camera.Size>() {
            public int compare(Camera.Size a, Camera.Size b) {
                return b.width * b.height - a.width * a.height;
            }
        });

        //------------------------------------------------------------------------------------------
        float targetRatioDifference = 0.0f;
        while (targetRatioDifference < 1) {
            for (Camera.Size size : cameraSizes) {
                int width = size.width;
                int height = size.height;
                if (verifyDimensionsAccordingToRotation(width, height)) {
                    width = size.height;
                    height = size.width;
                }
                float currentRatio = (float) width / height;
                float difference = Math.abs(currentRatio - targetRatio);
                if ((difference <= targetRatioDifference) &&
                        (width >= displayWidth) && (height >= displayHeight)) {
                    return size;
                }
            }
            targetRatioDifference += 0.1;
        }
        return cameraSizes.get(cameraSizes.size() - 1);
    }

    //----------------------------------------------------------------------------------------------
    private void setPictureSize() {
        Camera.Size optimalSize = getOptimalPictureSize();
        if (optimalSize != null) {
            Camera.Parameters camParameters = mCamera.getParameters();
            camParameters.setPictureSize(optimalSize.width, optimalSize.height);
            mCamera.setParameters(camParameters);
        }
    }

    //----------------------------------------------------------------------------------------------
    private void setPreviewSize() {

        //------------------------------------------------------------------------------------------
        Display display = getWindowManager().getDefaultDisplay();
        Point displaySize = new Point();
        display.getSize(displaySize);
        int w = displaySize.x;
        int h = displaySize.y;

        Camera.Parameters camParams = mCamera.getParameters();
        camParams.setPreviewSize(w, h);
        if (h > w) {
            camParams.setPreviewSize(h, w);
        }

        try {
            mCamera.setParameters(camParams);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //----------------------------------------------------------------------------------------------
    private boolean hasFlash() {
        List<String> flashModes = mCameraParameters.getSupportedFlashModes();
        if (flashModes == null) {
            return false;
        }

        for (String flashMode : flashModes) {
            if (Camera.Parameters.FLASH_MODE_ON.equals(flashMode)) {
                return true;
            }
        }

        return false;
    }

    //----------------------------------------------------------------------------------------------
    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    //----------------------------------------------------------------------------------------------
    private File getMediaFilesFolder(String folderPath) {

        File folder = new File(folderPath);
        try {
            if (!folder.exists()) {
                folder.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return folder;
    }


    //----------------------------------------------------------------------------------------------
    private File getOutputMediaFile(int type) {

        mFolder = getMediaFilesFolder(mFolderPath);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        File mediaFile;

        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mFolder.getPath() + File.separator +
                    "IMG_" + timeStamp + "." + IMAGE_FILE_EXTENSION);
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mFolder.getPath() + File.separator +
                    "VID_" + timeStamp + "." + VIDEO_FILE_EXTENSION);
        } else {
            return null;
        }

        return mediaFile;
    }


    //----------------------------------------------------------------------------------------------
    private boolean prepareVideoRecorder() {

        String TAG = "prepareVideo";

        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and start camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        mVideoFile = getOutputMediaFile(MEDIA_TYPE_VIDEO);
        mMediaRecorder.setOutputFile(mVideoFile.toString());

        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        mMediaRecorder.setOrientationHint(getCameraRotation(mDeviceOrientation));

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();

        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }

        return true;
    }

    //----------------------------------------------------------------------------------------------
    private void enableOrDisableButtons(boolean isDisable) {

        if (isDisable) {
            mTakePhotoButton.setClickable(false);
            mRecordVideoButton.setClickable(false);
            mSwitchCameraButton.setClickable(false);
            mFlashButton.setClickable(false);
            mPicturePreview.setClickable(false);
        } else {
            mTakePhotoButton.setClickable(true);
            mRecordVideoButton.setClickable(true);
            mSwitchCameraButton.setClickable(true);
            mFlashButton.setClickable(true);
            mPicturePreview.setClickable(true);
        }
    }

    //----------------------------------------------------------------------------------------------
    private void disableUiElementsWhileRecording() {
        if (mIsRecording) {
            mSwitchCameraButton.setEnabled(false);
        } else {
            mSwitchCameraButton.setEnabled(true);
        }

    }

    private void addMediaFileToDb(int fileType) {

        // -----------------------------------------------------------------------------------------
        String name = null;
        String audioPath = null;
        String textNote = "";
        String filePath = "";

        //------------------------------------------------------------------------------------------
        if (fileType == MEDIA_TYPE_IMAGE) {
            name = mImageFile.getName();
            File audioFile = createAudioFile(mImageFile.getAbsolutePath());
            audioPath = audioFile.getAbsolutePath();
            filePath = mImageFile.getAbsolutePath();
        } else if (fileType == MEDIA_TYPE_VIDEO) {
            name = mVideoFile.getName();
            filePath = mVideoFile.getAbsolutePath();
        }

        //------------------------------------------------------------------------------------------
        MediaFile mediaFile;
        mediaFile = new MediaFile(Objects.requireNonNull(name), audioPath,
                textNote, false, filePath, false, new File(filePath).getParent());
        mediaFileDatabase.insertMediaFileInDB(mediaFile);
    }

    //----------------------------------------------------------------------------------------------
    private void setCameraFocus(String mParameter) {
        Camera.Parameters mParameters = mCamera.getParameters();
        mParameters.setFocusMode(mParameter);
        mCamera.setParameters(mParameters);
    }

    //----------------------------------------------------------------------------------------------
    private void test() {
        evaluateTrialVersion();
    }

    //----------------------------------------------------------------------------------------------
    private boolean evaluateTrialVersion() {
        File appRootFolder = new DeviceStorage().getAppRootFolder();
        COUNT_OF_FILES = 0;
        countFilesRecursive(appRootFolder.getAbsolutePath());
        if (COUNT_OF_FILES >= 50) {
            return true;
        }
        return false;
    }

    //----------------------------------------------------------------------------------------------
    private void countFilesRecursive(String folderPath) {
        File file = new File(folderPath);
        String fileName = file.getName();
        if (fileName.equals(DeviceStorage.TEMP_FOLDER_NAME) || fileName.equals(DeviceStorage.AUDIO_FOLDER_NAME)) {
            return;
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File f : files) {
                countFilesRecursive(f.getAbsolutePath());
            }
        } else {
            String fileExtension = FilenameUtils.getExtension(file.getName());
            if (!fileExtension.equals(WORD_FILE_EXTENSION)) {
                COUNT_OF_FILES += 1;
            }
        }
    }

    //----------------------------------------------------------------------------------------------
    private void showTrialVersionPopupWindow() {

        LayoutInflater inflater = (LayoutInflater) getApplicationContext()
                .getSystemService(LAYOUT_INFLATER_SERVICE);

        View customView = inflater.inflate(R.layout.popup_trial_version, null);

        RelativeLayout adquireButton = (RelativeLayout) customView.findViewById(R.id.adquire_button);
        RelativeLayout cancelButton = (RelativeLayout) customView.findViewById(R.id.cancel_button);
        RelativeLayout container = (RelativeLayout) findViewById(R.id.camera_view_container);

        // Initialize a new instance of popup window
        final PopupWindow popupWindow = new PopupWindow(customView, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        // Set an elevation value for popup window
        if (Build.VERSION.SDK_INT >= 21) {
            popupWindow.setElevation(5.0f);
        }

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
            }
        });

        adquireButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://play.google.com/store/apps/details?id=com.predixtor.beePic";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        // Finally, show the popup window at the center location of root relative layout
        popupWindow.showAtLocation(container, Gravity.CENTER, 0, 0);
    }

}
