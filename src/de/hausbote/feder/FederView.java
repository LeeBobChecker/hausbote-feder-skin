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
 * Quill and ink. A pool of ink breathes and swells with the voice, a quill
 * hovers over it and dips in when you speak, writes looping flourishes while
 * the assistant thinks, and the answer appears word by word like fresh ink
 * on parchment. Full screen it covers the kiosk with paper; docked it is a
 * small parchment card at the bottom.
 */
final class FederView extends View {
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final int TRAIL = 72;
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
    private final Path blot = new Path();
    private final Path vane = new Path();
    private final Path shaft = new Path();
    private final Path nib = new Path();
    private final Path star = new Path();
    private final RectF rect = new RectF();
    private final float[] barbs;
    private final float[] blotX = new float[40];
    private final float[] blotY = new float[40];
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
    private float nibX = Float.NaN, nibY, lastNibX, angle = 30f;
    private final float[] trailX = new float[TRAIL];
    private final float[] trailY = new float[TRAIL];
    private final float[] trailAge = new float[TRAIL];
    private int trailHead, trailCount;
    private final float[] dropX = new float[DROPS], dropY = new float[DROPS], dropVX = new float[DROPS], dropVY = new float[DROPS];
    private final float[] dropLife = new float[DROPS], dropMax = new float[DROPS], dropR = new float[DROPS];
    private float dropTimer;

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

    // ----------------------------------------------------------- animation

    private void spawnDrop(float x, float y, float s, float strength) {
        for (int i = 0; i < DROPS; i++) {
            if (dropLife[i] > 0f) continue;
            double a = Math.toRadians(-150 + random.nextFloat() * 120);
            float speed = s * (0.55f + random.nextFloat() * 0.75f) * (0.6f + strength);
            dropX[i] = x;
            dropY[i] = y;
            dropVX[i] = (float) Math.cos(a) * speed;
            dropVY[i] = (float) Math.sin(a) * speed;
            dropMax[i] = dropLife[i] = 0.6f + random.nextFloat() * 0.5f;
            dropR[i] = s * (0.010f + random.nextFloat() * 0.018f);
            return;
        }
    }

    private void stepDrops(float dt, float s) {
        float gravity = s * 2.4f;
        for (int i = 0; i < DROPS; i++) {
            if (dropLife[i] <= 0f) continue;
            dropLife[i] -= dt;
            dropVY[i] += gravity * dt;
            dropX[i] += dropVX[i] * dt;
            dropY[i] += dropVY[i] * dt;
        }
    }

    private void drawDrops(Canvas canvas) {
        for (int i = 0; i < DROPS; i++) {
            if (dropLife[i] <= 0f) continue;
            float k = dropLife[i] / dropMax[i];
            fill.setColor(withAlpha(blotColor, Math.round(235 * k)));
            canvas.drawCircle(dropX[i], dropY[i], dropR[i] * (0.55f + 0.45f * k), fill);
        }
    }

    private void pushTrail(float x, float y) {
        trailHead = (trailHead + 1) % TRAIL;
        trailX[trailHead] = x;
        trailY[trailHead] = y;
        trailAge[trailHead] = 0f;
        if (trailCount < TRAIL) trailCount++;
    }

