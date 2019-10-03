package com.skolev.simplewheel.view.custom_views.simple_wheel;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;


import com.skolev.simplewheel.BuildConfig;

import java.lang.ref.WeakReference;
import java.util.Random;


public class SimpleWheelView extends View implements View.OnTouchListener {

    public static final int WHEEL_LAST_DIRECTION = 0;

    public interface OnWheelEventListener {

        void onWheelSpin(int direction);

        void onWheelStop(int sector);
    }

    private static final int NONE = -1;
    private static final int SECTORS = 8;
    private static final int FULL_ROTATIONS_FOR_REWARD = 10;
    private static final int POINTER_ANIMATION_DURATION = 250;
    private static final int DEFAULT_SHORT_ANIMATION_DURATION = 1000;
    private static final int POINTER_ANIMATION_ANGLE_ON_WHEEL_STOP = 45;
    private static final float FULL_ROTATION_ANGLE_FLOAT = 360F;
    private static final float DEGREES_PER_SECTOR_FLOAT = FULL_ROTATION_ANGLE_FLOAT / SECTORS;
    private static final float POINTER_INCLINATION_ANGLE_CONST = 3f;

    private int win;
    private int wheelTop;
    private int direction;
    private int wheelLeft;
    private int wheelRes;
    private int pointerRes;
    private int centerTextX;
    private int centerTextY;
    private int pointerTop;
    private int startSector;
    private int pointerLeft;
    private int wheelCenterX;
    private int wheelCenterY;
    private int centerTextSize;
    private float startAngle;
    private float rotationAngle;
    private float pointerDirection;
    private boolean canSpin;
    private boolean onPointer;
    private String centerText;

    private Paint paint;
    private Paint textPaint;
    private Bitmap wheel;
    private Bitmap pointer;
    private Bitmap sectors;

    private Matrix wheelMatrix;
    private Matrix pointerMatrix;

    private ValueAnimator spinAnimator;
    private ValueAnimator rewardAnimator;
    private ValueAnimator pointerAnimator;

    private Wheel wheelBluePrint;
    private Pointer pointerBluePrint;
    private BitmapFactory.Options opt;
    private ColorMatrixColorFilter cf;
    private GestureDetector gestureDetector;
    private WeakReference<OnWheelEventListener> listener;

    {
        init();
    }

    public SimpleWheelView(@NonNull Context context) {
        super(context);
    }

