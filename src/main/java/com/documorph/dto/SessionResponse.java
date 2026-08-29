package com.documorph.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.documorph.model.Category;
import com.documorph.model.ExtractionJob;
import com.documorph.model.UploadSession;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionResponse {
	private UUID sessionId;
	private UploadSession.SessionType sessionType;
	private UploadSession.SessionStatus status;
	private UUID categoryId;
	private OffsetDateTime createdAt;
	private List<JobInfo> jobs;
	private String categoryName;

	@Data
	@Builder
	public static class JobInfo {
		private UUID jobId;
		private UUID fileId;
		private String fileName;
		private Category.ExtractionMode extractionMode;
		private ExtractionJob.JobStatus status;
	}
}