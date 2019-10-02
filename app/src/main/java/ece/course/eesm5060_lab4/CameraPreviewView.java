package ece.course.eesm5060_lab4;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.hardware.Camera.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.List;

public class CameraPreviewView extends SurfaceView implements SurfaceHolder.Callback {

    List<Camera.Size> mSupportedPreviewSizes;
    Size mPreviewSize;
    Camera mCamera;

    public CameraPreviewView(Context context, AttributeSet attr) {
        super(context, attr);
        getHolder().addCallback(this);
        //getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);
        if (mSupportedPreviewSizes != null)
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();
        }
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        if (sizes == null) return null;
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;
        Size optimalSize = null;
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        try {
            if (mCamera != null) mCamera.setPreviewDisplay(holder);
        } catch (Exception exception) {
            System.out.println(exception.toString());
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        mCamera.setParameters(parameters);
        mCamera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null)
            mCamera.stopPreview();
    }


}
