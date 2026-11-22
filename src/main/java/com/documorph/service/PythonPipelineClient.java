package com.documorph.service;

import java.io.File;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class PythonPipelineClient {

	private static final String API_KEY_HEADER = "X-Pipeline-Key";

	private final RestClient restClient;
	// separate client so a dead pipeline fails the health check in seconds, not minutes
	private final RestClient healthClient;

	public PythonPipelineClient(@Value("${python.service.url}") String baseUrl,
			@Value("${python.service.api-key:}") String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			LoggerFactory.getLogger(PythonPipelineClient.class)
					.warn("PIPELINE_API_KEY is not set; the processing service will reject calls from Docker.");
		}
		this.restClient = RestClient.builder().baseUrl(baseUrl).defaultHeader(API_KEY_HEADER, apiKey).build();
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.healthClient = RestClient.builder().baseUrl(baseUrl).defaultHeader(API_KEY_HEADER, apiKey)
				.requestFactory(factory).build();
	}

	/**
	 * online is true only when the API answers and its queue (Redis) is
	 * reachable; without the queue, submitted jobs would never run.
	 */
	public Map<String, Object> checkHealth() {
		Map<String, Object> result = new LinkedHashMap<>();
		try {
			Map<?, ?> body = healthClient.get().uri("/health").retrieve().body(Map.class);
			boolean queue = body != null && Boolean.TRUE.equals(body.get("queueReachable"));
			result.put("online", queue);
			result.put("queueReachable", queue);
			result.put("ollamaReachable", body != null && Boolean.TRUE.equals(body.get("ollamaReachable")));
			result.put("qdrantReachable", body != null && Boolean.TRUE.equals(body.get("qdrantReachable")));
			if (!queue) {
				result.put("message", "The processing service is running but its job queue (Redis) is unreachable.");
			}
		} catch (Exception e) {
			result.put("online", false);
			result.put("message", "The processing service is offline. Start the NLP pipeline and try again.");
		}
		return result;
	}

	public String submitDocument(String filePath, String mode, String customPrompt, String workspaceId) {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", new FileSystemResource(new File(filePath)));
		form.add("mode", mode);
		form.add("customPrompt", customPrompt == null ? "" : customPrompt);
		form.add("workspaceId", workspaceId);

		Map<?, ?> response = restClient.post().uri("/process").contentType(MediaType.MULTIPART_FORM_DATA).body(form)
				.retrieve().body(Map.class);

		return (String) response.get("pythonJobId");
	}

	public Map<?, ?> getStatus(String pythonJobId) {
		return restClient.get().uri("/status/{id}", pythonJobId).retrieve().body(Map.class);
	}

	/**
	 * Index a document's text for retrieval. uploadedAt is the file's real
	 * upload time, sent so that a re-index keeps a document's original date
	 * rather than stamping it with the moment the re-index ran; search uses
	 * it to answer questions with a date window ("uploaded in the past
	 * four days"). Null lets the pipeline default it to now.
	 */
	public int indexDocument(String docId, String text, String workspaceId, String sessionId, String fileName,
			OffsetDateTime uploadedAt) {
		Map<String, Object> body = new HashMap<>();
		body.put("docId", docId);
		body.put("text", text);
		body.put("workspaceId", workspaceId);
		body.put("sessionId", sessionId == null ? "" : sessionId);
		body.put("fileName", fileName == null ? "" : fileName);
		body.put("uploadedAt", uploadedAt == null ? null : uploadedAt.toEpochSecond());
		Map<?, ?> response = restClient.post().uri("/index").contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().body(Map.class);
		return ((Number) response.get("chunksIndexed")).intValue();
	}

	/** Overload for callers with no upload time to hand; the pipeline uses now. */
	public int indexDocument(String docId, String text, String workspaceId, String sessionId, String fileName) {
		return indexDocument(docId, text, workspaceId, sessionId, fileName, null);
	}

	public List<?> retrieve(String query, int topK, String workspaceId) {
		Map<String, Object> body = Map.of("query", query, "topK", topK, "workspaceId", workspaceId);
		return restClient.post().uri("/retrieve").contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.body(List.class);
	}

	/** Retrieve-then-generate: returns {answer, sources, model_used, passages}. */
	/**
	 * Ask a question, optionally with the recent turns of the same
	 * conversation (oldest first) so the pipeline can resolve a follow-up
	 * like "what about that one" into a standalone question before it
	 * searches. An empty history behaves exactly as before.
	 */
	public Map<?, ?> answer(String question, int topK, String workspaceId,
			List<Map<String, String>> history) {
		Map<String, Object> body = new HashMap<>();
		body.put("question", question);
		body.put("topK", topK);
		body.put("workspaceId", workspaceId);
		body.put("history", history == null ? List.of() : history);
		return restClient.post().uri("/answer").contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.body(Map.class);
	}

	public Map<?, ?> answer(String question, int topK, String workspaceId) {
		Map<String, Object> body = Map.of("question", question, "topK", topK, "workspaceId", workspaceId);
		return restClient.post().uri("/answer").contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.body(Map.class);
	}

	public int deleteDocuments(List<String> docIds, String workspaceId) {
		Map<String, Object> body = Map.of("docIds", docIds, "workspaceId", workspaceId);
		Map<?, ?> response = restClient.post().uri("/delete-docs").contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().body(Map.class);
		return ((Number) response.get("deleted")).intValue();
	}

	public void resetCollection(String workspaceId) {
		restClient.post().uri("/reset-collection").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("workspaceId", workspaceId)).retrieve().toBodilessEntity();
	}
}