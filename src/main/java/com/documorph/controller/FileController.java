package com.documorph.controller;

import java.util.UUID;

import org.springframework.core.io.Resource;
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
import com.documorph.service.UploadSessionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FileController {

	private final UploadSessionService uploadSessionService;
	private final FileStorageService fileStorageService;

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
}