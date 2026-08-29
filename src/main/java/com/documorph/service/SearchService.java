package com.documorph.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.documorph.dto.SearchResultResponse;
import com.documorph.exception.ResourceNotFoundException;
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