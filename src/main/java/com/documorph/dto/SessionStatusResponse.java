package com.documorph.dto;

import java.util.List;
import java.util.UUID;

import com.documorph.model.ExtractionJob;
import com.documorph.model.UploadSession;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionStatusResponse {
	private UUID sessionId;
	private UploadSession.SessionStatus sessionStatus;
	private int totalFiles;
	private int completedFiles;
	private int failedFiles;
	private int queuedFiles;
	private List<JobStatusInfo> jobs;

	@Data
	@Builder
	public static class JobStatusInfo {
		private UUID jobId;
		private UUID fileId;
		private String fileName;
		private ExtractionJob.JobStatus status;
		private String errorCode;
		private String failureReason;
		private Double progress;
	}
}