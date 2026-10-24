package com.documorph.service;

import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	private final RestClient restClient;
	// separate client so a dead pipeline fails the health check in seconds, not minutes
	private final RestClient healthClient;

	public PythonPipelineClient(@Value("${python.service.url}") String baseUrl) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.healthClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
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

	public int indexDocument(String docId, String text, String workspaceId, String sessionId, String fileName) {
		Map<String, Object> body = Map.of("docId", docId, "text", text, "workspaceId", workspaceId, "sessionId",
				sessionId == null ? "" : sessionId, "fileName", fileName == null ? "" : fileName);
		Map<?, ?> response = restClient.post().uri("/index").contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().body(Map.class);
		return ((Number) response.get("chunksIndexed")).intValue();
	}

	public List<?> retrieve(String query, int topK, String workspaceId) {
		Map<String, Object> body = Map.of("query", query, "topK", topK, "workspaceId", workspaceId);
		return restClient.post().uri("/retrieve").contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.body(List.class);
	}

	/** Retrieve-then-generate: returns {answer, sources, model_used, passages}. */
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