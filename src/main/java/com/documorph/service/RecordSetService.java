package com.documorph.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import com.documorph.repository.ExtractionJobRepository;
import com.documorph.repository.ExtractionOutputRepository;
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
	private final ExtractionJobRepository extractionJobRepository;
	private final ExtractionOutputRepository extractionOutputRepository;
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

	/**
	 * Flattens mergedContent into a header-first table: one row per document for
	 * DOCUMENT_WISE sets, one column per field for CONSOLIDATED sets.
	 */
	@Transactional(readOnly = true)
	public List<List<String>> exportTable(UUID id, String email) {
		RecordSetResponse rec = getRecordSet(id, email);
		ObjectNode root = readRoot(rec.getMergedContent());
		List<List<String>> table = new ArrayList<>();

		if ("CONSOLIDATED".equals(root.path("storageFormat").asText())) {
			JsonNode fields = root.path("fields");
			List<String> header = new ArrayList<>();
			List<List<String>> cols = new ArrayList<>();
			fields.properties().forEach(e -> {
				header.add(e.getKey());
				List<String> col = new ArrayList<>();
				if (e.getValue().isArray()) {
					e.getValue().forEach(v -> col.add(cellText(v)));
				} else {
					col.add(cellText(e.getValue()));
				}
				cols.add(col);
			});
			table.add(header);
			int n = cols.stream().mapToInt(List::size).max().orElse(0);
			for (int r = 0; r < n; r++) {
				List<String> row = new ArrayList<>();
				for (List<String> col : cols) {
					row.add(r < col.size() ? col.get(r) : "");
				}
				table.add(row);
			}
		} else {
			JsonNode docs = root.path("documents");
			LinkedHashSet<String> keys = new LinkedHashSet<>();
			docs.forEach(d -> d.path("fields").properties().forEach(e -> keys.add(e.getKey())));
			List<String> header = new ArrayList<>(List.of("fileName", "processedAt"));
			header.addAll(keys);
			table.add(header);
			docs.forEach(d -> {
				List<String> row = new ArrayList<>();
				row.add(d.path("fileName").asText(""));
				row.add(d.path("processedAt").asText(""));
				for (String k : keys) {
					row.add(cellText(d.path("fields").get(k)));
				}
				table.add(row);
			});
		}
		return table;
	}

	private String cellText(JsonNode v) {
		if (v == null || v.isNull() || v.isMissingNode()) {
			return "";
		}
		return v.isValueNode() ? v.asText() : v.toString();
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

	/**
	 * Apply a user's correction of one file's fields. Document-wise sets replace
	 * that file's entry; consolidated sets are rebuilt from every source file's
	 * saved fields, since their value lists don't record which file each value
	 * came from. Adds the file if it was never recorded.
	 */
	@Transactional
	public void updateExtraction(UUID categoryId, UUID fileId, String fileName, String structuredJson,
			OffsetDateTime processedAt) {
		List<RecordSet> sets = recordSetRepository.findByCategoryIdOrderByCreatedAtAsc(categoryId);
		if (sets.isEmpty()) {
			return;
		}
		RecordSet recordSet = sets.get(0);
		Map<String, Object> fields = parseFields(structuredJson);
		ObjectNode root = readRoot(recordSet.getMergedContent());
		String fileIdStr = fileId.toString();

		if (recordSet.getStorageFormat() == Category.StorageFormat.CONSOLIDATED) {
			ArrayNode sourceIds = arrayAt(root, "sourceFileIds");
			List<String> ids = new ArrayList<>();
			sourceIds.forEach(n -> ids.add(n.asText()));
			if (!ids.contains(fileIdStr)) {
				ids.add(fileIdStr);
			}
			ObjectNode rebuilt = objectMapper.createObjectNode();
			for (String id : ids) {
				Map<String, Object> sourceFields = id.equals(fileIdStr) ? fields : savedFields(UUID.fromString(id));
				mergeConsolidated(rebuilt, UUID.fromString(id), sourceFields);
			}
			root = rebuilt;
		} else {
			ArrayNode docs = arrayAt(root, "documents");
			ObjectNode entry = null;
			for (JsonNode existing : docs) {
				if (fileIdStr.equals(existing.path("fileId").asText(null)) && existing instanceof ObjectNode obj) {
					entry = obj;
				}
			}
			if (entry == null) {
				mergeDocumentWise(root, fileId, fileName, processedAt, fields);
			} else {
				entry.set("fields", objectMapper.valueToTree(fields));
			}
		}

		recordSet.setMergedContent(writeRoot(root));
		recordSet.setCurrentVersion(recordSet.getCurrentVersion() + 1);
		recordSetRepository.save(recordSet);
	}

	private Map<String, Object> savedFields(UUID fileId) {
		return extractionJobRepository.findByFileId(fileId)
				.flatMap(job -> extractionOutputRepository.findByJobId(job.getId()))
				.map(output -> parseFields(output.getStructuredJson())).orElse(Map.of());
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