    public SimpleWheelView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SimpleWheelView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public SimpleWheelView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null) {
            init(attrs.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", "wheel", 0),
                    attrs.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", "pointer", 0),
                    attrs.getAttributeIntValue("http://schemas.android.com/apk/res-auto", "sectors", 0));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = getSuggestedMinimumWidth();
        int desiredHeight = getSuggestedMinimumHeight();

        int size = Math.min(measureDimension(desiredWidth, widthMeasureSpec),
                measureDimension(desiredHeight, heightMeasureSpec));

        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.wheel == null || this.pointer == null) {
            createWheel();
            initSectors();
        }
        if (this.wheelBluePrint == null) {
            return;
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        if (!this.canSpin) {
            this.paint.setColorFilter(this.cf);
        }
        canvas.drawBitmap(this.wheel, this.wheelMatrix, this.paint);

        if (this.sectors != null) {
            canvas.drawBitmap(this.sectors, this.wheelMatrix, this.paint);
        }
        if (!this.canSpin) {
            this.paint.setColorFilter(null);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        canvas.drawBitmap(this.pointer, this.pointerMatrix, this.paint);

        if (this.centerText != null) {
            canvas.drawText(this.centerText, this.centerTextX, this.centerTextY, this.textPaint);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (!this.canSpin) {
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            handleActionDown(event);
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            handleActionMove(event);
        }

        this.gestureDetector.onTouchEvent(event);
        return false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.canSpin = enabled;
        invalidate();
    }

    private int measureDimension(int desiredSize, int measureSpec) {
        int result = 0;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
            case MeasureSpec.AT_MOST:
            case MeasureSpec.UNSPECIFIED:
                result = Math.max(desiredSize, specSize);
                break;
        }

        if (result < desiredSize && BuildConfig.DEBUG) {
//            Log.e(this.getClass().getSimpleName(), "The view is too small, the content might get cut");
        }
        return result;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {

        int wheelRad = (width >> 3) * 3;
        int wheelCenterRadius = wheelRad >> 2;
        int pointerWidth = wheelRad >> 3;
        int pointerHeight = pointerWidth << 1;

        if (this.wheelBluePrint == null || this.pointerBluePrint == null) {
            this.wheelBluePrint = new Wheel(wheelRad, wheelCenterRadius, SECTORS);
            this.pointerBluePrint = new Pointer(pointerWidth, pointerHeight);
        } else if (width != oldw || height != oldh) {
            this.wheelBluePrint.radius = wheelRad;
            this.wheelBluePrint.centerRadius = wheelCenterRadius;
            this.pointerBluePrint.width = pointerWidth;
            this.pointerBluePrint.height = pointerHeight;
        }

        this.wheelCenterX = width >> 1;
        this.wheelCenterY = height >> 1;
        this.wheelLeft = this.wheelCenterX - this.wheelBluePrint.radius;
        this.wheelTop = this.wheelCenterY - this.wheelBluePrint.radius;

        this.pointerLeft = this.wheelCenterX - (this.pointerBluePrint.width >> 1);
        this.pointerTop = this.wheelTop - (this.pointerBluePrint.height - this.pointerBluePrint.width);

        updateWheelPosition();
        updatePointerPosition();
    }

    private void init(int wheelRes, int pointerRes, int sectors) {
        if (wheelRes == 0 || pointerRes == 0 || sectors == 0) return;

        this.wheelRes = wheelRes;
        this.pointerRes = pointerRes;

        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        BitmapFactory.decodeResource(getResources(), wheelRes, opt);
        this.wheelBluePrint = new Wheel(opt.outWidth >> 1, opt.outWidth >> 3, sectors);

        BitmapFactory.decodeResource(getResources(), pointerRes, opt);
        this.pointerBluePrint = new Pointer(opt.outWidth, opt.outHeight);
    }

    private void init() {
        this.canSpin = true;
        this.win = NONE;
        this.gestureDetector = createGestureDetector();

        initPaint();
        initMatrix();
        initTextPaint();
        initColorFilter();
        initBitmapOptions();
        this.setOnTouchListener(this);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void initPaint() {
        this.paint = new Paint(
                Paint.ANTI_ALIAS_FLAG
                        | Paint.FILTER_BITMAP_FLAG
                        | Paint.DITHER_FLAG);

        this.paint.setAntiAlias(true);
    }

    private void initTextPaint() {
        this.textPaint = new Paint(
                Paint.ANTI_ALIAS_FLAG
                        | Paint.SUBPIXEL_TEXT_FLAG
                        | Paint.LINEAR_TEXT_FLAG);

        this.textPaint.setAntiAlias(true);
        this.textPaint.setColor(Color.BLACK);
        this.textPaint.setTextSize(27);
    }

    private void initMatrix() {
        this.wheelMatrix = new Matrix();
        this.pointerMatrix = new Matrix();
    }

    private void initColorFilter() {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        this.cf = new ColorMatrixColorFilter(cm);
    }

    private void initBitmapOptions() {
        this.opt = new BitmapFactory.Options();
        this.opt.inMutable = true;
        this.opt.inScaled = false;
        this.opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    public void initSectors() {
        if (this.sectors != null) {
            this.sectors.recycle();
        }
        this.sectors = createSectors();
    }

    private void createWheel() {
        if (this.wheelRes != 0 && this.pointerRes != 0) {
            Bitmap tmp = BitmapFactory.decodeResource(getResources(), this.wheelRes, this.opt);
            this.wheel = Bitmap.createScaledBitmap(
                    tmp,
                    this.wheelBluePrint.radius << 1,
                    this.wheelBluePrint.radius << 1,
                    true);

            if (!tmp.isRecycled()) {
                tmp.recycle();
            }
            tmp = BitmapFactory.decodeResource(getResources(), this.pointerRes, this.opt);
            this.pointer = Bitmap.createScaledBitmap(
                    tmp,
                    this.pointerBluePrint.width,
                    this.pointerBluePrint.height,
                    true);

            if (!tmp.isRecycled()) {
                tmp.recycle();
            }
        } else {
            this.wheel = Bitmap.createBitmap(
                    this.wheelBluePrint.radius << 1,
                    this.wheelBluePrint.radius << 1,
                    Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(this.wheel);

            int centerX = this.wheelBluePrint.radius;
            int centerY = this.wheelBluePrint.radius;

            this.paint.setColor(Color.RED);
            this.paint.setStrokeWidth(5);
            canvas.drawCircle(
                    centerX,
                    centerY,
                    this.wheelBluePrint.radius,
                    this.paint);

            this.paint.setColor(Color.LTGRAY);
            canvas.drawCircle(
                    centerX,
                    centerY,
                    this.wheelBluePrint.radius - this.paint.getStrokeWidth(),
                    this.paint);

            this.paint.setColor(Color.RED);
            canvas.rotate(
                    (FULL_ROTATION_ANGLE_FLOAT / this.wheelBluePrint.sectors) / 2f,
                    centerX,
                    centerY);

            for (int i = 0; i < this.wheelBluePrint.sectors; i++) {
                canvas.drawLine(
                        centerX,
                        0,
                        centerX,
                        centerY - this.wheelBluePrint.centerRadius,
                        paint);

                canvas.rotate(360f / wheelBluePrint.sectors, centerX, centerY);
            }
            this.paint.setColor(Color.YELLOW);
            canvas.drawCircle(centerX, centerY, wheelBluePrint.centerRadius, paint);

            this.paint.setColor(Color.GREEN);
            this.pointer = Bitmap.createBitmap(
                    this.pointerBluePrint.width,
                    this.pointerBluePrint.height,
                    Bitmap.Config.ARGB_8888);

            canvas = new Canvas(this.pointer);
            canvas.drawLine(
                    this.pointer.getWidth(),
                    0,
                    this.pointer.getWidth() >> 1,
                    this.pointer.getHeight(),
                    this.paint);

            canvas.drawLine(
                    0,
                    0,
                    this.pointer.getWidth() >> 1,
                    this.pointer.getHeight(),
                    paint);

            canvas.drawLine(0, 0, pointer.getWidth(), 0, this.paint);
        }
    }

    private Bitmap createSectors() {
        this.sectors = Bitmap.createBitmap(
                this.wheel.getWidth(),
                this.wheel.getHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas wheelCanvas = new Canvas(sectors);
        int margin = wheelCanvas.getHeight() >> 4;
        float angle = 360f / this.wheelBluePrint.sectors;
        int dstWidth = Double.valueOf(
                Math.PI * this.wheel.getWidth()).intValue() / (this.wheelBluePrint.sectors << 1);

        int dstStart = (wheelCanvas.getWidth() >> 1) - (dstWidth >> 1);
        int dstBottom = margin + dstWidth;

        this.textPaint.setTextSize(dstWidth);
        this.textPaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < this.wheelBluePrint.sectors; i++) {
            String text = Integer.toString(SECTORS - i);

            wheelCanvas.drawText(
                    text,
                    0,
                    text.length(),
                    dstStart + (dstWidth >> 1),
                    dstBottom,
                    textPaint);

            wheelCanvas.rotate(angle, wheelCanvas.getWidth() >> 1, wheelCanvas.getHeight() >> 1);
        }
        wheelCanvas.rotate(90f, wheelCanvas.getWidth() >> 1, wheelCanvas.getHeight() >> 1);
        invalidate();
        return this.sectors;
    }

    private GestureDetector createGestureDetector() {
        return new GestureDetector(getContext(), new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

                if (velocityX > 2000 || velocityY > 2000) {
                    spin(direction);
                    return false;
                }
                return false;
            }
        });
    }

    private void setCenterTextPosition() {
        this.centerTextX = this.wheelCenterX;
        this.centerTextY = this.wheelCenterY + Math.round(this.centerTextSize / 3f);
    }

    private void startRewardAnimation() {
        final int rewardAngle = (direction < 0 ? win : 360 - win) + 360 * FULL_ROTATIONS_FOR_REWARD;
        updatePointerPosition();

        if (this.rewardAnimator == null) {
            this.rewardAnimator = ValueAnimator.ofInt(0, rewardAngle);
        } else {
            this.rewardAnimator.setIntValues(0, rewardAngle);
        }
        this.pointerDirection = this.direction;
        this.rewardAnimator.setDuration(DEFAULT_SHORT_ANIMATION_DURATION * FULL_ROTATIONS_FOR_REWARD);
        this.rewardAnimator.setInterpolator(new DecelerateInterpolator());
        this.rewardAnimator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                updateWheelPosition();
                startPointerAnimation();

                if (listener != null && listener.get() != null) {
                    listener.get().onWheelStop(wheelBluePrint.sectors - Math.round(win / DEGREES_PER_SECTOR_FLOAT));
                }
                setEnabled(false);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        this.rewardAnimator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            rotationAngle = value * direction * -1;
            updateWheelPosition();
            updatePointerPosition();
            invalidate();
        });
        this.rewardAnimator.start();
    }

    private void startPointerAnimation() {
        if (this.pointerAnimator == null) {
            this.pointerAnimator = ValueAnimator.ofFloat(1f, 0).setDuration(POINTER_ANIMATION_DURATION);
        }
        this.pointerAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            pointerMatrix.reset();
            pointerMatrix.setTranslate(pointerLeft, pointerTop);
            pointerMatrix.postRotate(
                    POINTER_ANIMATION_ANGLE_ON_WHEEL_STOP * value * pointerDirection,
                    wheelCenterX,
                    pointerTop);

            invalidate();
        });
        this.pointerAnimator.setInterpolator(new AccelerateInterpolator());
        this.pointerAnimator.start();
    }

    private void handleActionDown(MotionEvent event) {
        this.startAngle = calculateAngle(event.getX(), event.getY());

        //for handling sector touch events
        {
            float sectorTouched =
                    (360f + startSector * DEGREES_PER_SECTOR_FLOAT + rotationAngle)
                            - (startAngle + DEGREES_PER_SECTOR_FLOAT / 2f);

            int sector = (int) (formatAngle(sectorTouched) / DEGREES_PER_SECTOR_FLOAT);
        }
    }

    private void handleActionMove(MotionEvent event) {
        float resultAngle = 0;

        for (int i = 0; i < event.getHistorySize(); i++) {
            float currentAngle = calculateAngle(event.getHistoricalX(i), event.getHistoricalY(i));
            resultAngle += currentAngle - startAngle;
            this.startAngle = currentAngle;
        }
        this.rotationAngle = formatAngle(resultAngle + rotationAngle);
        updateWheelPosition();

        if (resultAngle != 0) {
            this.direction = Float.valueOf(Math.signum(resultAngle) * -1).intValue();
            if (!this.onPointer) {
                this.pointerDirection = this.direction;
            }
        }
        updatePointerPosition();
        this.invalidate();
    }

    private void updateWheelPosition() {
        this.wheelMatrix.reset();
        this.wheelMatrix.setTranslate(this.wheelLeft, this.wheelTop);
        this.wheelMatrix.postRotate(this.rotationAngle, this.wheelCenterX, this.wheelCenterY);
    }

    private void updatePointerPosition() {
        float speedBonusAngle = (isSpinning()) ? 38 : 0;
        float speedAmplitude = (isSpinning()) ? 0 : 30;

        this.pointerMatrix.reset();
        this.pointerMatrix.setTranslate(this.pointerLeft, this.pointerTop);
        this.pointerMatrix.postRotate(
                speedBonusAngle * this.pointerDirection,
                this.wheelCenterX,
                this.pointerTop);

        float angle = Math.abs(this.rotationAngle) % DEGREES_PER_SECTOR_FLOAT;
        float halfSector = DEGREES_PER_SECTOR_FLOAT / 2;
        float rightBoundary = isSpinning() ? halfSector + 4f : halfSector + 8f;
        float leftBoundary = isSpinning() ? halfSector - 4f : halfSector - 8f;

        if (angle > leftBoundary && angle < rightBoundary) {
            this.onPointer = true;
            float pointerStartAngle;

            if (this.pointerDirection > 0) {
                pointerStartAngle = leftBoundary;
            } else {
                pointerStartAngle = rightBoundary;
            }
            float maxAngleDiff = Math.abs(rightBoundary - leftBoundary);
            float angleDiff = Math.abs(pointerStartAngle - angle);
            float angleCoef = angleDiff / maxAngleDiff;
            float pointerRotationAngle =
                    ((1 - angleCoef) * (POINTER_INCLINATION_ANGLE_CONST * (maxAngleDiff - angleDiff)
                            + angleCoef * speedAmplitude) * this.pointerDirection);

            this.pointerMatrix.postRotate(pointerRotationAngle, this.wheelCenterX, this.pointerTop);
        } else {
            this.onPointer = false;
        }
    }

    private float formatAngle(float angle) {
        if (angle >= FULL_ROTATION_ANGLE_FLOAT) {
            angle %= FULL_ROTATION_ANGLE_FLOAT;
        } else if (angle < 0) {
            angle = angle % FULL_ROTATION_ANGLE_FLOAT + FULL_ROTATION_ANGLE_FLOAT;
        }
        return angle;
    }

    private double calculateRadius(float x, float y) {
        float deltaX = Math.round(x) - this.wheelCenterX;
        float deltaY = Math.round(y) - this.wheelCenterY;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    private float calculateAngle(float x, float y) {
        double effectiveRadius = calculateRadius(x, y);
        if (effectiveRadius == 0) {
            return 0f;
        }
        double cosine = (Math.round(x) - Math.round(this.wheelCenterX)) / effectiveRadius;
        double sign = 1.0;
        double normalizer = 0.0;
        if (y < this.wheelCenterY) {
            sign = -1.0;
            normalizer = 360.0;
        }
        double rads = Math.acos(cosine);
        double rotation = Math.toDegrees(rads);
        return Double.valueOf(normalizer + rotation * sign).floatValue();
    }

    public boolean isSpinning() {
        return this.spinAnimator != null
                && this.spinAnimator.isRunning() || this.rewardAnimator != null
                && this.rewardAnimator.isRunning();
    }

    public void setWin(int sector) {
        this.win = Math.round(DEGREES_PER_SECTOR_FLOAT * sector);
        Toast.makeText(
                getContext(),
                "Win sector: " + (sector != 0 ? sector : SECTORS),
                Toast.LENGTH_SHORT)
                .show();
    }

    public void setListener(OnWheelEventListener listener) {
        this.listener = new WeakReference<>(listener);
    }

    //sets initial sector
    public void setStartSector(int sector) {
        this.startSector = sector;
        if (this.rotationAngle == 0) {
            this.rotationAngle = FULL_ROTATION_ANGLE_FLOAT - (FULL_ROTATION_ANGLE_FLOAT / wheelBluePrint.sectors) * sector;
        }
        updateWheelPosition();
        invalidate();
    }

    public void setCenterText(String centerText) {
        if (centerText == null || centerText.isEmpty() || this.wheel == null) return;

        this.centerTextSize = this.wheel.getHeight() / 5;
        this.textPaint.setTextSize(this.centerTextSize);
        this.centerText = centerText;
        setCenterTextPosition();
        invalidate();
    }

    public void reset() {
        this.win = NONE;
    }

    public void spin(int rotationDirection) {
        postDelayed(() -> setWin(new Random().nextInt(SECTORS)),
                new Random().nextInt(1000));

        if (rotationDirection != WHEEL_LAST_DIRECTION) {
            this.direction = rotationDirection;
        }
        reset();

        if (this.listener != null && this.listener.get() != null) {
            this.listener.get().onWheelSpin(this.direction);
        }
        this.pointerDirection = this.direction;
        updatePointerPosition();
        this.rotationAngle = formatAngle(this.rotationAngle);
        this.rotationAngle = Math.round(this.rotationAngle);

        if (this.spinAnimator == null) {
            this.spinAnimator = ValueAnimator.ofInt(0, 360).setDuration(DEFAULT_SHORT_ANIMATION_DURATION);
        }
        this.spinAnimator.setInterpolator(new LinearInterpolator());
        this.spinAnimator.setRepeatMode(ValueAnimator.RESTART);
        this.spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        this.spinAnimator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            rotationAngle += value * direction * -1;
            rotationAngle = formatAngle(rotationAngle);
            updateWheelPosition();
            updatePointerPosition();
            if ((rotationAngle < 10 || rotationAngle > 350) && win != NONE) {
                animation.cancel();
                startRewardAnimation();
            }
            invalidate();
        });
        this.spinAnimator.start();
    }

    public void destroy() {
        animate().cancel();

        if (this.spinAnimator != null) {
            this.spinAnimator.cancel();
            this.spinAnimator.removeAllListeners();
            this.spinAnimator = null;
        }
        if (this.rewardAnimator != null) {
            this.rewardAnimator.cancel();
            this.rewardAnimator.removeAllListeners();
            this.rewardAnimator = null;
        }
        if (this.pointerAnimator != null) {
            this.pointerAnimator.cancel();
            this.pointerAnimator.removeAllListeners();
            this.pointerAnimator = null;
        }
        if (this.listener != null) {
            this.listener.clear();
            this.listener = null;
        }
        tryRecycleBitmap(this.wheel);
        tryRecycleBitmap(this.pointer);
        tryRecycleBitmap(this.sectors);

        this.gestureDetector = null;
    }

    private void tryRecycleBitmap(Bitmap btm) {
        if (btm != null && !btm.isRecycled()) {
            btm.recycle();
        }
    }
}