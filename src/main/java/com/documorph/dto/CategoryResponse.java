package com.documorph.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.documorph.model.Category.CategoryType;
import com.documorph.model.Category.ExtractionMode;
import com.documorph.model.Category.StorageFormat;
import com.documorph.model.Category.SummaryStyle;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {
	private UUID id;
	private String categoryName;
	private CategoryType categoryType;
	private ExtractionMode defaultExtractionMode;
	private SummaryStyle summaryStyle;
	private String customPrompt;
	private StorageFormat storageFormat;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
	private String description;
}