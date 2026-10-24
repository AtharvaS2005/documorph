package com.documorph.dto;

import com.documorph.model.Category.CategoryType;
import com.documorph.model.Category.ExtractionMode;
import com.documorph.model.Category.StorageFormat;
import com.documorph.model.Category.SummaryStyle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CategoryRequest {

	@NotBlank(message = "Category name is required")
	@Size(max = 100, message = "Category name must be at most 100 characters.")
	private String categoryName;

	@NotNull(message = "Category type is required")
	private CategoryType categoryType;

	@NotNull(message = "Default extraction mode is required")
	private ExtractionMode defaultExtractionMode;

	private SummaryStyle summaryStyle;
	@Size(max = 2000, message = "Custom prompt must be at most 2000 characters.")
	private String customPrompt;
	private StorageFormat storageFormat;

	@Size(max = 1000, message = "Description must be at most 1000 characters.")
	private String description;

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCategoryName() {
		return categoryName;
	}

	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}

	public CategoryType getCategoryType() {
		return categoryType;
	}

	public void setCategoryType(CategoryType categoryType) {
		this.categoryType = categoryType;
	}

	public ExtractionMode getDefaultExtractionMode() {
		return defaultExtractionMode;
	}

	public void setDefaultExtractionMode(ExtractionMode defaultExtractionMode) {
		this.defaultExtractionMode = defaultExtractionMode;
	}

	public SummaryStyle getSummaryStyle() {
		return summaryStyle;
	}

	public void setSummaryStyle(SummaryStyle summaryStyle) {
		this.summaryStyle = summaryStyle;
	}

	public String getCustomPrompt() {
		return customPrompt;
	}

	public void setCustomPrompt(String customPrompt) {
		this.customPrompt = customPrompt;
	}

	public StorageFormat getStorageFormat() {
		return storageFormat;
	}

	public void setStorageFormat(StorageFormat storageFormat) {
		this.storageFormat = storageFormat;
	}
}