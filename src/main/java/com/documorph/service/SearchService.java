package com.documorph.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.documorph.dto.ChatResponse;
import com.documorph.dto.SearchResultResponse;
import com.documorph.exception.ResourceNotFoundException;
import com.documorph.exception.ServiceUnavailableException;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.UserRepository;
import com.documorph.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchService {

	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final PythonPipelineClient pythonPipelineClient;

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
	 * from another tenant's files.
	 */
	public ChatResponse chat(String question, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		Map<?, ?> raw;
		try {
			raw = pythonPipelineClient.answer(question.trim(), CHAT_TOP_K, workspace.getId().toString());
		} catch (ResourceAccessException e) {
			throw new ServiceUnavailableException(
					"The processing service is offline. Start the NLP pipeline and try again.");
		} catch (Exception e) {
			throw new ServiceUnavailableException("Could not get an answer right now. Please try again.");
		}

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
		return ChatResponse.builder().answer(asString(raw.get("answer"))).sources(sources)
				.modelUsed(asString(raw.get("model_used"))).passages(passages).build();
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