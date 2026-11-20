// SPDX-License-Identifier: Apache-2.0
package de.hausbote.feder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import java.util.Random;
import me.jxl.kiosk.plugins.KsTheme;

/**
 * A talking inkwell with a quill. It leans in and its ink ripples while you
 * speak, rolls its eyes and bubbles while the assistant thinks, and moves its
 * mouth with the answer while the words appear like fresh ink on parchment.
 * Full screen it covers the kiosk with paper; docked it is a small parchment
 * card at the bottom.
 */
final class FederView extends View {
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final int DROPS = 28;
    private static final int SPECKS = 240;
    private static final float DEFAULT_BACKGROUND = 0.97f;

    private final FederSkin.Turn turn;
    private final boolean docked;
    private final float dp;
    private final int screenWidth;
    private KsTheme ksTheme;
    private boolean released;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint words = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final Path surface = new Path();
    private final Path vane = new Path();
    private final Path shaft = new Path();
    private final Path nib = new Path();
    private final Path star = new Path();
    private final RectF rect = new RectF();
    private final float[] barbs;
    private final float[] specks = new float[SPECKS * 2];
    private final float[] speckPoints = new float[SPECKS * 2];
    private final Random random = new Random(7);

    // Palette
    private boolean dark;
    private int paper, paperDeep, ink, inkSoft, seal, gold, quillColor, blotColor;

    // Animation
    private long lastFrame;
    private float time;
    private float shownLevel;
    private float mouthOpen, lookX, lookY, blink, blinkPhase, blinkTimer = 2f, bubbleTimer;
    private final float[] dropX = new float[DROPS], dropY = new float[DROPS], dropVX = new float[DROPS], dropVY = new float[DROPS];
    private final float[] dropLife = new float[DROPS], dropMax = new float[DROPS], dropR = new float[DROPS];

    // Text
    private final TextBlock statusBlock = new TextBlock();
    private final TextBlock commandBlock = new TextBlock();
    private final TextBlock answerBlock = new TextBlock();
    private float revealed;
    private String revealedFor = "";

    // Backdrop cache
    private RadialGradient vignette;
    private int vignetteKey;

    FederView(Context context, KsTheme theme, FederSkin.Turn turn, boolean docked) {
        super(context);
        this.turn = turn;
        this.docked = docked;
        this.ksTheme = theme;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        this.dp = metrics.density;
        this.screenWidth = metrics.widthPixels;
        barbs = buildQuill();
        for (int i = 0; i < SPECKS * 2; i++) specks[i] = random.nextFloat();
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        palette();
    }

    void setKsTheme(KsTheme theme) {
        ksTheme = theme;
        palette();
        invalidate();
    }

    void release() {
        released = true;
    }

    /** Called on the main thread when words or settings change. */
    void textChanged() {
        if (docked) requestLayout();
        invalidate();
    }

    private void palette() {
        String theme = turn.theme;
        dark = "dark".equals(theme) || (!"light".equals(theme) && ksTheme != null && ksTheme.isDark());
        if (dark) {
            paper = 0xFF18212C;
            paperDeep = 0xFF05080C;
            ink = 0xFFF3E8CF;
            inkSoft = 0xFFB9AC92;
            seal = 0xFFD0695C;
            gold = 0xFFE2B65A;
            quillColor = 0xFFE9DDC2;
            blotColor = 0xFF06090D;
        } else {
            paper = 0xFFEDE0C4;
            paperDeep = 0xFFB89B6A;
            ink = 0xFF2B211C;
            inkSoft = 0xFF6A5847;
            seal = 0xFF8C2A22;
            gold = 0xFFC9922E;
            quillColor = 0xFFF8F0DE;
            blotColor = 0xFF231B17;
        }
        vignette = null;
    }

    // ---------------------------------------------------------------- quill

    /** Rachis bend at u (0 at the vane's foot, 1 at the tip). */
    private static float bend(float u) {
        return 0.035f * u * u;
    }

