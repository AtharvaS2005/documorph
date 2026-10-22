package com.documorph.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.documorph.dto.SessionResponse;
import com.documorph.dto.SessionResultsResponse;
import com.documorph.dto.SessionStatusResponse;
import com.documorph.service.TabularExport;
import com.documorph.service.UploadSessionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

	private final UploadSessionService uploadSessionService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<SessionResponse> createSession(@RequestParam("sessionType") String sessionType,
			@RequestParam(value = "categoryId", required = false) UUID categoryId,
			@RequestParam(value = "extractionMode", required = false) String extractionMode,
			@RequestParam(value = "summaryStyle", required = false) String summaryStyle,
			@RequestParam(value = "customPrompt", required = false) String customPrompt,
			@RequestParam("files") List<MultipartFile> files, @AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.status(HttpStatus.CREATED).body(uploadSessionService.createSession(sessionType,
				categoryId, extractionMode, summaryStyle, customPrompt, files, userDetails.getUsername()));
    }

	@GetMapping
	public ResponseEntity<List<SessionResponse>> getSessions(@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(uploadSessionService.getSessions(userDetails.getUsername()));
    }
	@GetMapping("/{id}/status")
	public ResponseEntity<SessionStatusResponse> getSessionStatus(@PathVariable("id") UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(uploadSessionService.getSessionStatus(id, userDetails.getUsername()));
    }

	@GetMapping("/{id}/results")
	public ResponseEntity<SessionResultsResponse> getSessionResults(@PathVariable("id") UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(uploadSessionService.getSessionResults(id, userDetails.getUsername()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteSession(@PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
		uploadSessionService.deleteSession(id, userDetails.getUsername());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/jobs/{jobId}/retry")
	public ResponseEntity<Void> retryJob(@PathVariable UUID jobId, @AuthenticationPrincipal UserDetails userDetails) {
		uploadSessionService.retryJob(jobId, userDetails.getUsername());
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/{id}/export")
	public ResponseEntity<?> exportSession(@PathVariable UUID id, @RequestParam(defaultValue = "json") String format,
			@AuthenticationPrincipal UserDetails userDetails) {

		if ("csv".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
			List<List<String>> table = uploadSessionService.exportSessionsTable(List.of(id), userDetails.getUsername());
			return tableResponse(table, format, "session-" + id);
		}

		SessionResultsResponse results = uploadSessionService.getSessionResults(id, userDetails.getUsername());
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"session-" + id + ".json\"")
				.body(results);
	}

	static ResponseEntity<?> tableResponse(List<List<String>> table, String format, String baseName) {
		if ("xlsx".equalsIgnoreCase(format)) {
			return ResponseEntity.ok().contentType(XLSX)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".xlsx\"")
					.body(TabularExport.toXlsx(table, "Results"));
		}
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".csv\"")
				.body(TabularExport.toCsv(table));
	}

	private static final MediaType XLSX = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
}
