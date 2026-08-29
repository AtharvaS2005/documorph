package com.documorph.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documorph.dto.RecordSetRequest;
import com.documorph.dto.RecordSetResponse;
import com.documorph.exception.ResourceNotFoundException;
import com.documorph.model.Category;
import com.documorph.model.RecordSet;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.CategoryRepository;
import com.documorph.repository.RecordSetRepository;
import com.documorph.repository.UserRepository;
import com.documorph.repository.WorkspaceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecordSetService {

	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final CategoryRepository categoryRepository;
	private final RecordSetRepository recordSetRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Transactional
	public RecordSetResponse createRecordSet(RecordSetRequest request, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		Category category = categoryRepository.findById(request.getCategoryId())
				.orElseThrow(() -> new ResourceNotFoundException("Category not found."));

		if (!category.getWorkspace().getId().equals(workspace.getId())) {
			throw new ResourceNotFoundException("Category not found.");
		}

		if (recordSetRepository.existsByCategoryId(category.getId())) {
			throw new IllegalArgumentException("A record set already exists for this category.");
		}

		RecordSet recordSet = RecordSet.builder().category(category).workspace(workspace)
				.storageFormat(category.getStorageFormat()).build();
		recordSet = recordSetRepository.save(recordSet);

		return toResponse(recordSet);
	}

	@Transactional(readOnly = true)
	public List<RecordSetResponse> getRecordSets(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		return recordSetRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspace.getId()).stream()
				.map(this::toResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public RecordSetResponse getRecordSet(UUID id, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		if (!recordSetRepository.existsByIdAndWorkspaceId(id, workspace.getId())) {
			throw new ResourceNotFoundException("Record set not found.");
		}

		RecordSet recordSet = recordSetRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Record set not found."));

		return toResponse(recordSet);
	}


	private RecordSetResponse toResponse(RecordSet recordSet) {
		return RecordSetResponse.builder().id(recordSet.getId()).categoryId(recordSet.getCategory().getId())
				.categoryName(recordSet.getCategory().getCategoryName()).storageFormat(recordSet.getStorageFormat())
				.currentVersion(recordSet.getCurrentVersion()).mergedContent(recordSet.getMergedContent())
				.createdAt(recordSet.getCreatedAt()).updatedAt(recordSet.getUpdatedAt()).build();
	}

	/**
	 * Append one completed extraction into the record set belonging to the given
	 * category. No-op if that category has no record set, or if this file was
	 * already recorded (idempotent).
	 */
	@Transactional
	public void appendExtraction(UUID categoryId, UUID fileId, String fileName, String structuredJson,
			OffsetDateTime processedAt) {
		// tolerate pre-existing duplicates by using the oldest record set
		List<RecordSet> sets = recordSetRepository.findByCategoryIdOrderByCreatedAtAsc(categoryId);
		if (sets.isEmpty()) {
			return;
		}
		RecordSet recordSet = sets.get(0);

		Map<String, Object> fields = parseFields(structuredJson);
		ObjectNode root = readRoot(recordSet.getMergedContent());

		Category.StorageFormat format = recordSet.getStorageFormat() != null ? recordSet.getStorageFormat()
				: Category.StorageFormat.DOCUMENT_WISE;

		boolean changed = format == Category.StorageFormat.CONSOLIDATED ? mergeConsolidated(root, fileId, fields)
				: mergeDocumentWise(root, fileId, fileName, processedAt, fields);

		if (!changed) {
			return;
		}

		recordSet.setMergedContent(writeRoot(root));
		recordSet.setCurrentVersion(recordSet.getCurrentVersion() + 1);
		recordSetRepository.save(recordSet);
	}

	private boolean mergeDocumentWise(ObjectNode root, UUID fileId, String fileName, OffsetDateTime processedAt,
			Map<String, Object> fields) {
		root.put("storageFormat", "DOCUMENT_WISE");
		ArrayNode docs = arrayAt(root, "documents");

		String fileIdStr = fileId.toString();
		for (JsonNode existing : docs) {
			if (fileIdStr.equals(existing.path("fileId").asText(null))) {
				return false;
			}
		}

		ObjectNode entry = docs.addObject();
		entry.put("fileId", fileIdStr);
		entry.put("fileName", fileName);
		entry.put("processedAt", processedAt != null ? processedAt.toString() : null);
		entry.set("fields", objectMapper.valueToTree(fields));

		root.put("documentCount", docs.size());
		return true;
	}

	private boolean mergeConsolidated(ObjectNode root, UUID fileId, Map<String, Object> fields) {
		root.put("storageFormat", "CONSOLIDATED");
		ArrayNode sourceIds = arrayAt(root, "sourceFileIds");

		String fileIdStr = fileId.toString();
		for (JsonNode existing : sourceIds) {
			if (fileIdStr.equals(existing.asText(null))) {
				return false;
			}
		}
		sourceIds.add(fileIdStr);

		ObjectNode merged = objectAt(root, "fields");
		fields.forEach((key, value) -> {
			ArrayNode values = arrayAt(merged, key);
			String asText = value == null ? null : String.valueOf(value);
			for (JsonNode v : values) {
				if (asText == null ? v.isNull() : asText.equals(v.asText(null))) {
					return;
				}
			}
			if (asText == null) {
				values.addNull();
			} else {
				values.add(asText);
			}
		});

		root.put("documentCount", sourceIds.size());
		return true;
	}

	private ArrayNode arrayAt(ObjectNode parent, String field) {
		JsonNode existing = parent.get(field);
		return existing instanceof ArrayNode arr ? arr : parent.putArray(field);
	}

	private ObjectNode objectAt(ObjectNode parent, String field) {
		JsonNode existing = parent.get(field);
		return existing instanceof ObjectNode obj ? obj : parent.putObject(field);
	}

	private ObjectNode readRoot(String json) {
		if (json == null || json.isBlank()) {
			return objectMapper.createObjectNode();
		}
		try {
			JsonNode node = objectMapper.readTree(json);
			return node instanceof ObjectNode obj ? obj : objectMapper.createObjectNode();
		} catch (Exception e) {
			return objectMapper.createObjectNode();
		}
	}

	private String writeRoot(ObjectNode root) {
		try {
			return objectMapper.writeValueAsString(root);
		} catch (Exception e) {
			throw new IllegalStateException("Could not serialize record set content", e);
		}
	}

	private Map<String, Object> parseFields(String structuredJson) {
		if (structuredJson == null || structuredJson.isBlank()) {
			return Map.of();
		}
		try {
			JsonNode node = objectMapper.readTree(structuredJson);
			if (!node.isObject()) {
				return Map.of();
			}
			return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			return Map.of();
		}
	}

	@Transactional
	public void deleteRecordSet(UUID id, String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		if (!recordSetRepository.existsByIdAndWorkspaceId(id, workspace.getId())) {
			throw new ResourceNotFoundException("Record set not found.");
		}
		recordSetRepository.deleteById(id);
	}
}