    private void drawTrail(Canvas canvas, float dt, float s) {
        float life = 1.7f;
        for (int k = 0; k < trailCount; k++) trailAge[(trailHead - k + TRAIL) % TRAIL] += dt;
        for (int k = 0; k < trailCount - 1; k++) {
            int a = (trailHead - k + TRAIL) % TRAIL;
            int b = (trailHead - k - 1 + TRAIL) % TRAIL;
            float age = trailAge[b];
            if (age >= life) {
                trailCount = k + 1;
                break;
            }
            float f = 1f - age / life;
            line.setColor(withAlpha(dark ? gold : ink, Math.round(215 * f)));
            line.setStrokeWidth(Math.max(1f, s * 0.016f * (0.45f + 0.55f * f)));
            canvas.drawLine(trailX[b], trailY[b], trailX[a], trailY[a], line);
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

    /** Ink pool, sparkles, droplets, writing trail and quill around (cx, cy). */
    private void drawEmblem(Canvas canvas, float cx, float cy, float s, float dt, boolean small) {
        String state = turn.state;
        boolean processing = "processing".equals(state);
        boolean revealing = answerBlock.wordCount() > 0 && revealed < answerBlock.wordCount();
        boolean writing = processing || ("responding".equals(state) && revealing);
        float lv = shownLevel;
        float r = s * 0.20f * (1f + 0.30f * lv);

        // Shadow under the floating ink.
        fill.setShader(null);
        fill.setColor(withAlpha(ink, small ? 22 : 30));
        rect.set(cx - r * 1.15f, cy + r * 1.2f, cx + r * 1.15f, cy + r * 1.5f);
        canvas.drawOval(rect, fill);

        // The pool of ink: a soft, restless blot.
        int n = blotX.length;
        for (int i = 0; i < n; i++) {
            float a = i / (float) n * TWO_PI;
            float wobble = 1f
                + 0.07f * (float) Math.sin(3 * a + time * 1.1f)
                + 0.05f * (float) Math.sin(5 * a - time * 0.8f)
                + 0.03f * (float) Math.sin(8 * a + time * 1.9f)
                + lv * 0.09f * (float) Math.sin(6 * a + time * 6f);
            blotX[i] = cx + (float) Math.cos(a) * r * wobble;
            blotY[i] = cy + (float) Math.sin(a) * r * wobble * 0.92f;
        }
        blot.reset();
        blot.moveTo((blotX[n - 1] + blotX[0]) / 2f, (blotY[n - 1] + blotY[0]) / 2f);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            blot.quadTo(blotX[i], blotY[i], (blotX[i] + blotX[j]) / 2f, (blotY[i] + blotY[j]) / 2f);
        }
        blot.close();
        fill.setColor(blotColor);
        canvas.drawPath(blot, fill);
        if (dark) {
            line.setColor(withAlpha(gold, 110));
            line.setStrokeWidth(1.2f * dp);
            canvas.drawPath(blot, line);
        }
        // Sheen.
        line.setColor(withAlpha(dark ? gold : paper, dark ? 120 : 95));
        line.setStrokeWidth(Math.max(1f, s * 0.014f));
        rect.set(cx - r * 0.62f, cy - r * 0.64f, cx + r * 0.18f, cy + r * 0.08f);
        canvas.drawArc(rect, 200f, 55f, false, line);
        fill.setColor(withAlpha(dark ? gold : paper, dark ? 140 : 110));
        canvas.drawCircle(cx - r * 0.12f, cy - r * 0.48f, Math.max(1f, s * 0.011f), fill);

        // Sparkles around the ink.
        if (!small) {
            float spin = time * (processing ? 0.55f : 0.12f);
            for (int k = 0; k < 7; k++) {
                float a = k / 7f * TWO_PI + spin;
                float twinkle = 0.5f + 0.5f * (float) Math.sin(time * (1.3f + k * 0.37f) + k * 1.7f);
                float boost = processing ? 0.8f : 0.35f + 0.65f * lv;
                float alpha = twinkle * boost;
                if (alpha < 0.05f) continue;
                float dist = s * (0.58f + 0.05f * (float) Math.sin(time * 0.7f + k));
                buildStar(s * 0.032f * (0.6f + 0.7f * twinkle));
                canvas.save();
                canvas.translate(cx + (float) Math.cos(a) * dist, cy + (float) Math.sin(a) * dist * 0.8f);
                fill.setColor(withAlpha(gold, Math.round(235 * Math.min(1f, alpha))));
                canvas.drawPath(star, fill);
                canvas.restore();
            }
        }

        // Where the nib wants to be.
        float length = s * (small ? 0.85f : 1.15f);
        float baseX = cx + s * 0.02f;
        float baseY = cy - r - s * (small ? 0.09f : 0.13f);
        float tx, ty;
        if (processing) {
            float t = time * 2.2f;
            float w = s * (small ? 0.20f : 0.30f);
            tx = cx + w * (float) Math.sin(t);
            ty = cy - r - s * (small ? 0.16f : 0.20f) + s * (small ? 0.05f : 0.07f) * (float) Math.sin(2 * t);
        } else if (writing) {
            tx = baseX + s * 0.07f * (float) Math.sin(time * 7f);
            ty = baseY - s * 0.05f + s * 0.025f * (float) Math.sin(time * 11f);
        } else {
            tx = baseX + s * 0.02f * (float) Math.sin(time * 0.9f);
            ty = baseY + s * 0.025f * (float) Math.sin(time * 1.8f) + lv * s * 0.17f;
        }
        if (Float.isNaN(nibX)) {
            nibX = tx;
            nibY = ty;
            lastNibX = tx;
        }
        float ease = Math.min(1f, dt * (processing ? 14f : 7f));
        nibX += (tx - nibX) * ease;
        nibY += (ty - nibY) * ease;
        float vx = dt > 0f ? (nibX - lastNibX) / dt : 0f;
        lastNibX = nibX;
        float targetAngle = 30f + Math.max(-14f, Math.min(14f, vx / s * 9f)) + 3f * (float) Math.sin(time * 1.3f);
        angle += (targetAngle - angle) * Math.min(1f, dt * 6f);

        if (writing) pushTrail(nibX, nibY);
        drawTrail(canvas, dt, s);

        // Ink splashes up when you speak loudly or the nib dips in.
        if (!writing && "listening".equals(state)) {
            dropTimer += dt;
            boolean dipped = nibY > cy - r * 1.08f;
            if ((lv > 0.35f || dipped) && dropTimer > (small ? 0.16f : 0.09f)) {
                dropTimer = 0f;
                spawnDrop(cx + (random.nextFloat() - 0.5f) * r * 0.8f, cy - r * 0.85f, s, lv);
            }
        }
        stepDrops(dt, s);
        drawDrops(canvas);

        drawQuill(canvas, nibX, nibY, length, angle);
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
        if ("processing".equals(state)) return "Die Feder überlegt …";
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

        // The quill reaches about 1.33 s above the ink; keep it inside the frame.
        float s = Math.min(Math.min(w, h) * 0.34f, h * 0.24f);
        float cx = w / 2f, cy = Math.max(h * 0.30f, s * 1.33f + 44f * dp);
        drawEmblem(canvas, cx, cy, s, dt, false);

        // Rule between emblem and words.
        float ruleY = cy + s * 0.78f;
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
        float s = box * 0.78f;
        drawEmblem(canvas, pad + box / 2f, boxH / 2f + s * 0.28f, s, dt, true);

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
