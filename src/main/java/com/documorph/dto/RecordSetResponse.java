package com.documorph.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.documorph.model.Category;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecordSetResponse {
	private UUID id;
	private UUID categoryId;
	private String categoryName;
	private Category.StorageFormat storageFormat;
	private int currentVersion;
	private String mergedContent;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}