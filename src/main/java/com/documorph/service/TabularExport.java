package com.documorph.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Writes a header-first table of strings as CSV or XLSX, so every export
 * endpoint produces the same rows in either format.
 */
public final class TabularExport {

	/** Excel's hard limit on characters in a single cell. */
	private static final int MAX_CELL_LENGTH = 32767;

	private TabularExport() {
	}

	public static String toCsv(List<List<String>> table) {
		StringBuilder sb = new StringBuilder();
		for (List<String> row : table) {
			for (int i = 0; i < row.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				sb.append(csvCell(row.get(i)));
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	public static byte[] toXlsx(List<List<String>> table, String sheetName) {
		try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = wb.createSheet(safeSheetName(sheetName));
			CellStyle headerStyle = wb.createCellStyle();
			Font bold = wb.createFont();
			bold.setBold(true);
			headerStyle.setFont(bold);

			for (int r = 0; r < table.size(); r++) {
				Row row = sheet.createRow(r);
				List<String> values = table.get(r);
				for (int c = 0; c < values.size(); c++) {
					String v = values.get(c) == null ? "" : values.get(c);
					if (v.length() > MAX_CELL_LENGTH) {
						v = v.substring(0, MAX_CELL_LENGTH);
					}
					var cell = row.createCell(c);
					cell.setCellValue(v);
					if (r == 0) {
						cell.setCellStyle(headerStyle);
					}
				}
			}
			if (!table.isEmpty()) {
				sheet.createFreezePane(0, 1);
				for (int c = 0; c < table.get(0).size(); c++) {
					sheet.autoSizeColumn(c);
				}
			}
			wb.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Always quote: simplest correct handling of commas, quotes and newlines. */
	private static String csvCell(String value) {
		if (value == null) {
			return "\"\"";
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static String safeSheetName(String name) {
		String cleaned = (name == null || name.isBlank() ? "Export" : name).replaceAll("[\\\\/?*\\[\\]:]", "_");
		return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
	}
}
