package com.cricket.scorer.utils;

import android.content.Context;
import android.graphics.Bitmap;

import com.cricket.scorer.models.Match;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * MatchExcelExporter.java
 *
 * Produces a two-sheet .xlsx using Apache POI 4.1.2.
 *
 *   Sheet 1 — "Summary":
 *       Match details (date, format, teams, result)
 *       Innings 1 batting card
 *       Innings 1 bowling card
 *       Innings 2 batting card (if present)
 *       Innings 2 bowling card (if present)
 *
 *   Sheet 2 — "In-depth":
 *       Cumulative run-rate chart    ← embedded PNG
 *       Innings 1 over-by-over chart  ← embedded PNG
 *       Innings 1 over-by-over table
 *       Innings 2 over-by-over chart  ← embedded PNG (if present)
 *       Innings 2 over-by-over table (if present)
 *
 * Why POI over fastexcel:
 * fastexcel does not support image embedding. POI's Drawing.createPicture
 * API lets us anchor a PNG to a cell range. The trade-off is ~12MB APK
 * bloat from POI's transitive deps (xmlbeans, xerces, commons-*).
 *
 * Threading:
 * Call from a background thread. POI construction is heavy and Bitmap
 * compression is non-trivial — anywhere from 500ms to 2s typical.
 */
public final class MatchExcelExporter {

    private MatchExcelExporter() {}

