package com.documorph.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

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
@Table(name = "extraction_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionJob {

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private OffsetDateTime createdAt;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "file_id", nullable = false)
	private FileItem file;

	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(name = "extraction_mode", nullable = false)
	private Category.ExtractionMode extractionMode;

	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(name = "summary_style")
	private Category.SummaryStyle summaryStyle;

	@Column(name = "custom_prompt", columnDefinition = "TEXT")
	private String customPrompt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@Builder.Default
	private JobStatus status = JobStatus.QUEUED;

	@Column(name = "error_code")
	private String errorCode;

	@Column(name = "failure_reason", columnDefinition = "TEXT")
	private String failureReason;

	@Column(name = "external_job_id")
	private String externalJobId;

	@Column(name = "retry_count", nullable = false)
	@Builder.Default
	private Integer retryCount = 0;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "progress")
	private Double progress;

	public enum JobStatus {
		QUEUED, PROCESSING, COMPLETED, FAILED
	}
}