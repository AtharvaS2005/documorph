package com.documorph.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "workspace_id")
	private Workspace workspace;

	@Column(name = "category_name", nullable = false)
	private String categoryName;

	@Enumerated(EnumType.STRING)
	@Column(name = "category_type", nullable = false)
	private CategoryType categoryType;

	@Enumerated(EnumType.STRING)
	@Column(name = "default_extraction_mode", nullable = false)
	private ExtractionMode defaultExtractionMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "summary_style")
	private SummaryStyle summaryStyle;

	@Column(name = "custom_prompt", columnDefinition = "TEXT")
	private String customPrompt;

	@Enumerated(EnumType.STRING)
	@Column(name = "storage_format")
	private StorageFormat storageFormat;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private OffsetDateTime updatedAt;

	public enum CategoryType {
		DOCUMENT, RECORD
	}

	public enum ExtractionMode {
		FULL, SUMMARY, KEY_INSIGHTS, CUSTOM_FIELDS
	}

	public enum SummaryStyle {
		SHORT, BRIEF
	}

	public enum StorageFormat {
		DOCUMENT_WISE, CONSOLIDATED
	}
}