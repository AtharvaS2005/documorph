package com.documorph.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.documorph.model.Category;
import com.documorph.model.ExtractionJob;
import com.documorph.model.ExtractionOutput;
import com.documorph.model.FileItem;
import com.documorph.model.UploadSession;
import com.documorph.model.Workspace;
import com.documorph.repository.ExtractionJobRepository;
import com.documorph.repository.ExtractionOutputRepository;
import com.documorph.repository.FileItemRepository;
import com.documorph.repository.UploadSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Per-job work, each method in its own independent transaction (REQUIRES_NEW)
 * so failures stay isolated to a single job and a single concern.
 */
@Service
@RequiredArgsConstructor
public class JobResultProcessor {

	private static final int MAX_AGE_MINUTES = 45;

	private final ExtractionJobRepository extractionJobRepository;
	private final ExtractionOutputRepository extractionOutputRepository;
	private final PythonPipelineClient pythonPipelineClient;
	private final RecordSetService recordSetService;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private static final Logger log = LoggerFactory.getLogger(JobResultProcessor.class);

	private final EventLogService eventLogService;
	private final FileItemRepository fileItemRepository;
	private final UploadSessionRepository uploadSessionRepository;

	/** Returns true only if this call transitioned the job to COMPLETED. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean processOne(UUID jobId) {
		ExtractionJob job = extractionJobRepository.findById(jobId).orElse(null);
		if (job == null || job.getExternalJobId() == null) {
			return false;
		}

		// retries reset startedAt, so age is measured from the latest attempt
		OffsetDateTime ageBasis = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
		if (ageBasis != null && ageBasis.isBefore(OffsetDateTime.now().minusMinutes(MAX_AGE_MINUTES))) {
			job.setStatus(ExtractionJob.JobStatus.FAILED);
			job.setFailureReason("Processing timed out after " + MAX_AGE_MINUTES + " minutes.");
			extractionJobRepository.save(job);
			return false;
		}

		Map<?, ?> response = pythonPipelineClient.getStatus(job.getExternalJobId());
		Object statusObj = response.get("status");
		String pythonStatus = statusObj != null ? String.valueOf(statusObj) : "QUEUED";

		switch (pythonStatus) {
		case "PROCESSING":
			Object progressObj = response.get("progress");
			if (progressObj instanceof Number n) {
				job.setProgress(n.doubleValue());
			}
			job.setStatus(ExtractionJob.JobStatus.PROCESSING);
			extractionJobRepository.save(job);
			refreshSessionStatus(job);
			return false;
		case "COMPLETED":
			saveResult(job, (Map<?, ?>) response.get("result"));
			job.setStatus(ExtractionJob.JobStatus.COMPLETED);
			job.setProgress(1.0);
			job.setCompletedAt(OffsetDateTime.now());
			extractionJobRepository.save(job);
			refreshSessionStatus(job);
			recordJobEvent(job, "job_completed");
			return true;
		case "FAILED":
			Object errorObj = response.get("error");
			job.setStatus(ExtractionJob.JobStatus.FAILED);
			job.setProgress(1.0);
			job.setFailureReason(errorObj != null ? String.valueOf(errorObj) : "Unknown processing error.");
			extractionJobRepository.save(job);
			refreshSessionStatus(job);
			recordJobEvent(job, "job_failed");
			return false;
		case "NOT_FOUND":
			job.setStatus(ExtractionJob.JobStatus.FAILED);
			job.setProgress(1.0);
			job.setFailureReason("The processing service has no record of this job — "
					+ "it was likely lost during a restart. Use Retry to resubmit.");
			extractionJobRepository.save(job);
			refreshSessionStatus(job);
			return false;
		default:
			return false;
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void indexForRag(UUID jobId) {
		ExtractionJob job = extractionJobRepository.findById(jobId).orElse(null);
		if (job == null) {
			return;
		}
		ExtractionOutput output = extractionOutputRepository.findByJobId(jobId).orElse(null);
		if (output == null || output.getExtractedText() == null || output.getExtractedText().isBlank()) {
			return;
		}
		var file = job.getFile();
		var session = file.getSession();
		pythonPipelineClient.indexDocument(file.getId().toString(), output.getExtractedText(),
				session.getWorkspace().getId().toString(), session.getId().toString(), file.getFileName());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void appendToRecordSet(UUID jobId) {
		ExtractionJob job = extractionJobRepository.findById(jobId).orElse(null);
		if (job == null) {
			return;
		}
		var file = job.getFile();
		Category category = file.getSession().getCategory();
		// category is only set for CATEGORY_RUN sessions; only RECORD-type
		// categories accumulate into a record set
		if (category == null || category.getCategoryType() != Category.CategoryType.RECORD) {
			return;
		}
		ExtractionOutput output = extractionOutputRepository.findByJobId(jobId).orElse(null);
		if (output == null) {
			return;
		}
		recordSetService.appendExtraction(category.getId(), file.getId(), file.getFileName(),
				output.getStructuredJson(), output.getCreatedAt());
	}

	private void saveResult(ExtractionJob job, Map<?, ?> result) {
		if (result == null) {
			return;
		}
		Object textObj = result.get("extractedText");
		ExtractionOutput output = extractionOutputRepository.findByJobId(job.getId())
				.orElse(ExtractionOutput.builder().job(job).build());
		output.setExtractedText(textObj != null ? String.valueOf(textObj) : null);
		output.setStructuredJson(toJsonString(result.get("structuredJson")));
		output.setTables(toJsonString(result.get("tables")));
		output.setFieldLocations(toJsonString(result.get("fieldLocations")));
		extractionOutputRepository.save(output);
	}

	private String toJsonString(Object obj) {
		if (obj == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (Exception e) {
			return null;
		}
	}

	private void recordJobEvent(ExtractionJob job, String eventType) {
		try {
			Workspace workspace = job.getFile().getSession().getWorkspace();
			eventLogService.record(workspace.getUser(), workspace, eventType, job.getId().toString());
		} catch (Exception e) {
			log.warn("Could not record {} event for job {}", eventType, job.getId(), e);
		}
	}

	/**
	 * Roll job outcomes up to the parent session. Runs inside processOne's
	 * transaction: this is core state, not a best-effort side effect, so a failure
	 * here should fail the whole tick and be retried.
	 */
	private void refreshSessionStatus(ExtractionJob job) {
		UploadSession session = job.getFile().getSession();

		int total = 0, completed = 0, failed = 0;
		for (FileItem f : fileItemRepository.findBySessionId(session.getId())) {
			ExtractionJob j = extractionJobRepository.findByFileId(f.getId()).orElse(null);
			if (j == null) {
				continue;
			}
			total++;
			if (j.getStatus() == ExtractionJob.JobStatus.COMPLETED) {
				completed++;
			} else if (j.getStatus() == ExtractionJob.JobStatus.FAILED) {
				failed++;
			}
		}
		if (total == 0) {
			return;
		}

		UploadSession.SessionStatus next;
		if (completed + failed < total) {
			next = UploadSession.SessionStatus.PROCESSING;
		} else if (failed == 0) {
			next = UploadSession.SessionStatus.COMPLETED;
		} else if (completed == 0) {
			next = UploadSession.SessionStatus.FAILED;
		} else {
			next = UploadSession.SessionStatus.PARTIAL_FAILURE;
		}

		if (session.getStatus() != next) {
			session.setStatus(next);
			uploadSessionRepository.save(session);
		}
	}
}