    private static float profile(float u) {
        float s = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, u)));
        return (float) Math.pow(s, 0.7) * (1f - 0.2f * u);
    }

    private static float vaneY(float u) {
        return -0.26f - 0.74f * u;
    }

    /** Builds the quill in unit coordinates, nib at the origin, pointing up. Returns the barb lines. */
    private float[] buildQuill() {
        int steps = 28;
        vane.reset();
        for (int i = 0; i <= steps; i++) {
            float u = i / (float) steps;
            float x = bend(u) + 0.125f * profile(u);
            float y = vaneY(u) - 0.02f * profile(u);
            if (i == 0) vane.moveTo(x, y);
            else vane.lineTo(x, y);
        }
        for (int i = steps; i >= 0; i--) {
            float u = i / (float) steps;
            vane.lineTo(bend(u) - 0.07f * profile(u), vaneY(u));
        }
        vane.close();

        shaft.reset();
        shaft.moveTo(0f, -0.07f);
        shaft.lineTo(0f, vaneY(0f));
        for (int i = 1; i <= steps; i++) {
            float u = i / (float) steps;
            shaft.lineTo(bend(u), vaneY(u));
        }

        nib.reset();
        nib.moveTo(0f, 0f);
        nib.lineTo(-0.022f, -0.075f);
        nib.quadTo(0f, -0.09f, 0.022f, -0.075f);
        nib.close();

        int count = 17;
        float[] out = new float[count * 8];
        for (int k = 0; k < count; k++) {
            float u = (k + 1) / (float) (count + 1);
            float cx = bend(u), cy = vaneY(u), f = profile(u);
            out[k * 8] = cx;
            out[k * 8 + 1] = cy;
            out[k * 8 + 2] = cx + 0.122f * f;
            out[k * 8 + 3] = cy - 0.05f - 0.02f * f;
            out[k * 8 + 4] = cx;
            out[k * 8 + 5] = cy;
            out[k * 8 + 6] = cx - 0.068f * f;
            out[k * 8 + 7] = cy - 0.04f;
        }
        return out;
    }

    private void drawQuill(Canvas canvas, float x, float y, float length, float degrees) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(degrees);
        canvas.scale(length, length);
        float unit = 1f / length;
        // Soft shadow of the vane on the paper.
        canvas.save();
        canvas.translate(0.018f, 0.012f);
        fill.setShader(null);
        fill.setColor(withAlpha(ink, 26));
        canvas.drawPath(vane, fill);
        canvas.restore();
        fill.setColor(quillColor);
        canvas.drawPath(vane, fill);
        line.setColor(withAlpha(ink, 70));
        line.setStrokeWidth(0.8f * dp * unit);
        canvas.drawLines(barbs, line);
        line.setColor(withAlpha(ink, 200));
        line.setStrokeWidth(1.1f * dp * unit);
        canvas.drawPath(vane, line);
        line.setColor(withAlpha(dark ? 0xFFCDBB95 : 0xFF6B5642, 255));
        line.setStrokeWidth(2.0f * dp * unit);
        canvas.drawPath(shaft, line);
        fill.setColor(dark ? gold : ink);
        canvas.drawPath(nib, fill);
        line.setColor(withAlpha(paper, 160));
        line.setStrokeWidth(0.7f * dp * unit);
        canvas.drawLine(0f, -0.012f, 0f, -0.06f, line);
        canvas.restore();
    }

    // ----------------------------------------------------------- inkwell

    /** Bubbles that rise out of the neck while the inkwell thinks. */
    private void spawnBubble(float x, float y, float s) {
        for (int i = 0; i < DROPS; i++) {
            if (dropLife[i] > 0f) continue;
            dropX[i] = x;
            dropY[i] = y;
            dropVX[i] = (random.nextFloat() - 0.5f) * s * 0.12f;
            dropVY[i] = -s * (0.30f + random.nextFloat() * 0.25f);
            dropMax[i] = dropLife[i] = 1.0f + random.nextFloat() * 0.6f;
            dropR[i] = s * (0.012f + random.nextFloat() * 0.016f);
            return;
        }
    }

    private void stepBubbles(float dt, float s) {
        for (int i = 0; i < DROPS; i++) {
            if (dropLife[i] <= 0f) continue;
            dropLife[i] -= dt;
            dropVX[i] += (float) Math.sin(time * 3f + i) * s * 0.15f * dt;
            dropX[i] += dropVX[i] * dt;
            dropY[i] += dropVY[i] * dt;
        }
    }

    private void drawBubbles(Canvas canvas, float s) {
        for (int i = 0; i < DROPS; i++) {
            if (dropLife[i] <= 0f) continue;
            float k = dropLife[i] / dropMax[i];
            float grow = 1f + (1f - k) * 0.6f;
            line.setColor(withAlpha(dark ? gold : ink, Math.round(200 * Math.min(1f, k * 1.6f))));
            line.setStrokeWidth(Math.max(1f, s * 0.008f));
            canvas.drawCircle(dropX[i], dropY[i], dropR[i] * grow, line);
            fill.setColor(withAlpha(0xFFFFFFFF, Math.round(150 * k)));
            canvas.drawCircle(dropX[i] - dropR[i] * 0.35f, dropY[i] - dropR[i] * 0.35f, dropR[i] * 0.22f, fill);
        }
    }

    private void buildStar(float size) {
        star.reset();
        star.moveTo(0f, -size);
        star.quadTo(0f, 0f, size, 0f);
        star.quadTo(0f, 0f, 0f, size);
        star.quadTo(0f, 0f, -size, 0f);
        star.quadTo(0f, 0f, 0f, -size);
        star.close();
    }

    /** The bottle: a squat glass body with shoulders and a short neck, centred on (cx, cy). */
    private void buildBody(float cx, float cy, float s) {
        float l = cx - 0.31f * s, r = cx + 0.31f * s, nl = cx - 0.12f * s, nr = cx + 0.12f * s;
        float top = cy - 0.27f * s, shoulder = cy - 0.20f * s, side = cy - 0.02f * s;
        float bottom = cy + 0.26f * s, corner = 0.07f * s;
        body.reset();
        body.moveTo(nl, top);
        body.lineTo(nl, shoulder);
        body.cubicTo(nl - 0.10f * s, shoulder + 0.02f * s, l, cy - 0.15f * s, l, side);
        body.lineTo(l, bottom - corner);
        body.quadTo(l, bottom, l + corner, bottom);
        body.lineTo(r - corner, bottom);
        body.quadTo(r, bottom, r, bottom - corner);
        body.lineTo(r, side);
        body.cubicTo(r, cy - 0.15f * s, nr + 0.10f * s, shoulder + 0.02f * s, nr, shoulder);
        body.lineTo(nr, top);
        body.close();
    }

    private void stepFace(float dt, String state, float lv) {
        boolean responding = "responding".equals(state);
        float targetMouth;
        if (responding) targetMouth = turn.reactive ? Math.min(1f, lv * 1.7f) : 0.35f + 0.35f * (float) Math.sin(time * 13f);
        else targetMouth = 0f;
        mouthOpen += (targetMouth - mouthOpen) * Math.min(1f, dt * 18f);

        float tx, ty;
        if ("processing".equals(state)) {
            tx = 0.8f * (float) Math.sin(time * 1.6f);
            ty = -0.8f;
        } else if ("listening".equals(state)) {
            tx = 0.25f * (float) Math.sin(time * 0.7f);
            ty = 0.1f;
        } else if (responding) {
            tx = 0.2f * (float) Math.sin(time * 0.9f);
            ty = 0f;
        } else {
            tx = 0f;
            ty = 0f;
        }
        lookX += (tx - lookX) * Math.min(1f, dt * 5f);
        lookY += (ty - lookY) * Math.min(1f, dt * 5f);

        blinkTimer -= dt;
        if (blinkTimer <= 0f) {
            blinkTimer = 2.5f + random.nextFloat() * 3f;
            blinkPhase = 0.18f;
        }
        if (blinkPhase > 0f) {
            blinkPhase -= dt;
            float p = 1f - Math.max(0f, blinkPhase) / 0.18f;
            blink = p < 0.5f ? p * 2f : (1f - p) * 2f;
        } else {
            blink = 0f;
        }
    }

    private void drawFace(Canvas canvas, float cx, float cy, float s, String state) {
        boolean listening = "listening".equals(state);
        boolean processing = "processing".equals(state);
        int cream = 0xFFFBF5E6;
        float eyeY = cy + 0.035f * s + lookY * 0.008f * s;
        float eyeDX = 0.105f * s;
        float grow = listening ? 1.12f : 1f;
        float eyeW = 0.058f * s * grow, eyeH = 0.072f * s * grow * (1f - 0.92f * blink);
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * eyeDX + lookX * 0.01f * s;
            fill.setColor(cream);
            rect.set(ex - eyeW, eyeY - eyeH, ex + eyeW, eyeY + eyeH);
            canvas.drawOval(rect, fill);
            if (eyeH > 0.012f * s) {
                float px = ex + lookX * 0.022f * s, py = eyeY + lookY * 0.026f * s;
                fill.setColor(ink);
                canvas.drawCircle(px, py, 0.028f * s * grow, fill);
                fill.setColor(0xFFFFFFFF);
                canvas.drawCircle(px - 0.009f * s, py - 0.010f * s, 0.008f * s, fill);
            }
            // Brows.
            // Listening: both brows up. Thinking: one brow raised.
            float lift = listening ? 0.018f * s : processing && side < 0 ? 0.03f * s : 0f;
            float tilt = processing && side > 0 ? -0.008f * s : 0f;
            line.setColor(cream);
            line.setStrokeWidth(Math.max(1.5f, 0.013f * s));
            canvas.drawLine(ex - eyeW * 0.9f, eyeY - 0.072f * s * grow - 0.022f * s - lift + tilt,
                ex + eyeW * 0.9f, eyeY - 0.072f * s * grow - 0.030f * s - lift - tilt, line);
        }
        // Cheeks.
        fill.setColor(withAlpha(seal, 120));
        canvas.drawCircle(cx - 0.19f * s, cy + 0.125f * s, 0.028f * s, fill);
        canvas.drawCircle(cx + 0.19f * s, cy + 0.125f * s, 0.028f * s, fill);

        float my = cy + 0.165f * s;
        if (mouthOpen > 0.06f) {
            float mw = 0.10f * s * (1f - 0.25f * mouthOpen);
            float mh = 0.012f * s + 0.07f * s * mouthOpen;
            rect.set(cx - mw, my - mh, cx + mw, my + mh);
            fill.setColor(0xFF5C1A14);
            canvas.drawOval(rect, fill);
            if (mouthOpen > 0.3f) {
                fill.setColor(0xFFB04A3A);
                rect.set(cx - mw * 0.55f, my + mh * 0.15f, cx + mw * 0.55f, my + mh * 0.95f);
                canvas.drawOval(rect, fill);
            }
            line.setColor(cream);
            line.setStrokeWidth(Math.max(1.2f, 0.009f * s));
            rect.set(cx - mw, my - mh, cx + mw, my + mh);
            canvas.drawOval(rect, line);
        } else if (processing) {
            line.setColor(cream);
            line.setStrokeWidth(Math.max(1.5f, 0.013f * s));
            rect.set(cx - 0.02f * s, my - 0.03f * s, cx + 0.08f * s, my + 0.02f * s);
            canvas.drawArc(rect, 20f, 120f, false, line);
        } else {
            line.setColor(cream);
            line.setStrokeWidth(Math.max(1.5f, 0.013f * s));
            float wide = listening ? 0.045f : 0.065f;
            rect.set(cx - wide * s, my - 0.05f * s, cx + wide * s, my + 0.025f * s);
            canvas.drawArc(rect, 25f, 130f, false, line);
        }
    }

    /** The talking inkwell with its quill, centred on (cx, cy). */
    private void drawEmblem(Canvas canvas, float cx, float cy, float s, float dt, boolean small) {
        String state = turn.state;
        boolean processing = "processing".equals(state);
        boolean responding = "responding".equals(state);
        float lv = shownLevel;
        stepFace(dt, state, lv);

        float bottom = cy + 0.26f * s;
        // Shadow on the paper.
        fill.setShader(null);
        fill.setColor(withAlpha(ink, small ? 26 : 34));
        rect.set(cx - 0.36f * s, bottom - 0.03f * s, cx + 0.36f * s, bottom + 0.05f * s);
        canvas.drawOval(rect, fill);

        // Squash and stretch: it leans in while listening and bounces while it speaks.
        float stretch = "listening".equals(state) ? 0.07f * lv : responding ? 0.05f * mouthOpen : 0f;
        float breathe = 0.012f * (float) Math.sin(time * 2.0f);
        float sy = 1f + stretch + breathe, sx = 1f - stretch * 0.45f - breathe * 0.5f;
        canvas.save();
        canvas.translate(cx, bottom);
        canvas.scale(sx, sy);
        canvas.translate(-cx, -bottom);

        // The quill stands in the ink, behind the glass.
        float wiggle = responding ? 4f * (float) Math.sin(time * 9f) * Math.max(0.3f, mouthOpen)
            : processing ? 6f * (float) Math.sin(time * 2.4f) : 2.5f * (float) Math.sin(time * 1.3f);
        float bob = processing ? 0.02f * s * (float) Math.sin(time * 4.8f) : 0f;
        drawQuill(canvas, cx + 0.02f * s, cy - 0.04f * s + bob, s * (small ? 0.80f : 1.05f), 24f + wiggle);

        buildBody(cx, cy, s);
        fill.setColor(withAlpha(dark ? 0xFF2A3644 : 0xFFF7F0DF, dark ? 170 : 175));
        canvas.drawPath(body, fill);

        // Ink inside the glass, with a restless surface, rising bubbles and the face.
        canvas.save();
        canvas.clipPath(body);
        float l = cx - 0.32f * s, r = cx + 0.32f * s;
        float surfaceY = cy - 0.115f * s - 0.025f * s * lv;
        float amp = s * (0.007f + 0.022f * lv + (processing ? 0.012f : 0f));
        surface.reset();
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            float x = l + (r - l) * i / steps;
            float y = surfaceY + amp * (float) Math.sin((x - cx) / s * 18f + time * 3.2f)
                + amp * 0.5f * (float) Math.sin((x - cx) / s * 31f - time * 2.1f);
            if (i == 0) surface.moveTo(x, y);
            else surface.lineTo(x, y);
        }
        surface.lineTo(r, bottom + s);
        surface.lineTo(l, bottom + s);
        surface.close();
        fill.setColor(blotColor);
        canvas.drawPath(surface, fill);
        if (processing || "listening".equals(state)) {
            int count = processing ? 7 : 3;
            for (int k = 0; k < count; k++) {
                float phase = (time * (processing ? 0.55f : 0.3f) + k / (float) count) % 1f;
                float by = bottom - 0.03f * s - phase * (bottom - surfaceY - 0.03f * s);
                float bx = cx + 0.20f * s * (float) Math.sin(k * 2.3f + time * 0.8f) * (k % 2 == 0 ? 1f : -1f);
                line.setColor(withAlpha(0xFFFBF5E6, Math.round(90 * (1f - phase))));
                line.setStrokeWidth(Math.max(1f, s * 0.006f));
                canvas.drawCircle(bx, by, s * 0.011f * (0.6f + phase), line);
            }
        }
        drawFace(canvas, cx, cy, s, state);
        canvas.restore();

        // Glass outline, lip and highlights.
        line.setColor(withAlpha(dark ? ink : ink, 220));
        line.setStrokeWidth(Math.max(1.5f, s * 0.011f));
        canvas.drawPath(body, line);
        rect.set(cx - 0.155f * s, cy - 0.315f * s, cx + 0.155f * s, cy - 0.255f * s);
        fill.setColor(withAlpha(dark ? 0xFF3A4757 : 0xFFE9DDC2, 255));
        canvas.drawRoundRect(rect, 0.025f * s, 0.025f * s, fill);
        canvas.drawRoundRect(rect, 0.025f * s, 0.025f * s, line);
        line.setColor(withAlpha(0xFFFFFFFF, dark ? 90 : 170));
        line.setStrokeWidth(Math.max(1.5f, s * 0.018f));
        canvas.drawLine(cx - 0.255f * s, cy - 0.04f * s, cx - 0.255f * s, cy + 0.17f * s, line);
        line.setStrokeWidth(Math.max(1f, s * 0.012f));
        canvas.drawLine(cx - 0.20f * s, cy - 0.165f * s, cx - 0.235f * s, cy - 0.12f * s, line);
        canvas.restore();

        // Thinking bubbles out of the neck.
        if (processing) {
            bubbleTimer += dt;
            if (bubbleTimer > (small ? 0.45f : 0.3f)) {
                bubbleTimer = 0f;
                spawnBubble(cx + (random.nextFloat() - 0.5f) * 0.12f * s, cy - 0.32f * s, s);
            }
        }
        stepBubbles(dt, s);
        drawBubbles(canvas, s);

        // Sparkles around it.
        if (!small) {
            float spin = time * (processing ? 0.5f : 0.12f);
            for (int k = 0; k < 7; k++) {
                float a = k / 7f * TWO_PI + spin;
                float twinkle = 0.5f + 0.5f * (float) Math.sin(time * (1.3f + k * 0.37f) + k * 1.7f);
                float boost = processing ? 0.8f : responding ? 0.5f + 0.5f * mouthOpen : 0.35f + 0.65f * lv;
                float alpha = twinkle * boost;
                if (alpha < 0.05f) continue;
                float dist = s * (0.62f + 0.05f * (float) Math.sin(time * 0.7f + k));
                buildStar(s * 0.030f * (0.6f + 0.7f * twinkle));
                canvas.save();
                canvas.translate(cx + (float) Math.cos(a) * dist, cy + (float) Math.sin(a) * dist * 0.75f);
                fill.setColor(withAlpha(gold, Math.round(235 * Math.min(1f, alpha))));
                canvas.drawPath(star, fill);
                canvas.restore();
            }
        }
    }
    // -------------------------------------------------------------- drawing

    private float textSize(float sp) {
        return sp * dp * Math.max(0.5f, Math.min(2f, turn.textScale));
    }

    private void stepReveal(float dt) {
        String answer = turn.answer;
        if (answer.isEmpty()) {
            revealed = 0f;
            revealedFor = "";
            return;
        }
        if (!answer.startsWith(revealedFor)) revealed = 0f;
        revealedFor = answer;
        float target = answerBlock.wordCount();
        if (revealed < target) {
            float rate = 7f + 1.2f * (target - revealed);
            revealed = Math.min(target, revealed + rate * dt);
        }
    }

    private String status() {
        String state = turn.state;
        if ("listening".equals(state)) return "Ich höre zu …";
        if ("processing".equals(state)) return "Das Tintenfass überlegt …";
        return "";
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (!docked) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
            return;
        }
        int width = Math.round(Math.min(560f * dp, screenWidth - 48f * dp));
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) width = Math.min(width, MeasureSpec.getSize(widthSpec));
        int height = Math.round(dockedHeight(width));
        if (MeasureSpec.getMode(heightSpec) == MeasureSpec.AT_MOST || MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) {
            height = Math.min(height, MeasureSpec.getSize(heightSpec));
        }
        setMeasuredDimension(width, height);
    }

    private float dockPad() {
        return 16f * dp;
    }

    private float dockEmblem() {
        return 78f * dp;
    }

    private float dockTextWidth(float width) {
        return width - dockPad() * 2f - dockEmblem() - 14f * dp;
    }

    /** Height the docked card needs for the current words. */
    private float dockedHeight(float width) {
        float textWidth = dockTextWidth(width);
        float content = 0f;
        String command = turn.command;
        String answer = turn.answer.isEmpty() ? turn.tool : turn.answer;
        if (!command.isEmpty()) {
            words.setTypeface(turn.italic);
            words.setTextSize(textSize(15f));
            content += words.getTextSize() * 1.3f;
        }
        words.setTypeface(turn.regular);
        words.setTextSize(textSize(19f));
        if (!answer.isEmpty()) {
            answerBlock.layout(answer, words, textWidth);
            content += Math.min(3, Math.max(1, answerBlock.lineCount)) * words.getTextSize() * 1.28f;
        } else {
            content += words.getTextSize() * 1.3f;
        }
        return dockPad() * 2f + Math.max(dockEmblem(), content);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (released) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0L ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        time += dt;
        String themeNow = turn.theme;
        boolean wantDark = "dark".equals(themeNow) || (!"light".equals(themeNow) && ksTheme != null && ksTheme.isDark());
        if (wantDark != dark) palette();
        float target = turn.reactive ? Math.max(0f, Math.min(1f, turn.level)) : 0f;
        shownLevel += (target - shownLevel) * Math.min(1f, dt * 9f);

        if (docked) drawDocked(canvas, dt);
        else drawFull(canvas, dt);
        postInvalidateOnAnimation();
    }

    private void drawFull(Canvas canvas, float dt) {
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float bg = turn.background != null ? Math.max(0f, Math.min(1f, turn.background)) : DEFAULT_BACKGROUND;

        // Parchment, darker towards the edges, with a little grain.
        fill.setShader(null);
        fill.setColor(withAlpha(paper, Math.round(255 * bg)));
        canvas.drawRect(0f, 0f, w, h, fill);
        int key = Math.round(w) * 31 + Math.round(h) * 17 + Math.round(bg * 100) + (dark ? 7 : 0);
        if (vignette == null || key != vignetteKey) {
            vignetteKey = key;
            vignette = new RadialGradient(w / 2f, h * 0.42f, Math.max(w, h) * 0.78f,
                new int[] {withAlpha(paperDeep, 0), withAlpha(paperDeep, 0), withAlpha(paperDeep, Math.round(170 * bg))},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        }
        fill.setShader(vignette);
        canvas.drawRect(0f, 0f, w, h, fill);
        fill.setShader(null);
        for (int i = 0; i < SPECKS; i++) {
            speckPoints[i * 2] = specks[i * 2] * w;
            speckPoints[i * 2 + 1] = specks[i * 2 + 1] * h;
        }
        line.setColor(withAlpha(ink, Math.round(22 * bg)));
        line.setStrokeWidth(1.6f * dp);
        canvas.drawPoints(speckPoints, line);

        // Printer's frame with corner ornaments.
        float outer = 18f * dp, inner = 25f * dp;
        line.setColor(withAlpha(ink, Math.round(120 * bg)));
        line.setStrokeWidth(1.4f * dp);
        canvas.drawRect(outer, outer, w - outer, h - outer, line);
        line.setColor(withAlpha(ink, Math.round(70 * bg)));
        line.setStrokeWidth(0.7f * dp);
        canvas.drawRect(inner, inner, w - inner, h - inner, line);
        float c = 21.5f * dp;
        drawDiamond(canvas, c, c, 4.5f * dp, bg);
        drawDiamond(canvas, w - c, c, 4.5f * dp, bg);
        drawDiamond(canvas, c, h - c, 4.5f * dp, bg);
        drawDiamond(canvas, w - c, h - c, 4.5f * dp, bg);

        // The quill reaches about 1.05 s above the inkwell's centre; keep it inside the frame.
        float s = Math.min(Math.min(w, h) * 0.42f, h * 0.27f);
        float cx = w / 2f, cy = Math.max(h * 0.31f, s * 1.08f + 44f * dp);
        drawEmblem(canvas, cx, cy, s, dt, false);

        // Rule between emblem and words.
        float ruleY = cy + s * 0.48f;
        line.setColor(withAlpha(ink, 150));
        line.setStrokeWidth(1f * dp);
        canvas.drawLine(cx - s * 0.62f, ruleY, cx - 10f * dp, ruleY, line);
        canvas.drawLine(cx + 10f * dp, ruleY, cx + s * 0.62f, ruleY, line);
        drawDiamond(canvas, cx, ruleY, 4f * dp, 1f);

        float left = 56f * dp, right = w - 56f * dp, width = right - left;
        float top = ruleY + 26f * dp, bottom = h - 60f * dp;
        String command = turn.command;
        String answer = turn.answer;
        String tool = turn.tool;

        float y = top;
        if (!command.isEmpty()) {
            words.setTypeface(turn.italic);
            words.setTextSize(textSize(23f));
            float lh = words.getTextSize() * 1.3f;
            commandBlock.layout("„" + command + "“", words, width);
            int shown = commandBlock.shownLines(2, Float.MAX_VALUE);
            commandBlock.draw(canvas, words, left, y, width, 2, Float.MAX_VALUE, true, inkSoft, lh, 0f);
            y += shown * lh + 16f * dp;
        }

        float toolSpace = 0f;
        if (!tool.isEmpty()) {
            words.setTypeface(turn.italic);
            words.setTextSize(textSize(17f));
            toolSpace = words.getTextSize() * 1.3f + 12f * dp;
        }

        words.setTypeface(turn.regular);
        words.setTextSize(textSize(answer.length() > 180 ? 26f : 31f));
        answerBlock.layout(answer, words, width);
        stepReveal(dt);
        if (!answer.isEmpty()) {
            float lh = words.getTextSize() * 1.28f;
            int maxLines = Math.max(1, (int) ((bottom - toolSpace - y) / lh));
            answerBlock.draw(canvas, words, left, y, width, maxLines, revealed, true, ink, lh, 5f * dp);
            y += answerBlock.shownLines(maxLines, revealed) * lh + 12f * dp;
        } else {
            String status = status();
            if (!status.isEmpty()) {
                words.setTypeface(turn.italic);
                words.setTextSize(textSize(command.isEmpty() ? 26f : 20f));
                words.setTextAlign(Paint.Align.CENTER);
                words.setColor(withAlpha(inkSoft, Math.round(160 + 80 * (float) Math.sin(time * 2.4f))));
                canvas.drawText(status, cx, y + words.getTextSize(), words);
                words.setTextAlign(Paint.Align.LEFT);
            }
        }

        if (!tool.isEmpty()) {
            words.setTypeface(turn.italic);
            words.setTextSize(textSize(17f));
            words.setTextAlign(Paint.Align.CENTER);
            words.setColor(seal);
            canvas.drawText(TextBlock.fit("— " + tool + " —", words, width), cx, bottom - 4f * dp, words);
            words.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawDocked(Canvas canvas, float dt) {
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float radius = 10f * dp;
        fill.setShader(null);
        fill.setColor(withAlpha(0xFF000000, 40));
        rect.set(2f * dp, 4f * dp, w - 2f * dp, h);
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setColor(paper);
        rect.set(0f, 0f, w - 1f * dp, h - 3f * dp);
        canvas.drawRoundRect(rect, radius, radius, fill);
        line.setColor(withAlpha(ink, 160));
        line.setStrokeWidth(1.3f * dp);
        canvas.drawRoundRect(rect, radius, radius, line);
        line.setColor(withAlpha(ink, 70));
        line.setStrokeWidth(0.6f * dp);
        rect.inset(4f * dp, 4f * dp);
        canvas.drawRoundRect(rect, radius * 0.7f, radius * 0.7f, line);

        float pad = dockPad(), box = dockEmblem();
        float boxH = h - 3f * dp;
        float s = box * 0.92f;
        drawEmblem(canvas, pad + box / 2f, boxH / 2f + s * 0.16f, s, dt, true);

        float left = pad + box + 14f * dp;
        float width = dockTextWidth(w);
        String command = turn.command;
        String answer = turn.answer.isEmpty() ? turn.tool : turn.answer;

        float content = 0f;
        float commandLine = 0f;
        if (!command.isEmpty()) {
            words.setTypeface(turn.italic);
            words.setTextSize(textSize(15f));
            commandLine = words.getTextSize() * 1.3f;
            content += commandLine;
        }
        words.setTypeface(turn.regular);
        words.setTextSize(textSize(19f));
        float lh = words.getTextSize() * 1.28f;
        answerBlock.layout(answer, words, width);
        stepReveal(dt);
        int lines = answer.isEmpty() ? 1 : Math.min(3, Math.max(1, answerBlock.lineCount));
        content += lines * lh;
        float y = Math.max(pad, (boxH - content) / 2f);

        if (!command.isEmpty()) {
            words.setTypeface(turn.italic);
            words.setTextSize(textSize(15f));
            words.setColor(inkSoft);
            canvas.drawText(TextBlock.fit("„" + command + "“", words, width), left, y + words.getTextSize() * 0.95f, words);
            y += commandLine;
        }
        words.setTypeface(turn.regular);
        words.setTextSize(textSize(19f));
        if (!answer.isEmpty()) {
            float shownReveal = turn.answer.isEmpty() ? Float.MAX_VALUE : revealed;
            answerBlock.draw(canvas, words, left, y, width, 3, shownReveal, false, turn.answer.isEmpty() ? seal : ink, lh, 3f * dp);
        } else {
            String status = status();
            if (!status.isEmpty()) {
                words.setTypeface(turn.italic);
                words.setTextSize(textSize(18f));
                words.setColor(withAlpha(inkSoft, Math.round(160 + 80 * (float) Math.sin(time * 2.4f))));
                canvas.drawText(status, left, y + words.getTextSize() * 0.95f, words);
            }
        }
        if (Math.abs(dockedHeight(w) - h) > 2f * dp) requestLayout();
    }

    private void drawDiamond(Canvas canvas, float x, float y, float size, float alpha) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(45f);
        fill.setShader(null);
        fill.setColor(withAlpha(seal, Math.round(230 * alpha)));
        canvas.drawRect(-size / 2f, -size / 2f, size / 2f, size / 2f, fill);
        canvas.restore();
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
