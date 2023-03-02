package com.kserjhfnkedjfv.camerax.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.kserjhfnkedjfv.camerax.R;
import com.kserjhfnkedjfv.camerax.databinding.ActivityCameraBinding;
import com.kserjhfnkedjfv.camerax.util.CameraConstant;
import com.kserjhfnkedjfv.camerax.util.CameraParam;
import com.kserjhfnkedjfv.camerax.util.NoPermissionException;
import com.kserjhfnkedjfv.camerax.util.Tools;

import java.io.File;
import java.util.concurrent.TimeUnit;


public class CameraActivity extends AppCompatActivity {

    private static String TAG = "CameraActivity";
    private ImageCapture imageCapture;
    private CameraControl mCameraControl;
    private ProcessCameraProvider cameraProvider;
    private CameraParam mCameraParam;
    private boolean front;
    private Handler mTimerDisposable;
    private ConstraintSet mConstraintSet = new ConstraintSet();

    private ActivityCameraBinding mBinding;
    private int mTimer = 0xaa;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        mBinding = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        WindowInsetsControllerCompat windowInsetsControllerCompat = ViewCompat.getWindowInsetsController(mBinding.getRoot());
        //隐藏状态栏
        windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.statusBars());
//        windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.captionBar());

        mCameraParam = getIntent().getParcelableExtra(CameraConstant.CAMERA_PARAM_KEY);
        if (mCameraParam == null) {
            throw new IllegalArgumentException("CameraParam is null");
        }
        if (!Tools.checkPermission(this)) {
            throw new NoPermissionException("Need to have permission to take pictures and storage");
        }
        front = mCameraParam.isFront();
        initView();
        setViewParam();
        intCamera();

        initMaskDimensionRatio();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoFocusCancel();
        cameraProvider.unbindAll();
