// SPDX-License-Identifier: Apache-2.0
package de.hausbote.feder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import me.jxl.kiosk.plugins.KsTheme;

/**
 * An "Extrablatt" of Der Hausbote, built from the dashboard's own pieces:
 * the masthead with its date line, Fraktur title, stars and castle, a red
 * Bekanntmachung with a gold seal that carries the question and the answer,
 * three Wertmarken that show the stage of the turn, and a Billett for the
 * action the assistant took. Docked it is a small Bekanntmachung at the
 * bottom of the screen.
 */
final class BoteView extends View {
    // The Hausbote palette.
    static final int PAPIER = 0xFFEDE0C4, KARTON = 0xFFE3D3AE, HELL = 0xFFF3E8CF, TINTE = 0xFF2B211C, CREME = 0xFFF5ECD7;
    static final int ROT = 0xFF8C2A22, GRUEN = 0xFF2E4A3B, BLAU = 0xFF23364A, SENF = 0xFFD9A441, PETROL = 0xFF1E5B66;
    static final int GLUT = 0xFFF5D77A, VIGNETTE = 0xFFB89B6A;

    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float DEFAULT_BACKGROUND = 0.97f;
    private static final int SPECKS = 220;
    private static final String[] STAGE_NAME = {"Zuhören", "Setzen", "Drucken"};
    private static final String[] STAGE_NR = {"I", "II", "III"};
    private static final int[] STAGE_COLOR = {SENF, GRUEN, BLAU};

