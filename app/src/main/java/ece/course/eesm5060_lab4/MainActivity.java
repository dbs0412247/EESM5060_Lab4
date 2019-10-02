package ece.course.eesm5060_lab4;

import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.AccessNetworkConstants;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import ece.course.android.module.NetworkConnectionServer;

public class MainActivity extends AppCompatActivity {

    private static String TAG_DEBUG = "MainActivity";
    private static int APPS_PORT = 1234;
    private static byte MSG_PICTURE_CAPTURE = (byte)'P';
    private static byte MSG_VIDEO_RECORDING_START = (byte)'V';
    private static byte MSG_VIDEO_RECORDING_STOP = (byte)'S';

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private CameraPreviewView mCameraPreviewView;
    private Camera mCamera;
    private int mCameraId;
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecording = false;
    private WifiManager mWifiManager;
    private NetworkConnectionServer mServer;
    private TextView tvState;
    private TextView tvIp;

    private final Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            try {
                File pictureFile = getOutputFile(true);
                FileOutputStream fileOutputStream = new FileOutputStream(pictureFile);
                fileOutputStream.write(data);
                fileOutputStream.close();
            } catch (Exception ex) {
                System.out.println(ex.toString());
            }
        }
    };

    private String getIpString(int ipInteger) {
        return (ipInteger & 0xFF) + "." + ((ipInteger >> 8) & 0xFF) + "."
                + ((ipInteger >> 16) & 0xFF) + "." + ((ipInteger >> 24) & 0xFF);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Toast.makeText(this, "No Camara On The Phone Leaving...",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        int numberOfCameras = Camera.getNumberOfCameras();
        boolean hvBackCamera = false;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = i;
                hvBackCamera = true;
                break;
            }
        }
        if (!hvBackCamera) {
            Toast.makeText(this, "The Apps only support the back Camara, Leaving...",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getClass().getName());
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (mWifiManager == null) {
            Toast.makeText(this, "There is no wifi Module in the phone, leave...",
                    Toast.LENGTH_LONG).show();
            finish();
        }
        int ipInteger = mWifiManager.getConnectionInfo().getIpAddress();
        mCameraPreviewView = (CameraPreviewView) findViewById(R.id.camview);
        tvState = (TextView) findViewById(R.id.state);
        tvState.setText("Not Connected");
        tvIp = (TextView) findViewById(R.id.ip);
        tvIp.setText(getIpString(ipInteger));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWakeLock.acquire();
        mCamera = Camera.open(mCameraId);
        mCameraPreviewView.setCamera(mCamera);
    }

    @Override
    protected void onPause() {
        mWakeLock.release();
        if (mCamera != null) {
            mCameraPreviewView.setCamera(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        if(mServer != null){
            mServer.interrupt();
        }
        tvState.setText("Not Connected");
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuTakePicture :
                takePicture();
                return true;
            case R.id.menuRecordVideo :
                recordVideo();
                return true;
            case R.id.menuStopRecord :
                stopRecord();
                return true;
        }
        return false;
    }

    private void takePicture() {
        mCamera.takePicture(null, null, mPictureCallback);
    }

    private void recordVideo() {
        if (mIsRecording)
            return;
        if (prepareVideoRecorder()) {
            mMediaRecorder.start();
            mIsRecording = true;
        } else {
            releaseMediaRecorder();
        }
    }

    private void stopRecord() {
        if (!mIsRecording)
            return;
        mMediaRecorder.stop();
        releaseMediaRecorder();
        mCamera.lock();
        mCamera.stopPreview();
        mCamera.startPreview();
        mIsRecording = false;
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            mCamera.lock();
        }
    }

    private boolean prepareVideoRecorder() {
        mMediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        File mediaFile = getOutputFile(false);
        mMediaRecorder.setOutputFile(mediaFile.toString());
        mMediaRecorder.setPreviewDisplay(mCameraPreviewView.getHolder().getSurface());
        try {
            mMediaRecorder.prepare();
            return true;
        } catch (Exception exception) { }
        releaseMediaRecorder();
        return false;
    }

    private File getOutputFile(boolean isPicture) {
        File storageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "USTECE_Lab_4_2");
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.d(TAG_DEBUG, "failed to create directory");
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        if (isPicture) {
            return new File(storageDir.getPath() + File.separator
                    + "IMG_"+ timeStamp + ".jpg");
        } else {
            return new File(storageDir.getPath() + File.separator
                    + "VID_"+ timeStamp + ".mp4");
        }
    }
}
