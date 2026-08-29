package com.documorph.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "extraction_outputs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionOutput {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "job_id", nullable = false, unique = true)
	private ExtractionJob job;

	@Column(name = "extracted_text", columnDefinition = "TEXT")
	private String extractedText;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "structured_json", columnDefinition = "jsonb")
	private String structuredJson;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "tables", columnDefinition = "jsonb")
	private String tables;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private OffsetDateTime createdAt;
}