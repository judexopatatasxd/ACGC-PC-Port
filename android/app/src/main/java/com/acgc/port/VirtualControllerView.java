package com.acgc.port;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Transparent multi-touch controls that emit ordinary Android key events into SDL. */
public final class VirtualControllerView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Control> controls = new ArrayList<Control>();
    private final SparseArray<Control> pointers = new SparseArray<Control>();
    private final Set<Integer> pressedKeys = new HashSet<Integer>();

    private float unit;

    public VirtualControllerView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(true);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        stroke.setColor(Color.argb(175, 255, 255, 255));
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        releaseAll();
        controls.clear();
        unit = Math.min(width, height);

        float mainRadius = unit * 0.145f;
        float smallRadius = unit * 0.087f;
        float buttonRadius = unit * 0.066f;

        controls.add(Control.pad("STICK", width * 0.15f, height * 0.70f, mainRadius,
                KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D));
        controls.add(Control.pad("D", width * 0.36f, height * 0.75f, smallRadius,
                KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_K,
                KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_L));
        controls.add(Control.pad("C", width * 0.64f, height * 0.76f, smallRadius,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT));

        controls.add(Control.button("A", width * 0.88f, height * 0.68f,
                buttonRadius * 1.12f, KeyEvent.KEYCODE_SPACE));
        controls.add(Control.button("B", width * 0.78f, height * 0.79f,
                buttonRadius, KeyEvent.KEYCODE_SHIFT_LEFT));
        controls.add(Control.button("X", width * 0.79f, height * 0.57f,
                buttonRadius, KeyEvent.KEYCODE_X));
        controls.add(Control.button("Y", width * 0.69f, height * 0.68f,
                buttonRadius, KeyEvent.KEYCODE_Y));

        controls.add(Control.button("L", width * 0.11f, height * 0.12f,
                buttonRadius * 0.92f, KeyEvent.KEYCODE_Q));
        controls.add(Control.button("R", width * 0.89f, height * 0.12f,
                buttonRadius * 0.92f, KeyEvent.KEYCODE_E));
        controls.add(Control.button("Z", width * 0.76f, height * 0.12f,
                buttonRadius * 0.78f, KeyEvent.KEYCODE_Z));
        controls.add(Control.button("START", width * 0.50f, height * 0.12f,
                buttonRadius * 0.82f, KeyEvent.KEYCODE_ENTER));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        text.setTextSize(unit * 0.034f);
        for (Control control : controls) drawControl(canvas, control);
    }

    private void drawControl(Canvas canvas, Control control) {
        int alpha = control.activePointer >= 0 ? 150 : 78;
        fill.setColor(Color.argb(alpha, 17, 27, 25));
        canvas.drawCircle(control.cx, control.cy, control.radius, fill);
        canvas.drawCircle(control.cx, control.cy, control.radius, stroke);

        if (control.kind == Control.PAD) {
            fill.setColor(Color.argb(control.activePointer >= 0 ? 190 : 115, 114, 214, 161));
            canvas.drawCircle(control.cx + control.knobX, control.cy + control.knobY,
                    control.radius * 0.40f, fill);
        }

        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = control.cy - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(control.label, control.cx, baseline, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            int pointerId = event.getPointerId(index);
            Control control = findControl(event.getX(index), event.getY(index));
            if (control != null) {
                control.activePointer = pointerId;
                pointers.put(pointerId, control);
                control.update(event.getX(index), event.getY(index), this);
                invalidate();
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                Control control = pointers.get(event.getPointerId(i));
                if (control != null) control.update(event.getX(i), event.getY(i), this);
            }
            invalidate();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int index = event.getActionIndex();
            releasePointer(event.getPointerId(index));
            invalidate();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            releaseAll();
            invalidate();
        }
        return true;
    }

    private Control findControl(float x, float y) {
        Control best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Control control : controls) {
            if (control.activePointer >= 0) continue;
            float dx = x - control.cx;
            float dy = y - control.cy;
            float distance = dx * dx + dy * dy;
            float hitRadius = control.radius * (control.kind == Control.PAD ? 1.35f : 1.18f);
            if (distance <= hitRadius * hitRadius && distance < bestDistance) {
                best = control;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void releasePointer(int pointerId) {
        Control control = pointers.get(pointerId);
        if (control == null) return;
        control.release(this);
        pointers.remove(pointerId);
    }

    private void press(int keyCode) {
        if (pressedKeys.add(keyCode)) {
            try {
                SDLActivity.onNativeKeyDown(keyCode);
            } catch (UnsatisfiedLinkError ignored) {
                pressedKeys.remove(keyCode);
            }
        }
    }

    private void release(int keyCode) {
        if (pressedKeys.remove(keyCode)) {
            try {
                SDLActivity.onNativeKeyUp(keyCode);
            } catch (UnsatisfiedLinkError ignored) {
                // Native shutdown can race the final touch-up event.
            }
        }
    }

    private void releaseAll() {
        for (Control control : controls) control.release(this);
        Integer[] keys = pressedKeys.toArray(new Integer[0]);
        for (Integer key : keys) release(key);
        pointers.clear();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    private static final class Control {
        static final int BUTTON = 0;
        static final int PAD = 1;

        final int kind;
        final String label;
        final float cx;
        final float cy;
        final float radius;
        final int key;
        final int up;
        final int down;
        final int left;
        final int right;

        int activePointer = -1;
        float knobX;
        float knobY;
        boolean upHeld;
        boolean downHeld;
        boolean leftHeld;
        boolean rightHeld;

        private Control(int kind, String label, float cx, float cy, float radius,
                        int key, int up, int down, int left, int right) {
            this.kind = kind;
            this.label = label;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.key = key;
            this.up = up;
            this.down = down;
            this.left = left;
            this.right = right;
        }

        static Control button(String label, float x, float y, float radius, int key) {
            return new Control(BUTTON, label, x, y, radius, key, 0, 0, 0, 0);
        }

        static Control pad(String label, float x, float y, float radius,
                           int up, int down, int left, int right) {
            return new Control(PAD, label, x, y, radius, 0, up, down, left, right);
        }

        void update(float x, float y, VirtualControllerView view) {
            if (kind == BUTTON) {
                view.press(key);
                return;
            }

            float dx = x - cx;
            float dy = y - cy;
            float length = (float)Math.sqrt(dx * dx + dy * dy);
            float max = radius * 0.62f;
            if (length > max && length > 0f) {
                dx = dx * max / length;
                dy = dy * max / length;
            }
            knobX = dx;
            knobY = dy;

            float threshold = radius * 0.23f;
            setDirections(dy < -threshold, dy > threshold,
                    dx < -threshold, dx > threshold, view);
        }

        private void setDirections(boolean newUp, boolean newDown, boolean newLeft, boolean newRight,
                                   VirtualControllerView view) {
            if (newUp != upHeld) { if (newUp) view.press(up); else view.release(up); upHeld = newUp; }
            if (newDown != downHeld) { if (newDown) view.press(down); else view.release(down); downHeld = newDown; }
            if (newLeft != leftHeld) { if (newLeft) view.press(left); else view.release(left); leftHeld = newLeft; }
            if (newRight != rightHeld) { if (newRight) view.press(right); else view.release(right); rightHeld = newRight; }
        }

        void release(VirtualControllerView view) {
            if (kind == BUTTON) {
                view.release(key);
            } else {
                setDirections(false, false, false, false, view);
                knobX = 0f;
                knobY = 0f;
            }
            activePointer = -1;
        }
    }
}
