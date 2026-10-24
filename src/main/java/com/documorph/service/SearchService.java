package com.documorph.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.documorph.dto.ChatLogEntry;
import com.documorph.dto.ChatResponse;
import com.documorph.dto.SearchResultResponse;
import com.documorph.exception.ResourceNotFoundException;
import com.documorph.exception.ServiceUnavailableException;
import com.documorph.exception.TooManyRequestsException;
import com.documorph.model.ChatLog;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.ChatLogRepository;
import com.documorph.repository.UserRepository;
import com.documorph.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final PythonPipelineClient pythonPipelineClient;
	private final ChatRateLimiter chatRateLimiter;
	private final ChatLogRepository chatLogRepository;

	/** Passages handed to the model; more costs latency on a small local model. */
	private static final int CHAT_TOP_K = 5;

	public List<SearchResultResponse> search(String query, int topK, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		List<?> rawResults = pythonPipelineClient.retrieve(query, topK, workspace.getId().toString());

		List<SearchResultResponse> results = new ArrayList<>();
		for (Object item : rawResults) {
			Map<?, ?> row = (Map<?, ?>) item;
			results.add(SearchResultResponse.builder().text(asString(row.get("text"))).score(asDouble(row.get("score")))
					.docId(asString(row.get("doc_id"))).chunkIndex(asInt(row.get("chunk_index")))
					.sessionId(asString(row.get("session_id"))).fileName(asString(row.get("file_name"))).build());
		}
		return results;
	}

	/**
	 * Answer a question from the caller's own documents. Retrieval is scoped to
	 * their workspace, so passages (and the answer built from them) never come
	 * from another tenant's files. The pipeline applies the content guardrails
	 * (instructions in questions or documents, off-topic requests, unsupported
	 * numbers, sensitive identifiers); this adds the per-user rate limit and
	 * records every question, including rejected ones, in the audit log.
	 */
	public ChatResponse chat(String question, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));
		String q = question.trim();
		String userKey = user.getId().toString();

		try {
			chatRateLimiter.acquire(userKey);
		} catch (TooManyRequestsException e) {
			audit(user, workspace, q, null, ChatLog.RATE_LIMITED, null);
			throw e;
		}

		long started = System.currentTimeMillis();
		try {
			Map<?, ?> raw;
			try {
				raw = pythonPipelineClient.answer(q, CHAT_TOP_K, workspace.getId().toString());
			} catch (ResourceAccessException e) {
				audit(user, workspace, q, null, ChatLog.ERROR, started);
				throw new ServiceUnavailableException(
						"The processing service is offline. Start the NLP pipeline and try again.");
			} catch (Exception e) {
				audit(user, workspace, q, null, ChatLog.ERROR, started);
				throw new ServiceUnavailableException("Could not get an answer right now. Please try again.");
			}

			ChatResponse response = toChatResponse(raw);
			audit(user, workspace, q, response, response.getOutcome(), started);
			return response;
		} finally {
			chatRateLimiter.release(userKey);
		}
	}

	/** The caller's most recent chat questions, newest first. */
	public List<ChatLogEntry> chatLog(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));
		return chatLogRepository.findTop50ByWorkspaceIdOrderByCreatedAtDesc(workspace.getId()).stream()
				.map(l -> ChatLogEntry.builder().id(l.getId()).question(l.getQuestion()).answer(l.getAnswer())
						.outcome(l.getOutcome()).sources(l.getSources()).passagesUsed(l.getPassagesUsed())
						.sanitizedPassages(l.getSanitizedPassages()).redactions(l.getRedactions())
						.latencyMs(l.getLatencyMs()).createdAt(l.getCreatedAt()).build())
				.toList();
	}

	private ChatResponse toChatResponse(Map<?, ?> raw) {
		List<Integer> sources = new ArrayList<>();
		if (raw.get("sources") instanceof List<?> list) {
			for (Object n : list) {
				if (n instanceof Number num) {
					sources.add(num.intValue());
				}
			}
		}
		List<ChatResponse.Passage> passages = new ArrayList<>();
		if (raw.get("passages") instanceof List<?> list) {
			for (Object item : list) {
				Map<?, ?> row = (Map<?, ?>) item;
				passages.add(ChatResponse.Passage.builder().number(asInt(row.get("number")))
						.text(asString(row.get("text"))).score(asDouble(row.get("score")))
						.docId(asString(row.get("doc_id"))).sessionId(asString(row.get("session_id")))
						.fileName(asString(row.get("file_name"))).build());
			}
		}
		Map<?, ?> guard = raw.get("guard") instanceof Map<?, ?> m ? m : Map.of();
		String outcome = asString(raw.get("outcome"));
		return ChatResponse.builder().outcome(outcome == null ? "ANSWERED" : outcome)
				.answer(asString(raw.get("answer"))).sources(sources).modelUsed(asString(raw.get("model_used")))
				.passages(passages).sanitizedPassages(Math.max(0, asInt(guard.get("sanitizedPassages"))))
				.redactions(Math.max(0, asInt(guard.get("redactions")))).build();
	}

	/** Logging must never break the chat itself, so failures are only logged. */
	private void audit(User user, Workspace workspace, String question, ChatResponse response, String outcome,
			Long started) {
		try {
			String sources = response == null ? null
					: response.getSources().stream().map(String::valueOf).collect(Collectors.joining(","));
			chatLogRepository.save(ChatLog.builder().user(user).workspace(workspace)
					.question(PiiRedactor.redact(question)).answer(response != null ? response.getAnswer() : null)
					.outcome(outcome).sources(sources)
					.passagesUsed(response != null ? response.getPassages().size() : 0)
					.sanitizedPassages(response != null ? response.getSanitizedPassages() : 0)
					.redactions(response != null ? response.getRedactions() : 0)
					.latencyMs(started == null ? null : (int) (System.currentTimeMillis() - started)).build());
		} catch (Exception e) {
			log.warn("Could not write chat audit log entry", e);
		}
	}

	private String asString(Object o) {
		return o != null ? String.valueOf(o) : null;
	}

	private double asDouble(Object o) {
		return o instanceof Number n ? n.doubleValue() : 0.0;
	}

	private int asInt(Object o) {
		return o instanceof Number n ? n.intValue() : -1;
	}
}