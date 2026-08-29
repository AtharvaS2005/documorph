package com.documorph.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.documorph.model.ExtractionJob;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionResultsResponse {
	private UUID sessionId;
	private List<FileResult> results;

	@Data
	@Builder
	public static class FileResult {
		private UUID fileId;
		private String fileName;
		private UUID jobId;
		private ExtractionJob.JobStatus jobStatus;
		private String extractedText;
		private String structuredJson;
		private OffsetDateTime processedAt;
		private String tables;
	}
}