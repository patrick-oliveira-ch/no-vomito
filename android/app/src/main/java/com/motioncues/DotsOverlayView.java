package com.motioncues;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

public class DotsOverlayView extends View {
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int dotColor = 0xFF000000;
    private float dotAlpha = 0.45f;
    private float dotRadius;
    private int screenW, screenH;
    private float density;

    private static final float DOT_SIZE_DP = 10f;
    private int gridCols = 5;
    private int gridRows = 12;

    // Smooth animated offsets (what we actually draw)
    private float currentXOffset = 0f;
    private float currentYScroll = 0f;

    // Target offsets (from motion engine)
    private float targetXOffset = 0f;
    private float targetYScroll = 0f;

    // Settings
    private float sensitivity = 1.0f;
    private float scrollSpeed = 0.3f;

    // Smoothing factor
    private static final float SMOOTHING = 0.08f;

    // Grid positions
    private float[] dotBaseY;
    private float[] dotBaseX;
    private int dotCount;
    private float spacingX;
    private float spacingY;

    public DotsOverlayView(Context ctx) {
        super(ctx);
        dotPaint.setStyle(Paint.Style.FILL);
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
            .getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        density = dm.density;
        dotRadius = DOT_SIZE_DP * density / 2f;
        initDots();
    }

    private void initDots() {
        // Even grid across the whole screen
        float marginX = screenW * 0.06f;
        float marginY = screenH * 0.04f;
        spacingX = (screenW - marginX * 2) / (gridCols - 1);
        spacingY = (screenH - marginY * 2) / (gridRows - 1);

        dotCount = gridCols * gridRows;
        dotBaseY = new float[dotCount];
        dotBaseX = new float[dotCount];

        int idx = 0;
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < gridCols; col++) {
                dotBaseX[idx] = marginX + col * spacingX;
                dotBaseY[idx] = marginY + row * spacingY;
                idx++;
            }
        }
    }

    public void updateMotion(float motionX, float motionY) {
        float scale = 0.17f * scrollSpeed;
        float accelScale = 2.0f * sensitivity;

        targetYScroll += motionY * scale + motionY * accelScale * 0.1f;
        targetXOffset += motionX * scale + motionX * accelScale * 0.1f;

        invalidate();
    }

    public void setSensitivity(float s) {
        this.sensitivity = s;
    }

    public void setScrollSpeed(float s) {
        this.scrollSpeed = s;
    }

    public void setDotAlpha(float a) {
        this.dotAlpha = a;
        invalidate();
    }

    /** Set grid density. density 1-10, maps to 3x6 up to 8x18. */
    public void setGridDensity(int level) {
        gridCols = 3 + level / 2;
        gridRows = 6 + level;
        initDots();
        currentYScroll = 0f;
        currentXOffset = 0f;
        targetYScroll = 0f;
        targetXOffset = 0f;
        invalidate();
    }

    public void setDotColor(int color) {
        this.dotColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Smooth interpolation towards target
        currentXOffset += (targetXOffset - currentXOffset) * SMOOTHING;
        currentYScroll += (targetYScroll - currentYScroll) * SMOOTHING;

        dotPaint.setColor(dotColor);

        float fadeZone = dotRadius * 8;
        float rangeY = gridRows * spacingY;
        float rangeX = gridCols * spacingX;

        for (int i = 0; i < dotCount; i++) {
            float rawX = dotBaseX[i] + currentXOffset;
            float rawY = dotBaseY[i] + currentYScroll;

            // Wrap both axes for continuous looping
            float x = mod(rawX, rangeX);
            float y = mod(rawY, rangeY);

            // Fade near edges (all 4 sides)
            float alpha = dotAlpha;
            if (y < fadeZone) {
                alpha *= Math.max(0f, y / fadeZone);
            } else if (y > rangeY - fadeZone) {
                alpha *= Math.max(0f, (rangeY - y) / fadeZone);
            }
            if (x < fadeZone) {
                alpha *= Math.max(0f, x / fadeZone);
            } else if (x > rangeX - fadeZone) {
                alpha *= Math.max(0f, (rangeX - x) / fadeZone);
            }
            if (alpha <= 0.01f) continue;

            dotPaint.setAlpha((int)(alpha * 255));
            canvas.drawCircle(x, y, dotRadius, dotPaint);
        }
    }

    /** Always-positive modulo */
    private float mod(float a, float b) {
        float r = a % b;
        return r < 0 ? r + b : r;
    }
}
