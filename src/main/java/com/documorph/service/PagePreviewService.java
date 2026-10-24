package com.documorph.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.documorph.exception.ResourceNotFoundException;
import com.documorph.model.FileItem;

import lombok.RequiredArgsConstructor;

/**
 * Page images for the results preview, rendered on the server so the
 * browser needs no PDF library. PDF pages are rasterised with PDFBox;
 * image uploads are a single page served as they are, and the browser
 * applies their EXIF orientation just as the pipeline's field locator does,
 * so highlight boxes line up.
 */
@Service
@RequiredArgsConstructor
public class PagePreviewService {

	/** ~110 DPI: sharp enough to read, small enough to load quickly. */
	private static final float RENDER_SCALE = 1.5f;

	private final FileStorageService fileStorageService;

	public record PageImage(byte[] bytes, String contentType) {
	}

	public boolean isPdf(FileItem file) {
		return extension(file).equals("pdf");
	}

	public boolean isImage(FileItem file) {
		return switch (extension(file)) {
		case "png", "jpg", "jpeg", "gif", "webp", "bmp" -> true;
		default -> false;
		};
	}

	/** Number of previewable pages; 0 when the file type can't be previewed. */
	public int pageCount(FileItem file) {
		if (isImage(file)) {
			return 1;
		}
		if (!isPdf(file)) {
			return 0;
		}
		try (PDDocument doc = Loader.loadPDF(toFile(file))) {
			return doc.getNumberOfPages();
		} catch (IOException e) {
			return 0;
		}
	}

	/** Page {@code page} (1-based) as an image. */
	public PageImage render(FileItem file, int page) {
		if (isImage(file)) {
			if (page != 1) {
				throw new ResourceNotFoundException("Page not found.");
			}
			try (InputStream in = fileStorageService.loadAsResource(file.getFilePath()).getInputStream()) {
				String type = file.getMimeType() != null ? file.getMimeType() : "image/" + extension(file);
				return new PageImage(in.readAllBytes(), type);
			} catch (IOException e) {
				throw new ResourceNotFoundException("File could not be read.");
			}
		}
		if (!isPdf(file)) {
			throw new IllegalArgumentException("Previews are only available for PDFs and images.");
		}
		try (PDDocument doc = Loader.loadPDF(toFile(file))) {
			if (page < 1 || page > doc.getNumberOfPages()) {
				throw new ResourceNotFoundException("Page not found.");
			}
			BufferedImage image = new PDFRenderer(doc).renderImage(page - 1, RENDER_SCALE, ImageType.RGB);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return new PageImage(out.toByteArray(), "image/png");
		} catch (IOException e) {
			throw new ResourceNotFoundException("This PDF page could not be rendered.");
		}
	}

	private File toFile(FileItem file) throws IOException {
		Resource resource = fileStorageService.loadAsResource(file.getFilePath());
		return resource.getFile();
	}

	private String extension(FileItem file) {
		String name = file.getFileName() == null ? "" : file.getFileName();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
