package com.documorph.controller;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.documorph.model.FileItem;
import com.documorph.service.FileStorageService;
import com.documorph.service.PagePreviewService;
import com.documorph.service.UploadSessionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FileController {

	private final UploadSessionService uploadSessionService;
	private final FileStorageService fileStorageService;
	private final PagePreviewService pagePreviewService;

	@GetMapping("/api/files/{fileId}")
	public ResponseEntity<Resource> getFile(@PathVariable UUID fileId,
			@RequestParam(defaultValue = "false") boolean download, @AuthenticationPrincipal UserDetails userDetails) {

		FileItem file = uploadSessionService.getOwnedFile(fileId, userDetails.getUsername());
		Resource resource = fileStorageService.loadAsResource(file.getFilePath());

		MediaType contentType;
		try {
			contentType = file.getMimeType() != null ? MediaType.parseMediaType(file.getMimeType())
					: MediaType.APPLICATION_OCTET_STREAM;
		} catch (Exception e) {
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}

		String disposition = (download ? "attachment" : "inline") + "; filename=\""
				+ file.getFileName().replace("\"", "") + "\"";

		return ResponseEntity.ok().contentType(contentType).header(HttpHeaders.CONTENT_DISPOSITION, disposition)
				.body(resource);
	}

	/** How many pages of this file can be previewed (0 if its type can't be). */
	@GetMapping("/api/files/{fileId}/pages")
	public Map<String, Object> getPageInfo(@PathVariable UUID fileId,
			@AuthenticationPrincipal UserDetails userDetails) {
		FileItem file = uploadSessionService.getOwnedFile(fileId, userDetails.getUsername());
		return Map.of("pageCount", pagePreviewService.pageCount(file));
	}

	/** One page as an image, for highlighting extracted fields on the results page. */
	@GetMapping("/api/files/{fileId}/pages/{page}")
	public ResponseEntity<byte[]> getPage(@PathVariable UUID fileId, @PathVariable int page,
			@AuthenticationPrincipal UserDetails userDetails) {
		FileItem file = uploadSessionService.getOwnedFile(fileId, userDetails.getUsername());
		PagePreviewService.PageImage image = pagePreviewService.render(file, page);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate()).body(image.bytes());
	}
}