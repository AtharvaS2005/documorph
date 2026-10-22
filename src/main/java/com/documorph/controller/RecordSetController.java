package com.documorph.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.documorph.dto.RecordSetRequest;
import com.documorph.dto.RecordSetResponse;
import com.documorph.service.RecordSetService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/record-sets")
@RequiredArgsConstructor
public class RecordSetController {

	private final RecordSetService recordSetService;

	@PostMapping
	public ResponseEntity<RecordSetResponse> createRecordSet(@RequestBody RecordSetRequest request,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(recordSetService.createRecordSet(request, userDetails.getUsername()));
	}

	@GetMapping
	public ResponseEntity<List<RecordSetResponse>> getRecordSets(@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(recordSetService.getRecordSets(userDetails.getUsername()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<RecordSetResponse> getRecordSet(@PathVariable("id") UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {
		return ResponseEntity.ok(recordSetService.getRecordSet(id, userDetails.getUsername()));
	}

	@GetMapping("/{id}/export")
	public ResponseEntity<?> exportRecordSet(@PathVariable("id") UUID id,
			@RequestParam(defaultValue = "csv") String format, @AuthenticationPrincipal UserDetails userDetails) {
		if (!"csv".equalsIgnoreCase(format) && !"xlsx".equalsIgnoreCase(format)) {
			throw new IllegalArgumentException("Format must be csv or xlsx.");
		}
		RecordSetResponse rec = recordSetService.getRecordSet(id, userDetails.getUsername());
		String baseName = (rec.getCategoryName() == null ? "record-set" : rec.getCategoryName())
				.replaceAll("[^\\w\\-]+", "_") + "-v" + rec.getCurrentVersion();
		return SessionController.tableResponse(recordSetService.exportTable(id, userDetails.getUsername()), format,
				baseName);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteRecordSet(@PathVariable("id") UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {
		recordSetService.deleteRecordSet(id, userDetails.getUsername());
		return ResponseEntity.noContent().build();
	}
}