    public static File export(Context ctx, Match match, String outFileName)
            throws IOException {
        File dir = new File(ctx.getExternalCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create export dir: " + dir);
        }
        File out = new File(dir, outFileName);

        try (Workbook wb = new XSSFWorkbook()) {
            StyleSet styles = StyleSet.create((XSSFWorkbook) wb);
            writeSummarySheet(wb, styles, match);
            writeInDepthSheet(ctx, wb, styles, match);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                wb.write(fos);
            }
        }
        return out;
    }

    // ─── Sheet 1: Summary ───────────────────────────────────────────────────

    private static void writeSummarySheet(Workbook wb, StyleSet styles, Match match) {
        org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet("Summary");

        // Title banner
        Row titleRow = sh.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Match Summary");
        titleCell.setCellStyle(styles.title);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
        titleRow.setHeightInPoints(28);

        int row = 2;
        row = writeBlock(sh, styles, row, "Match details",
                MatchScorecardBuilder.summaryTable(match));
        row += 1;
        row = writeBlock(sh, styles, row,
                MatchScorecardBuilder.inningsHeading(match, 1) + " — Batting",
                MatchScorecardBuilder.battingTable(match, 1));
        row += 1;
        row = writeBlock(sh, styles, row,
                MatchScorecardBuilder.inningsHeading(match, 1) + " — Bowling",
                MatchScorecardBuilder.bowlingTable(match, 1));
        row += 1;

        if (match.getSecondInnings() != null) {
            row = writeBlock(sh, styles, row,
                    MatchScorecardBuilder.inningsHeading(match, 2) + " — Batting",
                    MatchScorecardBuilder.battingTable(match, 2));
            row += 1;
            row = writeBlock(sh, styles, row,
                    MatchScorecardBuilder.inningsHeading(match, 2) + " — Bowling",
                    MatchScorecardBuilder.bowlingTable(match, 2));
        }

        // Column widths (POI: width in units of 1/256th of a character)
        sh.setColumnWidth(0, 26 * 256);
        for (int c = 1; c < 8; c++) sh.setColumnWidth(c, 14 * 256);
    }

    // ─── Sheet 2: In-depth (with embedded chart images) ─────────────────────

    private static void writeInDepthSheet(Context ctx, Workbook wb, StyleSet styles,
                                          Match match) throws IOException {
        org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet("In-depth");

        Row titleRow = sh.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("In-depth Statistics");
        titleCell.setCellStyle(styles.title);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
        titleRow.setHeightInPoints(28);

        CreationHelper helper = wb.getCreationHelper();
        Drawing<?> drawing = sh.createDrawingPatriarch();

        int row = 2;

        // 1. Cumulative run-rate chart
        Bitmap rrChart = ChartBitmapRenderer.renderRunRateChart(ctx, match);
        row = embedImage(wb, sh, drawing, helper, rrChart, row, 0, 8, 22);
        rrChart.recycle();
        row += 2;

        // 2. Innings 1 over-by-over chart
        Bitmap bar1 = ChartBitmapRenderer.renderOverBarChart(ctx, match, 1);
        row = embedImage(wb, sh, drawing, helper, bar1, row, 0, 8, 22);
        bar1.recycle();
        row += 2;

        // 3. Innings 1 over-by-over data table
        row = writeBlock(sh, styles, row,
                MatchScorecardBuilder.inningsHeading(match, 1) + " — Over by over",
                MatchScorecardBuilder.overByOverTable(match, 1));
        row += 2;

        // 4. + 5. Innings 2 (if present)
        if (match.getSecondInnings() != null) {
            Bitmap bar2 = ChartBitmapRenderer.renderOverBarChart(ctx, match, 2);
            row = embedImage(wb, sh, drawing, helper, bar2, row, 0, 8, 22);
            bar2.recycle();
            row += 2;
            row = writeBlock(sh, styles, row,
                    MatchScorecardBuilder.inningsHeading(match, 2) + " — Over by over",
                    MatchScorecardBuilder.overByOverTable(match, 2));
        }

        for (int c = 0; c < 9; c++) sh.setColumnWidth(c, 13 * 256);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Embeds a Bitmap as a PNG anchored to cells (col1,row1)..(col2,row2).
     * Returns the row immediately below the image so the caller can keep
     * appending content.
     */
    private static int embedImage(Workbook wb, org.apache.poi.ss.usermodel.Sheet sh,
                                    Drawing<?> drawing, CreationHelper helper,
                                    Bitmap bmp, int startRow, int startCol,
                                    int endCol, int rowSpan) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 95, baos);
        int pictureIdx = wb.addPicture(baos.toByteArray(), Workbook.PICTURE_TYPE_PNG);

        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(startCol);
        anchor.setRow1(startRow);
        anchor.setCol2(endCol);
        anchor.setRow2(startRow + rowSpan);

        Picture pic = drawing.createPicture(anchor, pictureIdx);
        // resize() scales the picture to fill the anchor — keep the natural
        // aspect ratio by not calling pic.resize() and letting the anchor
        // bound the image. Some viewers prefer an explicit resize call though.
        // pic.resize();
        return startRow + rowSpan + 1;
    }

    /**
     * Writes a titled table starting at (startRow, 0). First row of `rows`
     * is the header — gets a green fill + white bold text. "TOTAL" row
     * (if present, identified by first cell == "TOTAL") gets a light fill.
     * Returns the next free row.
     */
    private static int writeBlock(org.apache.poi.ss.usermodel.Sheet sh,
                                    StyleSet styles, int startRow, String title,
                                    String[][] rows) {
        Row sectionRow = sh.createRow(startRow);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(title);
        sectionCell.setCellStyle(styles.section);

        int r = startRow + 1;
        int cols = rows[0].length;

        // Header row
        Row hdr = sh.createRow(r);
        for (int c = 0; c < cols; c++) {
            Cell cell = hdr.createCell(c);
            cell.setCellValue(rows[0][c] != null ? rows[0][c] : "");
            cell.setCellStyle(styles.header);
        }
        r++;

        // Body rows
        for (int i = 1; i < rows.length; i++) {
            Row body = sh.createRow(r);
            boolean isTotal = "TOTAL".equals(rows[i][0]);
            for (int c = 0; c < cols; c++) {
                Cell cell = body.createCell(c);
                String v = rows[i][c] != null ? rows[i][c] : "";
                if (looksNumeric(v)) {
                    try { cell.setCellValue(Double.parseDouble(v)); }
                    catch (NumberFormatException e) { cell.setCellValue(v); }
                } else {
                    cell.setCellValue(v);
                }
                cell.setCellStyle(isTotal ? styles.total : styles.body);
            }
            r++;
        }
        return r;
    }

    private static boolean looksNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != '-') return false;
        }
        return true;
    }

    /**
     * Holder for all the CellStyles we reuse — created once per workbook to
     * stay under POI's per-workbook style limit (4000) and avoid per-cell
     * allocation churn.
     */
    private static final class StyleSet {
        final CellStyle title;
        final CellStyle section;
        final CellStyle header;
        final CellStyle body;
        final CellStyle total;

        private StyleSet(CellStyle title, CellStyle section,
                         CellStyle header, CellStyle body, CellStyle total) {
            this.title = title; this.section = section;
            this.header = header; this.body = body; this.total = total;
        }

        static StyleSet create(XSSFWorkbook wb) {
            // Use IndexedColors only — XSSFColor(byte[]) doesn't exist in
            // poi-android's 3.17 build, and XSSFColor(Color) requires AWT
            // which isn't available on Android. IndexedColors are pure POI
            // and compile cleanly.
            //
            // Closest palette matches:
            //   #0F4E3D green  → IndexedColors.DARK_GREEN (index 58)
            //   header text    → IndexedColors.WHITE
            //   total row      → IndexedColors.LIGHT_GREEN (index 42)

            // Title — bold white on dark green
            XSSFFont titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setAlignment(HorizontalAlignment.LEFT);

            // Section heading — dark green text, no fill
            XSSFFont sectionFont = wb.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 13);
            sectionFont.setColor(IndexedColors.DARK_GREEN.getIndex());
            XSSFCellStyle section = wb.createCellStyle();
            section.setFont(sectionFont);

            // Header row — bold white on dark green
            XSSFFont headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            applyBorder(header);

            // Body — plain bordered cells
            XSSFCellStyle body = wb.createCellStyle();
            applyBorder(body);

            // Total row — light green fill, bold
            XSSFFont totalFont = wb.createFont();
            totalFont.setBold(true);
            XSSFCellStyle total = wb.createCellStyle();
            total.setFont(totalFont);
            total.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            total.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorder(total);

            return new StyleSet(title, section, header, body, total);
        }

        private static void applyBorder(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
