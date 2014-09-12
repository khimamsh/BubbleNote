package io.github.vickychijwani.bubblenote;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringListener;
import com.facebook.rebound.SpringSystem;
import com.facebook.rebound.SpringUtil;

public class BubbleNoteService extends Service {

    public static final String TAG = "BubbleNoteService";
    private static final int MOVE_THRESHOLD = 100;  // square of the threshold distance in pixels

    private WindowManager mWindowManager;
    private ViewGroup mBubble;
    private View mContent;

    private boolean mbExpanded = false;
    private boolean mbMoved = false;
    private int[] mPos = {0, -20};

    @Override
    public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mBubble = (ViewGroup) inflater.inflate(R.layout.bubble, null, false);

        mContent = mBubble.findViewById(R.id.content);
        mContent.setScaleX(0.0f);
        mContent.setScaleY(0.0f);

        mBubble.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mContent.setPivotX(mBubble.findViewById(R.id.bubble).getWidth() / 2);
                if (Build.VERSION.SDK_INT >= 16) {
                    mBubble.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    mBubble.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
            }
        });

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = mPos[0];
        params.y = mPos[1];
        params.dimAmount = 0.6f;


        SpringSystem system = SpringSystem.create();
        SpringConfig springConfig = new SpringConfig(200, 20);

        final Spring contentSpring = system.createSpring();
        contentSpring.setSpringConfig(springConfig);
        contentSpring.setCurrentValue(0.0);
        contentSpring.addListener(new SpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                float value = (float) spring.getCurrentValue();
                float clampedValue = (float) SpringUtil.clamp(value, 0.0, 1.0);
                mContent.setScaleX(value);
                mContent.setScaleY(value);
                mContent.setAlpha(clampedValue);
            }

            @Override
            public void onSpringAtRest(Spring spring) {
                mContent.setLayerType(View.LAYER_TYPE_NONE, null);
            }

            @Override
            public void onSpringActivate(Spring spring) {
                mContent.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            @Override
            public void onSpringEndStateChange(Spring spring) {

            }
        });

        final Spring bubbleSpring = system.createSpring();
        bubbleSpring.setSpringConfig(springConfig);
        bubbleSpring.setCurrentValue(1.0);
        bubbleSpring.addListener(new SpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                double value = spring.getCurrentValue();
                params.x = (int) (SpringUtil.mapValueFromRangeToRange(value, 0.0, 1.0, 0.0, mPos[0]));
                params.y = (int) (SpringUtil.mapValueFromRangeToRange(value, 0.0, 1.0, 0.0, mPos[1]));
                mWindowManager.updateViewLayout(mBubble, params);
                if (spring.isOvershooting() && contentSpring.isAtRest()) {
                    contentSpring.setEndValue(1.0);
                }
            }

            @Override
            public void onSpringAtRest(Spring spring) {
                if (spring.currentValueIsApproximately(1.0)) {
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT;
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    mWindowManager.updateViewLayout(mBubble, params);
                }
            }

            @Override
            public void onSpringActivate(Spring spring) {

            }

            @Override
            public void onSpringEndStateChange(Spring spring) {

            }
        });


        mBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mbMoved = false;
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (mbMoved) return true;
                        if (! mbExpanded) {
                            mBubble.getLocationOnScreen(mPos);
                            mPos[1] -= Utils.getStatusBarHeight(BubbleNoteService.this);
                            params.width = WindowManager.LayoutParams.MATCH_PARENT;
                            params.height = WindowManager.LayoutParams.MATCH_PARENT;
                            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                            bubbleSpring.setEndValue(0.0);
                        } else {
                            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                            params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                            bubbleSpring.setEndValue(1.0);
                            contentSpring.setEndValue(0.0);
                        }
                        mbExpanded = ! mbExpanded;
                        mWindowManager.updateViewLayout(mBubble, params);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        params.x = initialX + deltaX;
                        params.y = initialY + deltaY;
                        if (deltaX * deltaX + deltaY * deltaY >= MOVE_THRESHOLD) {
                            mbMoved = true;
                            mWindowManager.updateViewLayout(mBubble, params);
                        }
                        return true;
                }
                return false;
            }
        });

        mWindowManager.addView(mBubble, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBubble != null) {
            mWindowManager.removeView(mBubble);
        }
    }

}
