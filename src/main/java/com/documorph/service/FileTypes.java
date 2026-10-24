package com.documorph.service;

import java.util.Locale;
import java.util.Map;

/**
 * Content types for stored uploads, decided by the file extension (which
 * upload validation restricts) rather than the Content-Type the uploading
 * browser sent. That header is attacker-controlled: a ".png" uploaded as
 * text/html would otherwise be served back as HTML.
 */
public final class FileTypes {

	private static final Map<String, String> BY_EXTENSION = Map.of(
			"pdf", "application/pdf",
			"png", "image/png",
			"jpg", "image/jpeg",
			"jpeg", "image/jpeg",
			"tif", "image/tiff",
			"tiff", "image/tiff",
			"txt", "text/plain;charset=UTF-8",
			"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

	private FileTypes() {
	}

	public static String forFileName(String fileName) {
		String name = fileName == null ? "" : fileName;
		int dot = name.lastIndexOf('.');
		String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
		return BY_EXTENSION.getOrDefault(ext, "application/octet-stream");
	}
}
