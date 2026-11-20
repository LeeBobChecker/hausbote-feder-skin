// SPDX-License-Identifier: Apache-2.0
package de.hausbote.feder;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Word-wrapped text drawn word by word, so each word can fade in like fresh
 * ink. The paint must carry the typeface and size before layout and draw.
 */
final class TextBlock {
    private static final String[] NONE = new String[0];
    private String text;
    private float size = -1f;
    private float width = -1f;
    private Object face;
    String[] words = NONE;
    private float[] wordX = new float[0];
    private int[] wordLine = new int[0];
    private float[] lineWidth = new float[0];
    int lineCount;

    int wordCount() {
        return words.length;
    }

    /** Lays the text out for a width. Returns true when the layout changed. */
    boolean layout(String value, Paint paint, float maxWidth) {
        String next = value == null ? "" : value.trim();
        if (next.equals(text) && paint.getTextSize() == size && maxWidth == width && paint.getTypeface() == face) return false;
        text = next;
        size = paint.getTextSize();
        width = maxWidth;
        face = paint.getTypeface();
        words = next.isEmpty() ? NONE : next.split("\\s+");
        int n = words.length;
        wordX = new float[n];
        wordLine = new int[n];
        float[] lines = new float[Math.max(1, n)];
        float space = Math.max(paint.measureText(" "), paint.getTextSize() * 0.26f);
        int line = 0;
        float x = 0f;
        for (int i = 0; i < n; i++) {
            float w = paint.measureText(words[i]);
            if (x > 0f && x + space + w > maxWidth) {
                lines[line] = x;
                line++;
                x = 0f;
            }
            if (x > 0f) x += space;
            wordX[i] = x;
            wordLine[i] = line;
            x += w;
        }
        lines[line] = x;
        lineCount = n == 0 ? 0 : line + 1;
        lineWidth = lines;
        return true;
    }

    /** First line shown when at most maxLines fit and the newest revealed word must stay in view. */
    private int firstLine(int maxLines, float revealed) {
        if (lineCount == 0) return 0;
        int head = headLine(revealed);
        return Math.max(0, head - Math.max(1, maxLines) + 1);
    }

    private int headLine(float revealed) {
        int n = words.length;
        if (n == 0) return 0;
        int head = revealed >= n ? n - 1 : Math.max(0, (int) Math.floor(revealed));
        return wordLine[head];
    }

    /** How many lines draw shows. */
    int shownLines(int maxLines, float revealed) {
        if (lineCount == 0) return 0;
        return headLine(revealed) - firstLine(maxLines, revealed) + 1;
    }

    /**
     * Draws the lines. Words fade in as revealed passes their index;
     * Float.MAX_VALUE shows everything. rise lifts a fading word slightly.
     */
    void draw(Canvas canvas, Paint paint, float left, float top, float maxWidth, int maxLines, float revealed,
              boolean center, int color, float lineHeight, float rise) {
        int n = words.length;
        if (n == 0) return;
        int first = firstLine(maxLines, revealed);
        int last = first + Math.max(1, maxLines) - 1;
        int baseAlpha = Color.alpha(color);
        float ascent = paint.getTextSize() * 0.95f;
        for (int i = 0; i < n; i++) {
            int line = wordLine[i];
            if (line < first) continue;
            if (line > last) break;
            float a = revealed - i;
            if (a <= 0f) break;
            if (a > 1f) a = 1f;
            float x = left + (center ? (maxWidth - lineWidth[line]) / 2f : 0f) + wordX[i];
            float y = top + (line - first) * lineHeight + ascent + (1f - a) * rise;
            paint.setColor(color);
            paint.setAlpha(Math.round(baseAlpha * a));
            canvas.drawText(words[i], x, y, paint);
        }
    }

    /** Lines shown when printed lines are on paper and at most maxLines fit. */
    int shownPrinted(int maxLines, int printed) {
        int p = Math.min(printed, lineCount);
        return Math.min(Math.max(1, maxLines), p);
    }

    /**
     * Draws whole printed lines, the newest ones last; when more are printed
     * than fit, the oldest scroll away. The newest line takes freshAlpha.
     */
    void drawPrinted(Canvas canvas, Paint paint, float left, float top, float maxWidth, int maxLines, int printed,
                     boolean center, int color, float lineHeight, float freshAlpha) {
        int p = Math.min(printed, lineCount);
        if (p <= 0) return;
        int first = Math.max(0, p - Math.max(1, maxLines));
        int baseAlpha = Color.alpha(color);
        float ascent = paint.getTextSize() * 0.95f;
        for (int i = 0; i < words.length; i++) {
            int line = wordLine[i];
            if (line < first) continue;
            if (line >= p) break;
            float a = line == p - 1 ? Math.max(0f, Math.min(1f, freshAlpha)) : 1f;
            float x = left + (center ? (maxWidth - lineWidth[line]) / 2f : 0f) + wordX[i];
            float y = top + (line - first) * lineHeight + ascent;
            paint.setColor(color);
            paint.setAlpha(Math.round(baseAlpha * a));
            canvas.drawText(words[i], x, y, paint);
        }
    }

    /** Shortens a single line to fit, ending with an ellipsis. */
    static String fit(String value, Paint paint, float maxWidth) {
        if (value == null) return "";
        String s = value.trim();
        if (paint.measureText(s) <= maxWidth) return s;
        String dots = "…";
        int end = s.length();
        while (end > 0 && paint.measureText(s.substring(0, end) + dots) > maxWidth) end--;
        return s.substring(0, end).trim() + dots;
    }
}