    private final FederSkin.Turn turn;
    private final boolean docked;
    private final float dp;
    private final int screenWidth;
    private boolean released;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path seal = new Path();
    private final Path sealStar = new Path();
    private final Path star = new Path();
    private final Path castle = new Path();
    private final float[] windows = {63, 28, 72, 28, 66, 36, 124, 24, 130, 34, 159, 38, 24, 38, 91, 40, 104, 40, 181, 44};
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final float[] specks = new float[SPECKS * 2];
    private final float[] speckPoints = new float[SPECKS * 2];
    private final Random random = new Random(11);
    private final TextBlock headBlock = new TextBlock();
    private final TextBlock answerBlock = new TextBlock();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMAN);
    private final SimpleDateFormat shortFormat = new SimpleDateFormat("d. MMMM yyyy", Locale.GERMAN);
    private String dateShort = "";
    private String fitKey = "";
    private float fitHead, fitAnswer;

    private long lastFrame;
    private float time;
    private float shownLevel;
    private float revealed;
    private String revealedFor = "";
    private String headline = "";
    private float headlineTime = -10f;
    private float sealTime = -10f;
    private String sealFor = "";
    private String toolSeen = "";
    private float toolTime = -10f;
    private int stageShown = -1;
    private float stageTime;
    private LinearGradient sheen;
    private float sheenWidth;
    private RadialGradient vignette;
    private int vignetteKey;
    private String dateLine = "";
    private long dateLineAt;

    BoteView(Context context, KsTheme theme, FederSkin.Turn turn, boolean docked) {
        super(context);
        this.turn = turn;
        this.docked = docked;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        this.dp = metrics.density;
        this.screenWidth = metrics.widthPixels;
        for (int i = 0; i < SPECKS * 2; i++) specks[i] = random.nextFloat();
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        buildShapes();
    }

    void setKsTheme(KsTheme theme) {
        invalidate();
    }

    void release() {
        released = true;
    }

    void textChanged() {
        if (docked) requestLayout();
        invalidate();
    }

    // -------------------------------------------------------------- shapes

    /** The dashboard's gold seal, star and castle, in their SVG units. */
    private void buildShapes() {
        seal.reset();
        seal.moveTo(24, 2);
        seal.cubicTo(30, 1, 33, 5, 38, 6);
        seal.cubicTo(44, 8, 46, 13, 45, 19);
        seal.cubicTo(47, 25, 45, 30, 42, 35);
        seal.cubicTo(40, 42, 33, 46, 27, 45);
        seal.cubicTo(21, 47, 14, 45, 10, 40);
        seal.cubicTo(4, 36, 2, 29, 3, 23);
        seal.cubicTo(1, 16, 5, 10, 10, 7);
        seal.cubicTo(14, 3, 19, 3, 24, 2);
        seal.close();
        sealStar.reset();
        sealStar.moveTo(24, 10);
        sealStar.lineTo(27, 21);
        sealStar.lineTo(38, 24);
        sealStar.lineTo(27, 27);
        sealStar.lineTo(24, 38);
        sealStar.lineTo(21, 27);
        sealStar.lineTo(10, 24);
        sealStar.lineTo(21, 21);
        sealStar.close();
        star.reset();
        star.moveTo(27, 2);
        star.lineTo(32, 22);
        star.lineTo(52, 27);
        star.lineTo(32, 32);
        star.lineTo(27, 52);
        star.lineTo(22, 32);
        star.lineTo(2, 27);
        star.lineTo(22, 22);
        star.close();
        castle.reset();
        rectPath(castle, 0, 52, 200, 8);
        rectPath(castle, 18, 32, 16, 22);
        triPath(castle, 16, 32, 26, 8, 36, 32);
        rectPath(castle, 58, 22, 22, 32);
        triPath(castle, 55, 22, 69, -2, 83, 22);
        rectPath(castle, 86, 36, 30, 18);
        rectPath(castle, 118, 18, 18, 36);
        triPath(castle, 115, 18, 127, -6, 139, 18);
        rectPath(castle, 152, 32, 18, 22);
        triPath(castle, 150, 32, 161, 12, 172, 32);
        rectPath(castle, 174, 40, 22, 14);
    }

    private static void rectPath(Path p, float x, float y, float w, float h) {
        p.moveTo(x, y);
        p.lineTo(x + w, y);
        p.lineTo(x + w, y + h);
        p.lineTo(x, y + h);
        p.close();
    }

    private static void triPath(Path p, float x1, float y1, float x2, float y2, float x3, float y3) {
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.close();
    }

    /** Four-point sparkle centred on the origin, as on the dashboard. */
    private void sparkle(Canvas canvas, float x, float y, float size, int color) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(size / 54f, size / 54f);
        canvas.translate(-27f, -27f);
        fill.setShader(null);
        fill.setColor(color);
        canvas.drawPath(star, fill);
        canvas.restore();
    }

    // ------------------------------------------------------------- helpers

    private static int alpha(int color, float a) {
        int v = Math.max(0, Math.min(255, Math.round(Color.alpha(color) * a)));
        return Color.argb(v, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Text colour on a background, like schrift() on the dashboard. */
    private static int on(int background) {
        return background == PAPIER || background == KARTON || background == HELL || background == SENF || background == CREME ? TINTE : CREME;
    }

    private float scale() {
        return Math.max(0.5f, Math.min(2f, turn.textScale));
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** The stamp-in of the dashboard: 1.7 x, overshoot to 0.96, settle. */
    private static float stampScale(float t) {
        if (t >= 0.65f) return 1f;
        float u = t / 0.65f;
        if (u < 0.6f) return 1.7f - 0.74f * ease(u / 0.6f);
        return 0.96f + 0.04f * ease((u - 0.6f) / 0.4f);
    }

    private void setText(Typeface face, float px, int color) {
        text.setShader(null);
        text.setTypeface(face);
        text.setTextSize(px);
        text.setColor(color);
        text.setTextAlign(Paint.Align.LEFT);
        text.setLetterSpacing(0f);
    }

    private int stage() {
        String state = turn.state;
        if ("listening".equals(state)) return 0;
        if ("processing".equals(state)) return 1;
        if ("responding".equals(state) || !turn.answer.isEmpty()) return 2;
        return 0;
    }

    private String status() {
        String state = turn.state;
        if ("listening".equals(state)) return "Ich höre zu …";
        if ("processing".equals(state)) return "Wird gesetzt …";
        return "";
    }

    private String dateLine() {
        long now = System.currentTimeMillis();
        if (now - dateLineAt > 20000L || dateLine.isEmpty()) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            int h = c.get(Calendar.HOUR_OF_DAY);
            String ausgabe = h < 5 ? "Nachtausgabe" : h < 11 ? "Morgenausgabe" : h < 14 ? "Mittagsausgabe" : h < 18 ? "Nachmittagsausgabe" : h < 23 ? "Abendausgabe" : "Nachtausgabe";
            String uhr = two(h) + "." + two(c.get(Calendar.MINUTE)) + " Uhr";
            String nr = "Nr. " + c.get(Calendar.DAY_OF_YEAR) + " · ";
            dateLine = nr + dateFormat.format(new Date(now)) + "|" + ausgabe + " · " + uhr;
            dateShort = nr + shortFormat.format(new Date(now)) + "|" + uhr;
            dateLineAt = now;
        }
        return dateLine;
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private void track(float dt) {
        String answer = turn.answer;
        if (answer.isEmpty()) {
            revealed = 0f;
            revealedFor = "";
        } else {
            if (!answer.startsWith(revealedFor)) revealed = 0f;
            revealedFor = answer;
            float target = answerBlock.wordCount();
            if (revealed < target) revealed = Math.min(target, revealed + (7f + 1.2f * (target - revealed)) * dt);
        }
        // The seal stamps in when an answer arrives.
        if (!answer.isEmpty() && (sealFor.isEmpty() || !answer.startsWith(sealFor))) sealTime = time;
        sealFor = answer;
        String head = turn.command.isEmpty() ? status() : turn.command;
        if (!head.equals(headline)) {
            headline = head;
            headlineTime = time;
        }
        if (!turn.tool.equals(toolSeen)) {
            toolSeen = turn.tool;
            toolTime = time;
        }
        int st = stage();
        if (st != stageShown) {
            stageShown = st;
            stageTime = time;
        }
    }

    // ------------------------------------------------------------- measure

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (!docked) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
            return;
        }
        int width = Math.round(Math.min(560f * dp, screenWidth - 48f * dp));
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) width = Math.min(width, MeasureSpec.getSize(widthSpec));
        int height = Math.round(dockedHeight(width));
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) height = Math.min(height, MeasureSpec.getSize(heightSpec));
        setMeasuredDimension(width, height);
    }

    private float dockSeal() {
        return 46f * dp;
    }

    private float dockTextWidth(float width) {
        return width - 2f * 7f * dp - 2f * 14f * dp - dockSeal() - 14f * dp;
    }

    private float dockedHeight(float width) {
        float tw = dockTextWidth(width);
        float content = 0f;
        String head = turn.command.isEmpty() ? status() : turn.command;
        if (!head.isEmpty()) {
            setText(turn.oswald, 13f * dp * scale(), CREME);
            text.setLetterSpacing(0.12f);
            content += text.getTextSize() * 1.5f;
        }
        String answer = turn.answer.isEmpty() ? turn.tool : turn.answer;
        if (!answer.isEmpty()) {
            setText(turn.regular, 19f * dp * scale(), CREME);
            answerBlock.layout(answer, text, tw);
            content += Math.min(3, Math.max(1, answerBlock.lineCount)) * text.getTextSize() * 1.25f;
        }
        return 2f * 7f * dp + 2f * 12f * dp + Math.max(dockSeal(), content);
    }

    // ----------------------------------------------------------------- draw

    @Override protected void onDraw(Canvas canvas) {
        if (released) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0L ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        time += dt;
        float target = turn.reactive ? Math.max(0f, Math.min(1f, turn.level)) : 0f;
        shownLevel += (target - shownLevel) * Math.min(1f, dt * 10f);
        if (docked) drawDocked(canvas, dt);
        else drawFull(canvas, dt);
        postInvalidateOnAnimation();
    }

    private void drawFull(Canvas canvas, float dt) {
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float bg = turn.background != null ? Math.max(0f, Math.min(1f, turn.background)) : DEFAULT_BACKGROUND;
        drawPaper(canvas, w, h, bg);

        float m = 16f * dp;
        float left = m, right = w - m, width = right - left;
        float y = m;

        // Date line.
        float leisteH = 28f * dp;
        drawLeiste(canvas, left, y, right, y + leisteH);
        y += leisteH;

        // Title row with stars, castle and sparkles.
        float title = Math.max(44f * dp, Math.min(Math.min(92f * dp, width * 0.095f), h * 0.085f));
        float rowH = title * 1.18f;
        drawTitle(canvas, left, y + 4f * dp, right, rowH, title);
        y += 4f * dp + rowH;

        // Motto band with the stage of the turn.
        String state = turn.state;
        String band = "listening".equals(state) ? "Der Hausbote hört zu"
            : "processing".equals(state) ? "Die Setzerei arbeitet"
            : turn.announcement ? "Durchsage" : "Extrablatt";
        drawBand(canvas, left, y + 2f * dp, right, band);
        y += 2f * dp + 26f * dp + 16f * dp;

        // Bottom: Wertmarken for the stage, and a Billett for the action taken.
        float bottom = h - m;
        boolean hasTool = !turn.tool.isEmpty();
        float billettH = hasTool ? 58f * dp : 0f;
        float markenH = 62f * dp;
        float markenTop = bottom - billettH - (hasTool ? 10f * dp : 0f) - markenH;
        float plakatBottom = markenTop - 14f * dp;

        // Text sizes are fitted to the Bekanntmachung first; the reveal needs the word count.
        float inner = width - 2f * 7f * dp - 2f * 16f * dp;
        float textRoom = plakatBottom - y - 2f * 7f * dp - 24f * dp - 13f * dp * 1.6f - 6f * dp;
        fit(inner, textRoom, width);
        setText(turn.regular, fitAnswer, CREME);
        answerBlock.layout(turn.answer, text, inner);
        track(dt);

        drawPlakat(canvas, left, y, right, plakatBottom);
        drawMarken(canvas, left, markenTop, right, markenTop + markenH);
        if (hasTool) drawBillett(canvas, left, bottom - billettH, right, bottom);
    }

    /** Largest answer size (34 down to 20) at which the headline and the answer fit; cached per text. */
    private void fit(float inner, float room, float width) {
        String head = turn.command.isEmpty() ? status() : turn.command;
        String key = head + "\u0001" + turn.answer + "\u0001" + Math.round(inner) + "\u0001" + Math.round(room) + "\u0001" + scale();
        if (key.equals(fitKey)) return;
        fitKey = key;
        float s = scale();
        fitHead = Math.max(26f * dp, Math.min(40f * dp, width * 0.065f)) * s;
        float headH = 0f;
        if (!head.isEmpty()) {
            setText(turn.fat, fitHead, CREME);
            headBlock.layout(turn.command.isEmpty() ? head : "\u201e" + head + "\u201c", text, inner);
            headH = Math.min(3, headBlock.lineCount) * fitHead * 1.06f + 10f * dp;
        }
        fitAnswer = 20f * dp * s;
        if (turn.answer.isEmpty()) return;
        for (float size = 34f; size >= 20f; size -= 2f) {
            float px = size * dp * s;
            setText(turn.regular, px, CREME);
            answerBlock.layout(turn.answer, text, inner);
            if (headH + 12f * dp + answerBlock.lineCount * px * 1.26f <= room) {
                fitAnswer = px;
                return;
            }
        }
    }

    private void drawPaper(Canvas canvas, float w, float h, float bg) {
        fill.setShader(null);
        fill.setColor(alpha(PAPIER, bg));
        canvas.drawRect(0f, 0f, w, h, fill);
        int key = Math.round(w) * 31 + Math.round(h) * 17 + Math.round(bg * 100);
        if (vignette == null || key != vignetteKey) {
            vignetteKey = key;
            vignette = new RadialGradient(w / 2f, h * 0.45f, Math.max(w, h) * 0.8f,
                new int[] {alpha(VIGNETTE, 0f), alpha(VIGNETTE, 0f), alpha(VIGNETTE, 0.6f * bg)},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        fill.setShader(vignette);
        canvas.drawRect(0f, 0f, w, h, fill);
        fill.setShader(null);
        for (int i = 0; i < SPECKS; i++) {
            speckPoints[i * 2] = specks[i * 2] * w;
            speckPoints[i * 2 + 1] = specks[i * 2 + 1] * h;
        }
        line.setColor(alpha(TINTE, 0.08f * bg));
        line.setStrokeWidth(1.6f * dp);
        canvas.drawPoints(speckPoints, line);
    }

    private void drawLeiste(Canvas canvas, float l, float t, float r, float b) {
        fill.setShader(null);
        fill.setColor(BLAU);
        canvas.drawRect(l, t, r, b, fill);
        setText(turn.oswald, 12.5f * dp, CREME);
        text.setLetterSpacing(0.16f);
        String[] parts = dateLine().split("\\|");
        String first = parts[0].toUpperCase(Locale.GERMAN), last = parts.length > 1 ? parts[1].toUpperCase(Locale.GERMAN) : "";
        String mid = turn.ort == null ? "" : turn.ort.toUpperCase(Locale.GERMAN);
        float pad = 14f * dp, base = (t + b) / 2f + text.getTextSize() * 0.36f;
        float avail = r - l - 2f * pad;
        float wf = text.measureText(first), wl = text.measureText(last), wm = text.measureText(mid);
        if (wf + wl + 20f * dp > avail) {
            String[] small = dateShort.split("\\|");
            first = small[0].toUpperCase(Locale.GERMAN);
            last = small.length > 1 ? small[1].toUpperCase(Locale.GERMAN) : "";
            wf = text.measureText(first);
            wl = text.measureText(last);
        }
        if (wf + wl + 20f * dp > avail) {
            first = TextBlock.fit(first, text, avail - wl - 20f * dp);
            wf = text.measureText(first);
        }
        canvas.drawText(first, l + pad, base, text);
        canvas.drawText(last, r - pad - wl, base, text);
        float midX = (l + r) / 2f - wm / 2f;
        if (!mid.isEmpty() && l + pad + wf + 16f * dp < midX && midX + wm + 16f * dp < r - pad - wl) canvas.drawText(mid, midX, base, text);
    }

    private void drawTitle(Canvas canvas, float l, float t, float r, float rowH, float size) {
        float cx = (l + r) / 2f;
        float width = r - l;
        float processing = "processing".equals(turn.state) ? 1f : 0f;

        // Castle, faint behind the title, with flickering windows.
        float ch = rowH * 0.92f;
        float k = ch / 66f;
        float cw = 200f * k;
        if (cw > width * 0.7f) {
            k = width * 0.7f / 200f;
            cw = width * 0.7f;
            ch = 66f * k;
        }
        canvas.save();
        canvas.translate(cx - cw / 2f, t + rowH - ch + 6f * k);
        canvas.scale(k, k);
        fill.setShader(null);
        fill.setColor(alpha(TINTE, 0.15f));
        canvas.drawPath(castle, fill);
        for (int i = 0; i < windows.length; i += 2) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(time * (1.6f + (i % 6) * 0.3f) + i);
            fill.setColor(alpha(SENF, 0.15f + 0.45f * pulse));
            canvas.drawRect(windows[i], windows[i + 1], windows[i] + 3f, windows[i + 1] + 5f, fill);
        }
        canvas.restore();

        // Title in Fraktur with the gold sheen running across.
        setText(turn.fraktur, size, TINTE);
        String titel = "Der Hausbote";
        float tw = text.measureText(titel);
        float base = t + rowH * 0.5f + size * 0.34f;
        float span = tw * 0.9f;
        if (sheen == null || sheenWidth != span) {
            sheenWidth = span;
            sheen = new LinearGradient(0f, 0f, span, 0f, new int[] {TINTE, TINTE, SENF, TINTE, TINTE},
                new float[] {0f, 0.38f, 0.5f, 0.62f, 1f}, Shader.TileMode.CLAMP);
        }
        float period = processing > 0f ? 2.2f : 6f;
        float phase = (time % period) / period;
        float sweep = Math.min(1f, phase / 0.3f);
        float x0 = cx - tw / 2f;
        matrix.reset();
        matrix.setTranslate(x0 - span + sweep * (tw + span), 0f);
        sheen.setLocalMatrix(matrix);
        text.setShader(sheen);
        canvas.drawText(titel, x0, base, text);
        text.setShader(null);

        // Stars either side; they turn once in a while, and keep turning while it thinks.
        float ss = Math.max(28f * dp, Math.min(56f * dp, width * 0.06f));
        float gap = width * 0.04f;
        float turnPhase = (time % 6f) / 6f;
        float spin = processing > 0f ? time * 120f : turnPhase < 0.16f ? ease(turnPhase / 0.16f) * 360f : 0f;
        float grow = processing > 0f ? 1f : turnPhase < 0.16f ? 1f + 0.3f * (float) Math.sin(turnPhase / 0.16f * Math.PI) : 1f;
        for (int side = -1; side <= 1; side += 2) {
            float sx = side < 0 ? x0 - gap - ss / 2f : x0 + tw + gap + ss / 2f;
            float sy = t + rowH / 2f;
            canvas.save();
            canvas.translate(sx, sy);
            canvas.rotate(spin * side);
            canvas.scale(ss / 54f * grow, ss / 54f * grow);
            canvas.translate(-27f, -27f);
            fill.setShader(null);
            fill.setColor(SENF);
            canvas.drawPath(star, fill);
            line.setColor(TINTE);
            line.setStrokeWidth(2f);
            canvas.drawPath(star, line);
            fill.setColor(ROT);
            canvas.drawCircle(27f, 27f, 5f, fill);
            canvas.restore();
        }

        // Blinking sparkles and rising embers.
        float[][] spots = {{0.07f, 0.12f, 0f}, {0.21f, 0.72f, 1.6f}, {0.38f, 0.08f, 3.1f}, {0.63f, 0.76f, 0.8f}, {0.79f, 0.10f, 4.2f}, {0.93f, 0.60f, 2.4f}};
        for (float[] sp : spots) {
            float p = ((time + sp[2]) % 6f) / 6f;
            float a = p < 0.62f ? 0f : p < 0.74f ? (p - 0.62f) / 0.12f : p < 0.88f ? 1f - (p - 0.74f) / 0.14f : 0f;
            if (a <= 0f) continue;
            canvas.save();
            canvas.translate(l + sp[0] * width, t + sp[1] * rowH);
            canvas.rotate(p * 90f);
            sparkle(canvas, 0f, 0f, 14f * dp * (0.3f + 0.7f * a), alpha(SENF, a));
            canvas.restore();
        }
        float[] emberX = {0.12f, 0.27f, 0.45f, 0.58f, 0.72f, 0.88f};
        for (int i = 0; i < emberX.length; i++) {
            float dur = 6f + (i % 4);
            float p = ((time + i * 2.3f) % dur) / dur;
            float a = p < 0.15f ? p / 0.15f : p > 0.85f ? (1f - p) / 0.15f : 1f - 0.4f * (p - 0.15f) / 0.7f;
            float ex = l + emberX[i] * width + (i % 2 == 0 ? -1f : 1f) * 14f * dp * p;
            float ey = t + rowH - 4f * dp - 60f * dp * p;
            fill.setShader(null);
            fill.setColor(alpha(SENF, 0.35f * a));
            canvas.drawCircle(ex, ey, 5f * dp * (0.5f + 0.5f * p), fill);
            fill.setColor(alpha(GLUT, a));
            canvas.drawCircle(ex, ey, 2.5f * dp * (0.5f + 0.5f * p), fill);
        }
    }

    /** The motto band: double rules either side of a mustard label. */
    private void drawBand(Canvas canvas, float l, float t, float r, String label) {
        setText(turn.smallCaps, 16f * dp, TINTE);
        text.setLetterSpacing(0.06f);
        float lw = text.measureText(label);
        float boxW = lw + 28f * dp, boxH = 26f * dp;
        float cx = (l + r) / 2f;
        float cy = t + boxH / 2f;
        line.setColor(TINTE);
        line.setStrokeCap(Paint.Cap.BUTT);
        line.setStrokeWidth(1f * dp);
        canvas.drawLine(l, cy - 2.5f * dp, cx - boxW / 2f - 12f * dp, cy - 2.5f * dp, line);
        canvas.drawLine(cx + boxW / 2f + 12f * dp, cy - 2.5f * dp, r, cy - 2.5f * dp, line);
        line.setStrokeWidth(3f * dp);
        canvas.drawLine(l, cy + 1.5f * dp, cx - boxW / 2f - 12f * dp, cy + 1.5f * dp, line);
        canvas.drawLine(cx + boxW / 2f + 12f * dp, cy + 1.5f * dp, r, cy + 1.5f * dp, line);
        line.setStrokeCap(Paint.Cap.ROUND);
        fill.setShader(null);
        fill.setColor(SENF);
        canvas.drawRect(cx - boxW / 2f, t, cx + boxW / 2f, t + boxH, fill);
        line.setStrokeWidth(1f * dp);
        canvas.drawRect(cx - boxW / 2f, t, cx + boxW / 2f, t + boxH, line);
        canvas.drawText(label, cx - lw / 2f, cy + text.getTextSize() * 0.33f, text);
    }

    /** The red Bekanntmachung: rubric, the question as headline, the answer below, gold seal. */
    private void drawPlakat(Canvas canvas, float l, float t, float r, float b) {
        fill.setShader(null);
        fill.setColor(ROT);
        canvas.drawRect(l, t, r, b, fill);
        float o = 7f * dp;
        line.setColor(CREME);
        line.setStrokeWidth(1f * dp);
        canvas.drawRect(l + o, t + o, r - o, b - o, line);
        float il = l + o + 16f * dp, ir = r - o - 16f * dp, iw = ir - il, cx = (l + r) / 2f;
        float y = t + o + 12f * dp;

        setText(turn.oswald, 13f * dp, CREME);
        text.setLetterSpacing(0.3f);
        String rubric = turn.announcement ? "DURCHSAGE" : "BEKANNTMACHUNG";
        float rw = text.measureText(rubric);
        canvas.drawText(rubric, cx - rw / 2f, y + text.getTextSize(), text);
        y += text.getTextSize() * 1.6f + 6f * dp;

        // Headline and answer sit in the middle of the space below the rubric.
        float room = b - o - 12f * dp - y;
        float content = 0f;
        if (!headline.isEmpty()) {
            setText(turn.fat, fitHead, CREME);
            headBlock.layout(turn.command.isEmpty() ? headline : "\u201e" + headline + "\u201c", text, iw);
            content += Math.min(3, headBlock.lineCount) * fitHead * 1.06f + 10f * dp;
        }
        if (!turn.answer.isEmpty()) content += 12f * dp + answerBlock.lineCount * fitAnswer * 1.26f;
        // While nothing is answered yet, a large seal sits above the headline: it swells with the voice and turns while it thinks.
        float bigSeal = turn.answer.isEmpty() ? Math.min(120f * dp, Math.max(0f, room - content - 24f * dp)) : 0f;
        if (bigSeal > 40f * dp) content += bigSeal + 24f * dp;
        else bigSeal = 0f;
        if (content < room) y += (room - content) / 2f;
        if (bigSeal > 0f) {
            boolean thinking = "processing".equals(turn.state);
            float pulse = thinking ? 1f + 0.03f * (float) Math.sin(time * 5f) : 1f + 0.22f * shownLevel + 0.02f * (float) Math.sin(time * 2f);
            float rot = thinking ? time * 70f : -8f + 6f * shownLevel * (float) Math.sin(time * 9f);
            float appear = ease(Math.min(1f, time / 0.5f));
            drawSeal(canvas, cx, y + bigSeal / 2f, bigSeal * pulse * (0.6f + 0.4f * appear), rot, appear);
            y += bigSeal + 24f * dp;
        }

        // Headline: what was said, or the status while nothing was said yet. It stamps in.
        if (!headline.isEmpty()) {
            float size = fitHead;
            setText(turn.fat, size, CREME);
            String shown = turn.command.isEmpty() ? headline : "„" + headline + "“";
            headBlock.layout(shown, text, iw);
            int lines = Math.min(3, headBlock.lineCount);
            float lh = size * 1.06f;
            float hh = lines * lh;
            float st = stampScale(time - headlineTime);
            float a = Math.min(1f, (time - headlineTime) / 0.25f);
            canvas.save();
            canvas.translate(cx, y + hh / 2f);
            canvas.scale(st, st);
            canvas.rotate(-5f * (st - 1f) / 0.7f);
            canvas.translate(-cx, -(y + hh / 2f));
            int color = turn.command.isEmpty() ? alpha(CREME, 0.75f + 0.25f * (float) Math.sin(time * 2.4f)) : CREME;
            headBlock.draw(canvas, text, il, y, iw, 3, Float.MAX_VALUE, true, alpha(color, a), lh, 0f);
            canvas.restore();
            y += hh + 10f * dp;
        }

        // The answer, under a thin rule, word by word.
        if (!turn.answer.isEmpty()) {
            line.setColor(alpha(CREME, 0.8f));
            line.setStrokeWidth(1f * dp);
            canvas.drawLine(il, y, ir, y, line);
            y += 12f * dp;
            setText(turn.regular, fitAnswer, CREME);
            float lh = text.getTextSize() * 1.26f;
            int maxLines = Math.max(1, (int) ((b - o - 12f * dp - y) / lh));
            answerBlock.draw(canvas, text, il, y, iw, maxLines, revealed, true, CREME, lh, 4f * dp);
        }

        // Gold seal at the top right, stamped in with the answer.
        if (!turn.answer.isEmpty()) {
            float p = (time - sealTime) / 0.7f;
            float sc, rot, a;
            if (p < 0.7f) {
                float u = ease(p / 0.7f);
                sc = 2.4f - 1.5f * u;
                rot = -25f + 20f * u;
                a = u;
            } else {
                float u = ease((p - 0.7f) / 0.3f);
                sc = 0.9f + 0.1f * u;
                rot = -5f - 3f * u;
                a = 1f;
            }
            float size = 52f * dp;
            float sx = r - 14f * dp - size / 2f, sy = t + 12f * dp + size / 2f;
            drawSeal(canvas, sx, sy, size * sc, rot, a);
        }
    }

    private void drawSeal(Canvas canvas, float x, float y, float size, float rotation, float a) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(size / 48f, size / 48f);
        canvas.translate(-24f, -24f);
        fill.setShader(null);
        canvas.save();
        canvas.translate(0f, 2f);
        fill.setColor(alpha(0xFF000000, 0.35f * a));
        canvas.drawPath(seal, fill);
        canvas.restore();
        fill.setColor(alpha(SENF, a));
        canvas.drawPath(seal, fill);
        line.setColor(alpha(TINTE, a));
        line.setStrokeWidth(1.4f);
        canvas.drawPath(seal, line);
        line.setStrokeWidth(1.2f);
        canvas.drawCircle(24f, 24f, 14f, line);
        fill.setColor(alpha(TINTE, a));
        canvas.drawPath(sealStar, fill);
        canvas.restore();
    }

    /** Three Wertmarken: Zuhoeren, Setzen, Drucken. The current one is coloured and moves. */
    private void drawMarken(Canvas canvas, float l, float t, float r, float b) {
        float gap = 8f * dp;
        float mw = (r - l - 2f * gap) / 3f;
        int current = stage();
        String state = turn.state;
        for (int i = 0; i < 3; i++) {
            boolean done = i < current;
            boolean active = i == current;
            int bg = done || active ? STAGE_COLOR[i] : HELL;
            int fg = done || active ? on(bg) : alpha(TINTE, 0.55f);
            float pop = Math.min(1f, Math.max(0f, (time - i * 0.07f - 0.15f) / 0.5f));
            float popScale = pop >= 1f ? 1f : 0.6f + 0.4f * ease(pop) + 0.06f * (float) Math.sin(pop * Math.PI);
            float lift = 0f, grow = 1f;
            if (active) {
                if ("processing".equals(state)) {
                    grow = 1f + 0.04f * (float) Math.sin(time * 6f);
                } else {
                    lift = shownLevel * 10f * dp;
                    grow = 1f + 0.06f * shownLevel;
                }
                // A short pop when the stage starts.
                float s = time - stageTime;
                if (s < 0.5f) grow *= 0.8f + 0.2f * ease(s / 0.5f) + 0.08f * (float) Math.sin(Math.min(1f, s / 0.5f) * Math.PI);
            }
            float ml = l + i * (mw + gap), mr = ml + mw;
            float mcx = (ml + mr) / 2f, mcy = (t + b) / 2f - lift;
            canvas.save();
            canvas.translate(mcx, mcy);
            canvas.scale(grow * popScale, grow * popScale);
            canvas.translate(-mcx, -mcy);
            float top = t - lift, bottom = b - lift;
            fill.setShader(null);
            fill.setColor(alpha(bg, pop));
            canvas.drawRect(ml, top, mr, bottom, fill);
            line.setColor(alpha(TINTE, pop));
            line.setStrokeWidth(2f * dp);
            canvas.drawRect(ml, top, mr, bottom, line);
            dashedRect(canvas, ml + 6f * dp, top + 6f * dp, mr - 6f * dp, bottom - 6f * dp, alpha(fg, pop));
            setText(turn.oswald, 11f * dp, alpha(fg, pop));
            text.setLetterSpacing(0.2f);
            String name = STAGE_NAME[i].toUpperCase(Locale.GERMAN);
            float nw = text.measureText(name);
            canvas.drawText(name, mcx - nw / 2f, top + 21f * dp, text);
            setText(turn.fat, 21f * dp, alpha(fg, pop));
            float vw = text.measureText(STAGE_NR[i]);
            canvas.drawText(STAGE_NR[i], mcx - vw / 2f, bottom - 13f * dp, text);
            canvas.restore();
            // Sparks around the moving stamp.
            if (active && shownLevel > 0.25f) {
                for (int k = 0; k < 3; k++) {
                    float a = ((time * 1.7f + k / 3f) % 1f);
                    float sx = ml + mw * (0.15f + 0.7f * ((k * 0.37f + time * 0.11f) % 1f));
                    float sy = top - 6f * dp - 18f * dp * a;
                    sparkle(canvas, sx, sy, 10f * dp * (1f - a) * shownLevel, alpha(SENF, 1f - a));
                }
            }
        }
    }

    private void dashedRect(Canvas canvas, float l, float t, float r, float b, int color) {
        line.setColor(color);
        line.setStrokeWidth(1f * dp);
        line.setStrokeCap(Paint.Cap.BUTT);
        float dash = 3f * dp, gap = 2.5f * dp;
        for (float x = l; x < r; x += dash + gap) {
            float e = Math.min(r, x + dash);
            canvas.drawLine(x, t, e, t, line);
            canvas.drawLine(x, b, e, b, line);
        }
        for (float y = t; y < b; y += dash + gap) {
            float e = Math.min(b, y + dash);
            canvas.drawLine(l, y, l, e, line);
            canvas.drawLine(r, y, r, e, line);
        }
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** A Billett for the action the assistant took, switched on (green) with the sheen running over it. */
    private void drawBillett(Canvas canvas, float l, float t, float r, float b) {
        float age = time - toolTime;
        float a = Math.min(1f, age / 0.3f);
        fill.setShader(null);
        fill.setColor(alpha(GRUEN, a));
        canvas.drawRect(l, t, r, b, fill);
        float nr = 38f * dp;
        line.setColor(alpha(CREME, a));
        line.setStrokeWidth(2f * dp);
        line.setStrokeCap(Paint.Cap.BUTT);
        float dash = 4f * dp;
        for (float y = t; y < b; y += dash * 2f) canvas.drawLine(l + nr, y, l + nr, Math.min(b, y + dash), line);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(alpha(TINTE, a));
        canvas.drawRect(l, t, r, b, line);
        setText(turn.fat, 18f * dp, alpha(CREME, a));
        float w1 = text.measureText("1");
        canvas.drawText("1", l + nr / 2f - w1 / 2f, (t + b) / 2f + 6f * dp, text);
        float tl = l + nr + 10f * dp, tw = r - tl - 10f * dp;
        setText(turn.oswald, 14f * dp * scale(), alpha(CREME, a));
        text.setLetterSpacing(0.07f);
        String name = TextBlock.fit(turn.tool.toUpperCase(Locale.GERMAN), text, tw);
        canvas.drawText(name, tl, t + (b - t) * 0.44f, text);
        setText(turn.italic, 15f * dp * scale(), alpha(CREME, a));
        canvas.drawText("Erledigt", tl, t + (b - t) * 0.80f, text);
        // Sheen once when it appears.
        if (age < 1.2f) {
            float p = ease(age / 0.9f);
            float sw = (r - l) * 0.25f;
            float sx = l - sw + p * (r - l + 2f * sw);
            LinearGradient g = new LinearGradient(sx - sw, 0f, sx + sw, 0f, new int[] {0x00FFF0B4, 0x99FFF0B4, 0x00FFF0B4},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            fill.setShader(g);
            canvas.save();
            canvas.clipRect(l, t, r, b);
            canvas.drawRect(l, t, r, b, fill);
            canvas.restore();
            fill.setShader(null);
        }
    }

    private void drawDocked(Canvas canvas, float dt) {
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float tw = dockTextWidth(w);
        String answer = turn.answer.isEmpty() ? turn.tool : turn.answer;
        setText(turn.regular, 19f * dp * scale(), CREME);
        answerBlock.layout(answer, text, tw);
        track(dt);

        fill.setShader(null);
        fill.setColor(alpha(0xFF000000, 0.25f));
        canvas.drawRect(2f * dp, 4f * dp, w, h, fill);
        float bh = h - 3f * dp, bw = w - 2f * dp;
        fill.setColor(ROT);
        canvas.drawRect(0f, 0f, bw, bh, fill);
        line.setColor(TINTE);
        line.setStrokeWidth(1.5f * dp);
        canvas.drawRect(0f, 0f, bw, bh, line);
        float o = 7f * dp;
        line.setColor(CREME);
        line.setStrokeWidth(1f * dp);
        canvas.drawRect(o, o, bw - o, bh - o, line);

        // The seal breathes with the voice.
        float size = dockSeal() * (1f + 0.12f * shownLevel);
        float spin = "processing".equals(turn.state) ? time * 90f : -8f;
        drawSeal(canvas, o + 14f * dp + dockSeal() / 2f, bh / 2f, size, spin, 1f);

        float left = o + 14f * dp + dockSeal() + 14f * dp;
        String head = turn.command.isEmpty() ? status() : turn.command;
        float content = 0f;
        float headH = 0f;
        if (!head.isEmpty()) {
            setText(turn.oswald, 13f * dp * scale(), CREME);
            text.setLetterSpacing(0.12f);
            headH = text.getTextSize() * 1.5f;
            content += headH;
        }
        setText(turn.regular, 19f * dp * scale(), CREME);
        float lh = text.getTextSize() * 1.25f;
        int lines = answer.isEmpty() ? 0 : Math.min(3, Math.max(1, answerBlock.lineCount));
        content += lines * lh;
        float y = Math.max(o + 12f * dp, (bh - content) / 2f);
        if (!head.isEmpty()) {
            setText(turn.oswald, 13f * dp * scale(), turn.command.isEmpty() ? alpha(CREME, 0.75f + 0.25f * (float) Math.sin(time * 2.4f)) : CREME);
            text.setLetterSpacing(0.12f);
            String shown = TextBlock.fit(head.toUpperCase(Locale.GERMAN), text, tw);
            canvas.drawText(shown, left, y + text.getTextSize(), text);
            y += headH;
        }
        if (!answer.isEmpty()) {
            setText(turn.regular, 19f * dp * scale(), CREME);
            float shownReveal = turn.answer.isEmpty() ? Float.MAX_VALUE : revealed;
            answerBlock.draw(canvas, text, left, y, tw, 3, shownReveal, false, CREME, lh, 3f * dp);
        }
        if (Math.abs(dockedHeight(w) - h) > 2f * dp) requestLayout();
    }
}
