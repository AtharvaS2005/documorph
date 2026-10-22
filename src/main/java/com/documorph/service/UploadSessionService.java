package com.documorph.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.documorph.dto.SessionResponse;
import com.documorph.dto.SessionResultsResponse;
import com.documorph.dto.SessionStatusResponse;
import com.documorph.exception.ResourceNotFoundException;
import com.documorph.exception.ServiceUnavailableException;
import com.documorph.model.Category;
import com.documorph.model.ExtractionJob;
import com.documorph.model.ExtractionOutput;
import com.documorph.model.FileItem;
import com.documorph.model.UploadSession;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.CategoryRepository;
import com.documorph.repository.ExtractionJobRepository;
import com.documorph.repository.ExtractionOutputRepository;
import com.documorph.repository.FileItemRepository;
import com.documorph.repository.UploadSessionRepository;
import com.documorph.repository.UserRepository;
import com.documorph.repository.WorkspaceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadSessionService {

	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final CategoryRepository categoryRepository;
	private final FileItemRepository fileItemRepository;
	private final UploadSessionRepository uploadSessionRepository;
	private final ExtractionJobRepository extractionJobRepository;
	private final ExtractionOutputRepository extractionOutputRepository;
	private final FileStorageService fileStorageService;
	private final PythonPipelineClient pythonPipelineClient;
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "png", "jpg", "jpeg", "tif",
			"tiff");
	private static final Logger log = LoggerFactory.getLogger(UploadSessionService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final EventLogService eventLogService;

	@Transactional
	public SessionResponse createSession(String sessionTypeStr, UUID categoryId, String extractionModeStr,
			String summaryStyleStr, String customPrompt, List<MultipartFile> files, String email) {

		validateFiles(files);
		requirePipelineOnline();
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		UploadSession.SessionType sessionType = UploadSession.SessionType.valueOf(sessionTypeStr);

		Category category = null;
		if (sessionType == UploadSession.SessionType.CATEGORY_RUN) {
			if (categoryId == null) {
				throw new IllegalArgumentException("Category ID is required for Category Run sessions.");
			}
			category = categoryRepository.findById(categoryId)
					.orElseThrow(() -> new ResourceNotFoundException("Category not found."));
		}

		Category.ExtractionMode extractionMode = null;
		Category.SummaryStyle summaryStyle = null;
		if (sessionType == UploadSession.SessionType.SINGLE_SESSION) {
			if (extractionModeStr == null) {
				throw new IllegalArgumentException("Extraction mode is required for Single Session.");
			}
			extractionMode = Category.ExtractionMode.valueOf(extractionModeStr);
			if (summaryStyleStr != null) {
				summaryStyle = Category.SummaryStyle.valueOf(summaryStyleStr);
			}
		}

		UploadSession session = UploadSession.builder().workspace(workspace).category(category).sessionType(sessionType)
				.build();
		session = uploadSessionRepository.save(session);

		try {
			eventLogService.record(user, workspace, "session_created", session.getId().toString());
		} catch (Exception e) {
			log.warn("Could not record session_created event", e);
		}

		List<SessionResponse.JobInfo> jobInfos = new ArrayList<>();
		final Category finalCategory = category;
		final UploadSession finalSession = session;
		final Category.ExtractionMode finalMode = extractionMode;
		final Category.SummaryStyle finalStyle = summaryStyle;

		for (MultipartFile file : files) {
			try {
				String storedPath = fileStorageService.store(finalSession.getId(), file);

				FileItem fileItem = FileItem.builder().session(finalSession).fileName(file.getOriginalFilename())
						.fileSize(file.getSize()).mimeType(file.getContentType()).filePath(storedPath).build();
				fileItem = fileItemRepository.save(fileItem);

				Category.ExtractionMode mode = finalCategory != null ? finalCategory.getDefaultExtractionMode()
						: finalMode;
				Category.SummaryStyle style = finalCategory != null ? finalCategory.getSummaryStyle() : finalStyle;
				String prompt = finalCategory != null ? finalCategory.getCustomPrompt() : customPrompt;

				ExtractionJob job = ExtractionJob.builder().file(fileItem).extractionMode(mode).summaryStyle(style)
						.customPrompt(prompt).build();
				job = extractionJobRepository.save(job);
				try {
					String pythonJobId = pythonPipelineClient.submitDocument(fileItem.getFilePath(), mode.name(),
							prompt, workspace.getId().toString());
					job.setExternalJobId(pythonJobId);
					job = extractionJobRepository.save(job);
				} catch (Exception e) {
					job.setStatus(ExtractionJob.JobStatus.FAILED);
					job.setFailureReason("Failed to submit to processing service: " + e.getMessage());
					job = extractionJobRepository.save(job);
				}

				jobInfos.add(SessionResponse.JobInfo.builder().jobId(job.getId()).fileId(fileItem.getId())
						.fileName(fileItem.getFileName()).extractionMode(mode).status(job.getStatus()).build());

			} catch (Exception e) {
				throw new RuntimeException(
						"Failed to process file " + file.getOriginalFilename() + ": " + e.getMessage());
			}
		}

		return SessionResponse.builder().sessionId(session.getId()).sessionType(session.getSessionType())
				.status(session.getStatus()).categoryId(category != null ? category.getId() : null)
				.categoryName(category != null ? category.getCategoryName() : null)
				.createdAt(session.getCreatedAt()).jobs(jobInfos).build();
	}

	@Transactional(readOnly = true)
	public SessionStatusResponse getSessionStatus(UUID sessionId, String email) {
	    User user = userRepository.findByEmail(email)
	            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
	    Workspace workspace = workspaceRepository.findByUserId(user.getId())
	            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		UploadSession session = uploadSessionRepository.findById(sessionId)
				.filter(s -> s.getWorkspace().getId().equals(workspace.getId()))
				.orElseThrow(() -> new ResourceNotFoundException("Session not found."));

	    List<FileItem> files = fileItemRepository.findBySessionId(sessionId);
		List<SessionStatusResponse.JobStatusInfo> jobInfos = new ArrayList<>();
		int completed = 0, failed = 0, queued = 0;

		for (FileItem file : files) {
			ExtractionJob job = extractionJobRepository.findByFileId(file.getId()).orElse(null);
			if (job != null) {
				if (job.getStatus() == ExtractionJob.JobStatus.COMPLETED) {
					completed++;
				} else if (job.getStatus() == ExtractionJob.JobStatus.FAILED) {
					failed++;
				} else {
					queued++;
				}

				jobInfos.add(SessionStatusResponse.JobStatusInfo.builder().jobId(job.getId()).fileId(file.getId())
						.fileName(file.getFileName()).status(job.getStatus()).errorCode(job.getErrorCode())
						.failureReason(job.getFailureReason()).progress(job.getProgress())
						.startedAt(job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt())
						.completedAt(job.getCompletedAt()).build());
			}
		}

	    return SessionStatusResponse.builder()
	            .sessionId(sessionId)
				.sessionStatus(session.getStatus())
	            .totalFiles(files.size())
				.completedFiles(completed).failedFiles(failed).queuedFiles(queued).jobs(jobInfos)
	            .build();
	}

	@Transactional(readOnly = true)
	public List<SessionResponse> getSessions(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		return uploadSessionRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspace.getId()).stream()
				.map(session -> {
					List<FileItem> files = fileItemRepository.findBySessionId(session.getId());
					List<SessionResponse.JobInfo> jobs = files.stream().map(file -> {
						ExtractionJob job = extractionJobRepository.findByFileId(file.getId()).orElse(null);
						return SessionResponse.JobInfo.builder().fileId(file.getId()).fileName(file.getFileName())
								.jobId(job != null ? job.getId() : null)
								.extractionMode(job != null ? job.getExtractionMode() : null)
								.status(job != null ? job.getStatus() : null).build();
					}).collect(Collectors.toList());

					return SessionResponse.builder().sessionId(session.getId()).sessionType(session.getSessionType())
							.status(session.getStatus())
							.categoryId(session.getCategory() != null ? session.getCategory().getId() : null)
							.categoryName(
									session.getCategory() != null ? session.getCategory().getCategoryName() : null)
							.createdAt(session.getCreatedAt()).jobs(jobs).build();
				}).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public SessionResultsResponse getSessionResults(UUID sessionId, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		if (!uploadSessionRepository.existsByIdAndWorkspaceId(sessionId, workspace.getId())) {
			throw new ResourceNotFoundException("Session not found.");
		}

		List<FileItem> files = fileItemRepository.findBySessionId(sessionId);
		List<SessionResultsResponse.FileResult> results = new ArrayList<>();

		for (FileItem file : files) {
			ExtractionJob job = extractionJobRepository.findByFileId(file.getId()).orElse(null);
			ExtractionOutput output = null;
			if (job != null) {
				output = extractionOutputRepository.findByJobId(job.getId()).orElse(null);
			}

			results.add(SessionResultsResponse.FileResult.builder().fileId(file.getId()).fileName(file.getFileName())
					.jobId(job != null ? job.getId() : null).jobStatus(job != null ? job.getStatus() : null)
					.extractedText(output != null ? output.getExtractedText() : null)
					.structuredJson(output != null ? output.getStructuredJson() : null)
					.tables(output != null ? output.getTables() : null)
					.processedAt(output != null ? output.getCreatedAt() : null).build());
		}

		return SessionResultsResponse.builder().sessionId(sessionId).results(results).build();
	}

	private void validateFiles(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			throw new IllegalArgumentException("At least one file is required.");
		}
		for (MultipartFile file : files) {
			String name = file.getOriginalFilename();
			if (name == null || !name.contains(".")) {
				throw new IllegalArgumentException("File has no recognizable extension: " + name);
			}
			String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
			if (!ALLOWED_EXTENSIONS.contains(ext)) {
				throw new IllegalArgumentException(
						"Unsupported file type '." + ext + "'. Allowed: " + ALLOWED_EXTENSIONS);
			}
			if (file.isEmpty()) {
				throw new IllegalArgumentException("File is empty: " + name);
			}
		}
	}

	@Transactional(readOnly = true)
	public FileItem getOwnedFile(UUID fileId, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		FileItem file = fileItemRepository.findById(fileId)
				.orElseThrow(() -> new ResourceNotFoundException("File not found."));

		// 404 rather than 403 for another workspace's file, so we don't leak
		// whether that id exists
		if (!file.getSession().getWorkspace().getId().equals(workspace.getId())) {
			throw new ResourceNotFoundException("File not found.");
		}
		return file;
	}

	@Transactional
	public void deleteSession(UUID sessionId, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		if (!uploadSessionRepository.existsByIdAndWorkspaceId(sessionId, workspace.getId())) {
			throw new ResourceNotFoundException("Session not found.");
		}

		List<String> docIds = fileItemRepository.findBySessionId(sessionId).stream().map(f -> f.getId().toString())
				.collect(Collectors.toList());

		// cascades to file_items -> extraction_jobs -> extraction_outputs
		uploadSessionRepository.deleteById(sessionId);

		// External cleanup is best-effort. These are plain HTTP/filesystem
		// calls, not nested @Transactional work, so catching here cannot mark
		// the transaction rollback-only.
		try {
			fileStorageService.deleteSessionDirectory(sessionId);
		} catch (Exception e) {
			log.warn("Could not remove upload directory for session {}", sessionId, e);
		}
		try {
			pythonPipelineClient.deleteDocuments(docIds, workspace.getId().toString());
		} catch (Exception e) {
			log.warn("Could not remove Qdrant vectors for session {}", sessionId, e);
		}
		try {
			eventLogService.record(user, workspace, "session_deleted", sessionId.toString());
		} catch (Exception e) {
			log.warn("Could not record session_deleted event", e);
		}
	}

	/**
	 * Drop and rebuild this workspace's search index from already-extracted text.
	 * No re-OCR involved, so this is fast relative to reprocessing -- and it clears
	 * any orphaned vectors left by earlier deletions. Deliberately not
	 * transactional: it only reads simple fields and can run for a minute or more.
	 */
	public int reindexWorkspace(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		String workspaceId = workspace.getId().toString();
		pythonPipelineClient.resetCollection(workspaceId);

		int indexed = 0;
		for (UploadSession session : uploadSessionRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspace.getId())) {
			for (FileItem file : fileItemRepository.findBySessionId(session.getId())) {
				ExtractionJob job = extractionJobRepository.findByFileId(file.getId()).orElse(null);
				if (job == null || job.getStatus() != ExtractionJob.JobStatus.COMPLETED) {
					continue;
				}
				ExtractionOutput output = extractionOutputRepository.findByJobId(job.getId()).orElse(null);
				if (output == null || output.getExtractedText() == null || output.getExtractedText().isBlank()) {
					continue;
				}
				try {
					pythonPipelineClient.indexDocument(file.getId().toString(), output.getExtractedText(), workspaceId,
							session.getId().toString(), file.getFileName());
					indexed++;
				} catch (Exception e) {
					log.warn("Reindex failed for file {}", file.getId(), e);
				}
			}
		}
		return indexed;
	}

	@Transactional
	public void retryJob(UUID jobId, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		ExtractionJob job = extractionJobRepository.findById(jobId)
				.orElseThrow(() -> new ResourceNotFoundException("Job not found."));
		FileItem file = job.getFile();

		if (!file.getSession().getWorkspace().getId().equals(workspace.getId())) {
			throw new ResourceNotFoundException("Job not found.");
		}
		if (job.getStatus() != ExtractionJob.JobStatus.FAILED) {
			throw new IllegalArgumentException("Only failed jobs can be retried.");
		}

		requirePipelineOnline();
		String pythonJobId;
		try {
			pythonJobId = pythonPipelineClient.submitDocument(file.getFilePath(), job.getExtractionMode().name(),
					job.getCustomPrompt(), workspace.getId().toString());
		} catch (Exception e) {
			throw new IllegalArgumentException("Could not reach the processing service: " + e.getMessage());
		}

		job.setExternalJobId(pythonJobId);
		job.setStatus(ExtractionJob.JobStatus.QUEUED);
		job.setProgress(null);
		job.setFailureReason(null);
		job.setErrorCode(null);
		job.setCompletedAt(null);
		job.setStartedAt(OffsetDateTime.now());
		job.setRetryCount(job.getRetryCount() == null ? 1 : job.getRetryCount() + 1);
		extractionJobRepository.save(job);
	}

	/** Rejects uploads up front rather than storing files whose jobs can only fail. */
	private void requirePipelineOnline() {
		Map<String, Object> health = pythonPipelineClient.checkHealth();
		if (!Boolean.TRUE.equals(health.get("online"))) {
			throw new ServiceUnavailableException(String.valueOf(health.get("message")));
		}
	}

	/**
	 * One row per file: fileName, status, then the union of all extracted field
	 * names. With several sessions a leading sessionId column says where each row
	 * came from. Text-only results (FULL, SUMMARY, KEY_INSIGHTS) have no fields,
	 * so an extractedText column is added when any row lacks them.
	 */
	@Transactional(readOnly = true)
	public List<List<String>> exportSessionsTable(List<UUID> sessionIds, String email) {
		if (sessionIds == null || sessionIds.isEmpty()) {
			throw new IllegalArgumentException("Select at least one session to export.");
		}
		boolean multi = sessionIds.size() > 1;

		List<UUID> rowSessions = new ArrayList<>();
		List<SessionResultsResponse.FileResult> rowResults = new ArrayList<>();
		List<Map<String, Object>> rowFields = new ArrayList<>();
		LinkedHashSet<String> columns = new LinkedHashSet<>();
		boolean needsText = false;

		for (UUID sessionId : new LinkedHashSet<>(sessionIds)) {
			for (SessionResultsResponse.FileResult r : getSessionResults(sessionId, email).getResults()) {
				Map<String, Object> fields = parseJsonObject(r.getStructuredJson());
				rowSessions.add(sessionId);
				rowResults.add(r);
				rowFields.add(fields);
				columns.addAll(fields.keySet());
				if (fields.isEmpty() && r.getExtractedText() != null) {
					needsText = true;
				}
			}
		}

		List<List<String>> table = new ArrayList<>();
		List<String> header = new ArrayList<>();
		if (multi) {
			header.add("sessionId");
		}
		header.add("fileName");
		header.add("status");
		header.addAll(columns);
		if (needsText) {
			header.add("extractedText");
		}
		table.add(header);

		for (int i = 0; i < rowResults.size(); i++) {
			SessionResultsResponse.FileResult r = rowResults.get(i);
			Map<String, Object> fields = rowFields.get(i);
			List<String> row = new ArrayList<>();
			if (multi) {
				row.add(rowSessions.get(i).toString());
			}
			row.add(r.getFileName());
			row.add(r.getJobStatus() == null ? "" : r.getJobStatus().name());
			for (String c : columns) {
				Object v = fields.get(c);
				row.add(v == null ? "" : String.valueOf(v));
			}
			if (needsText) {
				row.add(fields.isEmpty() && r.getExtractedText() != null ? r.getExtractedText() : "");
			}
			table.add(row);
		}
		return table;
	}

	private Map<String, Object> parseJsonObject(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			return Map.of();
		}
	}
}
