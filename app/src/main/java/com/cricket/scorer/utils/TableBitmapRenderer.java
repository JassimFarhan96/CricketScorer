package com.cricket.scorer.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * TableBitmapRenderer.java
 *
 * Draws a 2D array of strings as a clean tabular image on a Bitmap.
 * First row is treated as the header (green background, white text).
 * Subsequent rows alternate light-gray banding for readability.
 *
 * Used by both MatchImageExporter (PNG export) and as a fallback within
 * the XLSX exporter when a column-grouped block is easier rendered as an
 * embedded image than emitted as native XLSX cells.
 *
 * Column widths are auto-fit to the widest content in each column with
 * a minimum width per column.
 */
public final class TableBitmapRenderer {

    private TableBitmapRenderer() {}

    // Visual tokens (matches the app's c_* color system)
    private static final int CLR_HEADER_BG    = 0xFF0F4E3D;  // green_dark
    private static final int CLR_HEADER_TEXT  = 0xFFFFFFFF;
    private static final int CLR_ROW_ALT_BG   = 0xFFF6F8F7;
    private static final int CLR_ROW_BG       = 0xFFFFFFFF;
    private static final int CLR_GRID         = 0xFFDDDDDD;
    private static final int CLR_TEXT         = 0xFF222222;
    private static final int CLR_TITLE        = 0xFF0F4E3D;

    private static final int PADDING_X = 22;
    private static final int PADDING_Y = 18;
    private static final int MIN_COL_W = 70;
    private static final int TITLE_TS  = 36;
    private static final int HEADER_TS = 26;
    private static final int CELL_TS   = 24;

    /**
     * Renders a single titled table.
     *
     * @param title      heading drawn above the table (null/empty → no title)
     * @param rows       2D string array; rows[0] is the header row
     * @return a Bitmap sized to fit the table tightly
     */
    public static Bitmap render(String title, String[][] rows) {
        if (rows == null || rows.length == 0) {
            return Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888);
        }
        Paint titlePaint  = paint(TITLE_TS,  CLR_TITLE,       true);
        Paint headerPaint = paint(HEADER_TS, CLR_HEADER_TEXT, true);
        Paint cellPaint   = paint(CELL_TS,   CLR_TEXT,        false);

        int cols = rows[0].length;
        int[] colW = new int[cols];
        Rect tmp = new Rect();
        for (int c = 0; c < cols; c++) {
            for (String[] row : rows) {
                String s = row[c] != null ? row[c] : "";
                Paint p = row == rows[0] ? headerPaint : cellPaint;
                p.getTextBounds(s, 0, s.length(), tmp);
                if (tmp.width() > colW[c]) colW[c] = tmp.width();
            }
            colW[c] = Math.max(colW[c] + PADDING_X * 2, MIN_COL_W);
        }

        // Row height: use header for header row, cell for the rest
        int headerH = HEADER_TS + PADDING_Y * 2;
        int rowH    = CELL_TS   + PADDING_Y * 2;
        int titleH  = (title != null && !title.isEmpty()) ? TITLE_TS + 40 : 0;

        int totalW = 0; for (int w : colW) totalW += w;
        int totalH = titleH + headerH + (rows.length - 1) * rowH + 8;
        // Outer margin
        int margin = 24;
        Bitmap bmp = Bitmap.createBitmap(totalW + margin * 2, totalH + margin * 2,
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        if (titleH > 0) {
            c.drawText(title, margin, margin + TITLE_TS, titlePaint);
        }

        int xStart = margin;
        int y = margin + titleH;
        // Header
        Paint bg = new Paint(); bg.setColor(CLR_HEADER_BG);
        c.drawRect(xStart, y, xStart + totalW, y + headerH, bg);
        int x = xStart;
        for (int col = 0; col < cols; col++) {
            String s = rows[0][col] != null ? rows[0][col] : "";
            c.drawText(s, x + PADDING_X, y + headerH - PADDING_Y - 4, headerPaint);
            x += colW[col];
        }
        y += headerH;

        // Body rows
        Paint grid = new Paint(); grid.setColor(CLR_GRID); grid.setStrokeWidth(1);
        for (int r = 1; r < rows.length; r++) {
            Paint rb = new Paint();
            rb.setColor(r % 2 == 0 ? CLR_ROW_ALT_BG : CLR_ROW_BG);
            c.drawRect(xStart, y, xStart + totalW, y + rowH, rb);
            x = xStart;
            for (int col = 0; col < cols; col++) {
                String s = rows[r][col] != null ? rows[r][col] : "";
                c.drawText(s, x + PADDING_X, y + rowH - PADDING_Y - 4, cellPaint);
                x += colW[col];
            }
            // Horizontal line below the row
            c.drawLine(xStart, y + rowH, xStart + totalW, y + rowH, grid);
            y += rowH;
        }
        // Vertical column separators
        x = xStart;
        for (int col = 0; col < cols - 1; col++) {
            x += colW[col];
            c.drawLine(x, margin + titleH, x, y, grid);
        }
        // Outer frame
        Paint frame = new Paint();
        frame.setStyle(Paint.Style.STROKE);
        frame.setColor(CLR_GRID);
        frame.setStrokeWidth(2);
        c.drawRect(xStart, margin + titleH, xStart + totalW, y, frame);

        return bmp;
    }

    /**
     * Composes multiple labeled tables (and optional bitmaps) vertically
     * into a single Bitmap. Used to assemble the in-depth-stats PNG.
     */
    public static Bitmap stack(Bitmap... parts) {
        int totalH = 0, maxW = 0;
        int gap = 32;
        for (Bitmap b : parts) {
            if (b == null) continue;
            totalH += b.getHeight() + gap;
            if (b.getWidth() > maxW) maxW = b.getWidth();
        }
        Bitmap out = Bitmap.createBitmap(maxW, Math.max(totalH, 50),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        int y = 0;
        for (Bitmap b : parts) {
            if (b == null) continue;
            int x = (maxW - b.getWidth()) / 2;
            c.drawBitmap(b, x, y, null);
            y += b.getHeight() + gap;
        }
        return out;
    }

    private static Paint paint(int textSize, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(textSize);
        p.setFakeBoldText(bold);
        return p;
    }
}
