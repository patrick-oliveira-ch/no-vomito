package com.motioncues;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

public class DotsOverlayView extends View {
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lateralOffset = 0f;
    private float longitudinalOffset = 0f;
    private int dotColor = 0xFF333333;
    private float dotRadius;
    private float dotAlpha = 0.7f;
    private int screenW, screenH;
    private static final int DOTS_PER_SIDE = 5;
    private static final float MAX_OFFSET = 30f;
    private static final float SENSITIVITY = 12f;
    private boolean visible = true;

    public DotsOverlayView(Context ctx) {
        super(ctx);
        dotPaint.setStyle(Paint.Style.FILL);
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
            .getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        dotRadius = dm.density * 4f;
    }

    public void updateMotion(float lateralG, float longitudinalG) {
        lateralOffset = clamp(lateralG * SENSITIVITY, -MAX_OFFSET, MAX_OFFSET);
        longitudinalOffset = clamp(longitudinalG * SENSITIVITY, -MAX_OFFSET, MAX_OFFSET);
        invalidate();
    }

    public void setDotColor(int color) {
        this.dotColor = color;
        invalidate();
    }

    public void setDotsVisible(boolean v) {
        this.visible = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!visible) return;

        dotPaint.setColor(dotColor);
        dotPaint.setAlpha((int)(dotAlpha * 255));

        float margin = dotRadius * 2;
        float spacing = (screenH - margin * 2) / (DOTS_PER_SIDE + 1);

        // Left and right edge dots
        for (int i = 1; i <= DOTS_PER_SIDE; i++) {
            float baseY = margin + spacing * i;
            float y = baseY + longitudinalOffset;

            float lx = margin + lateralOffset;
            canvas.drawCircle(lx, y, dotRadius, dotPaint);

            float rx = screenW - margin - lateralOffset;
            canvas.drawCircle(rx, y, dotRadius, dotPaint);
        }

        // Top edge dots (2)
        float topSpacing = screenW / 3f;
        for (int i = 1; i <= 2; i++) {
            float x = topSpacing * i + lateralOffset;
            float ty = margin + longitudinalOffset;
            canvas.drawCircle(x, ty, dotRadius, dotPaint);
        }

        // Bottom edge dots (2)
        for (int i = 1; i <= 2; i++) {
            float x = topSpacing * i + lateralOffset;
            float by = screenH - margin - longitudinalOffset;
            canvas.drawCircle(x, by, dotRadius, dotPaint);
        }
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