//        if (cameraExecutor != null)
//            cameraExecutor.shutdown();
    }

    private void autoFocusCancel() {
        if (mTimerDisposable != null) {
            mTimerDisposable.removeMessages(mTimer);
        }
    }

    /**
     * 设置裁剪区域的比例
     */
    private void initMaskDimensionRatio() {
        String maskDimensionRatio = mCameraParam.getMaskDimensionRatio();
        if (!maskDimensionRatio.isEmpty()) {
            WindowManager mgr = ((WindowManager) getSystemService(Context.WINDOW_SERVICE));
            int lastOrientation;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lastOrientation = getDisplay().getRotation();
            } else {
                lastOrientation = mgr.getDefaultDisplay().getRotation();
            }
            if (lastOrientation == 1 || lastOrientation == 3) {
                maskDimensionRatio = maskDimensionRatio.replaceAll("h", "w");
            } else {
                maskDimensionRatio = maskDimensionRatio.replaceAll("w", "h");
            }
            try {
                ConstraintLayout constraintLayout = findViewById(R.id.max_layout);
                mConstraintSet.clone(constraintLayout);
                mConstraintSet.setDimensionRatio(R.id.view_mask, maskDimensionRatio);
                mConstraintSet.applyTo(constraintLayout);
            } catch (Exception e) {
                Log.e(TAG, "onRestoreInstanceState: ", e);
            }

        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            autoFocus((int) event.getX(), (int) event.getY(), false);
        }
        return super.onTouchEvent(event);
    }

    //https://developer.android.com/training/camerax/configuration
    private void autoFocus(int x, int y, boolean first) {
        MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(x, y);
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
//                .disableAutoCancel()
//                .addPoint(point2, FocusMeteringAction.FLAG_AE)
                // 3秒内自动调用取消对焦
                .setAutoCancelDuration(mCameraParam.getFocusViewTime(), TimeUnit.SECONDS)
                .build();
//        mCameraControl.cancelFocusAndMetering();
        ListenableFuture<FocusMeteringResult> future = mCameraControl.startFocusAndMetering(action);
        future.addListener(() -> {
            try {
                FocusMeteringResult result = future.get();
                if (result.isFocusSuccessful()) {
//                    mBinding.focusView.showFocusView(x, y);
                    if (!first && mCameraParam.isShowFocusTips()) {
                        Toast mToast = Toast.makeText(getApplicationContext(), mCameraParam.getFocusSuccessTips(this), Toast.LENGTH_LONG);
                        mToast.setGravity(Gravity.CENTER, 0, 0);
                        mToast.show();
                    }
                } else {
                    if (mCameraParam.isShowFocusTips()) {
                        Toast mToast = Toast.makeText(getApplicationContext(), mCameraParam.getFocusFailTips(this), Toast.LENGTH_LONG);
                        mToast.setGravity(Gravity.CENTER, 0, 0);
                        mToast.show();
                    }
                    mBinding.focusView.hideFocusView();
                }
            } catch (Exception e) {
                e.printStackTrace();
                mBinding.focusView.hideFocusView();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setViewParam() {
        //是否显示切换按钮
        if (mCameraParam.isShowSwitch()) {
            mBinding.imgSwitch.setVisibility(View.VISIBLE);
            if (mCameraParam.getSwitchSize() != -1 || mCameraParam.getSwitchLeft() != -1 || mCameraParam.getSwitchTop() != -1) {
                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) mBinding.imgSwitch.getLayoutParams();
                if (mCameraParam.getSwitchSize() != -1) {
                    layoutParams.width = layoutParams.height = mCameraParam.getSwitchSize();
                }
                if (mCameraParam.getSwitchLeft() != -1) {
                    layoutParams.leftMargin = mCameraParam.getSwitchLeft();
                }
                if (mCameraParam.getSwitchTop() != -1) {
                    layoutParams.topMargin = mCameraParam.getSwitchTop();
                }
                mBinding.imgSwitch.setLayoutParams(layoutParams);
            }
            if (mCameraParam.getSwitchImgId() != -1) {
                mBinding.imgSwitch.setImageResource(mCameraParam.getSwitchImgId());
            }
        } else {
            mBinding.imgSwitch.setVisibility(View.GONE);
        }

        //是否显示裁剪框
        if (mCameraParam.isShowMask()) {
            mBinding.viewMask.setVisibility(View.VISIBLE);
            if (mCameraParam.getMaskMarginLeftAndRight() != -1 || mCameraParam.getMaskMarginTop() != -1
                    || mCameraParam.getMaskRatioH() != -1) {
                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) mBinding.viewMask.getLayoutParams();

                if (mCameraParam.getMaskMarginLeftAndRight() != -1) {
                    layoutParams.leftMargin = layoutParams.rightMargin = mCameraParam.getMaskMarginLeftAndRight();
                }

                if (mCameraParam.getMaskMarginTop() != -1) {
                    layoutParams.topMargin = mCameraParam.getMaskMarginTop();
                }

                if (mCameraParam.getMaskRatioH() != -1) {
                    Tools.reflectMaskRatio(mBinding.viewMask, mCameraParam.getMaskRatioW(), mCameraParam.getMaskRatioH());
                }
                mBinding.viewMask.setLayoutParams(layoutParams);
            }
            if (mCameraParam.getMaskImgId() != -1) {
                mBinding.viewMask.setBackgroundResource(mCameraParam.getMaskImgId());
            }
        } else {
            mBinding.viewMask.setVisibility(View.GONE);
        }

        if (mCameraParam.getBackText() != null) {
            mBinding.tvBack.setText(mCameraParam.getBackText());
        }
        if (mCameraParam.getBackColor() != -1) {
            mBinding.tvBack.setTextColor(mCameraParam.getBackColor());
        }
        if (mCameraParam.getBackSize() != -1) {
            mBinding.tvBack.setTextSize(mCameraParam.getBackSize());
        }

        if (mCameraParam.getTakePhotoSize() != -1) {
            int size = mCameraParam.getTakePhotoSize();

            ViewGroup.LayoutParams pictureCancelParams = mBinding.imgPictureCancel.getLayoutParams();
            pictureCancelParams.width = pictureCancelParams.height = size;
            mBinding.imgPictureCancel.setLayoutParams(pictureCancelParams);

            ViewGroup.LayoutParams pictureSaveParams = mBinding.imgPictureSave.getLayoutParams();
            pictureSaveParams.width = pictureSaveParams.height = size;
            mBinding.imgPictureSave.setLayoutParams(pictureSaveParams);

            ViewGroup.LayoutParams takePhotoParams = mBinding.imgTakePhoto.getLayoutParams();
            takePhotoParams.width = takePhotoParams.height = size;
            mBinding.imgTakePhoto.setLayoutParams(takePhotoParams);
        }

        mBinding.focusView.setParam(mCameraParam.getFocusViewSize(), mCameraParam.getFocusViewColor(),
                mCameraParam.getFocusViewTime(), mCameraParam.getFocusViewStrokeSize(), mCameraParam.getCornerViewSize());


        if (mCameraParam.getCancelImgId() != -1) {
            mBinding.imgPictureCancel.setImageResource(mCameraParam.getCancelImgId());
        }
        if (mCameraParam.getSaveImgId() != -1) {
            mBinding.imgPictureSave.setImageResource(mCameraParam.getSaveImgId());
        }
        if (mCameraParam.getTakePhotoImgId() != -1) {
            mBinding.imgTakePhoto.setImageResource(mCameraParam.getTakePhotoImgId());
        }

        if (mCameraParam.getResultBottom() != -1) {
            ConstraintLayout.LayoutParams resultPictureParams = (ConstraintLayout.LayoutParams) mBinding.rlResultPicture.getLayoutParams();
            resultPictureParams.bottomMargin = mCameraParam.getResultBottom();
            mBinding.rlResultPicture.setLayoutParams(resultPictureParams);

            ConstraintLayout.LayoutParams startParams = (ConstraintLayout.LayoutParams) mBinding.rlStart.getLayoutParams();
            startParams.bottomMargin = mCameraParam.getResultBottom();
            mBinding.rlStart.setLayoutParams(startParams);
        }

        if (mCameraParam.getResultLeftAndRight() != -1) {
            RelativeLayout.LayoutParams pictureCancelParams = (RelativeLayout.LayoutParams) mBinding.imgPictureCancel.getLayoutParams();
            pictureCancelParams.leftMargin = mCameraParam.getResultLeftAndRight();
            mBinding.imgPictureCancel.setLayoutParams(pictureCancelParams);

            RelativeLayout.LayoutParams pictureSaveParams = (RelativeLayout.LayoutParams) mBinding.imgPictureSave.getLayoutParams();
            pictureSaveParams.rightMargin = mCameraParam.getResultLeftAndRight();
            mBinding.imgPictureSave.setLayoutParams(pictureSaveParams);
        }

        if (mCameraParam.getBackLeft() != -1) {
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mBinding.tvBack.getLayoutParams();
            layoutParams.leftMargin = mCameraParam.getBackLeft();
            mBinding.tvBack.setLayoutParams(layoutParams);
        }
        Tools.reflectPreviewRatio(mBinding.previewView, Tools.aspectRatio(this));
    }

    private void initView() {

        //切换相机
        mBinding.imgSwitch.setOnClickListener(v -> {
            switchOrition();
            bindCameraUseCases();
        });

        //拍照成功然后点取消
        mBinding.imgPictureCancel.setOnClickListener(v -> {
            mBinding.imgPicture.setImageBitmap(null);
            mBinding.rlStart.setVisibility(View.VISIBLE);
            mBinding.rlResultPicture.setVisibility(View.GONE);
            mBinding.llPictureParent.setVisibility(View.GONE);
            autoFocusTimer();
        });
        //拍照成功然后点保存
        mBinding.imgPictureSave.setOnClickListener(v -> {
            savePicture();
        });
        //还没拍照就点取消
        mBinding.tvBack.setOnClickListener(v -> {
            finish();
        });
        //点击拍照
        mBinding.imgTakePhoto.setOnClickListener(v -> {
            takePhoto(mCameraParam.getPictureTempPath());
        });

    }

    private void switchOrition() {
        if (front) {
            front = false;
        } else {
            front = true;
        }
    }

    private void intCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.d("wld________", e.toString());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        int screenAspectRatio = Tools.aspectRatio(this);
        int rotation = mBinding.previewView.getDisplay() == null ? Surface.ROTATION_0 : mBinding.previewView.getDisplay().getRotation();
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build();

        imageCapture = new ImageCapture.Builder()
                //优化捕获速度，可能降低图片质量
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build();
        // 在重新绑定之前取消绑定用例
        cameraProvider.unbindAll();
        int cameraOrition = front ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraOrition).build();
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        preview.setSurfaceProvider(mBinding.previewView.getSurfaceProvider());
        mCameraControl = camera.getCameraControl();
        autoFocusTimer();
    }

    private void autoFocusTimer() {
        int[] outLocation = Tools.getViewLocal(mBinding.viewMask);
        autoFocusCancel();

        mTimerDisposable = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                if (msg.what == mTimer) {
                    autoFocus(outLocation[0] + (mBinding.viewMask.getMeasuredWidth()) / 2, outLocation[1] + (mBinding.viewMask.getMeasuredHeight()) / 2, true);
                    mTimerDisposable.sendEmptyMessageDelayed(mTimer, 10000);
                }
            }
        };
        mTimerDisposable.sendEmptyMessage(mTimer);

    }

    private void takePhoto(String photoFile) {
        // 保证相机可用
        if (imageCapture == null) {
            return;
        }

        autoFocusCancel();

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(new File(photoFile)).build();

        //  设置图像捕获监听器，在拍照后触发
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        mBinding.rlStart.setVisibility(View.GONE);
                        mBinding.rlResultPicture.setVisibility(View.VISIBLE);
                        mBinding.llPictureParent.setVisibility(View.VISIBLE);
                        Bitmap bitmap = Tools.bitmapClip(CameraActivity.this, photoFile, front);
                        mBinding.imgPicture.setImageBitmap(bitmap);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("wld_____", "Photo capture failed: ${exc.message}", exception);
                    }
                });
    }

    private void savePicture() {
        Rect rect = null;
        if (mCameraParam.isShowMask()) {
            int[] outLocation = Tools.getViewLocal(mBinding.viewMask);
            rect = new Rect(outLocation[0], outLocation[1],
                    mBinding.viewMask.getMeasuredWidth(), mBinding.viewMask.getMeasuredHeight());
        }
        Tools.saveBitmap(this, mCameraParam.getPictureTempPath(), mCameraParam.getPicturePath(), rect, front);
        Tools.deletTempFile(mCameraParam.getPictureTempPath());

        Intent intent = new Intent();
        intent.putExtra(CameraConstant.PICTURE_PATH_KEY, mCameraParam.getPicturePath());
        setResult(RESULT_OK, intent);
        finish();
    }
}
