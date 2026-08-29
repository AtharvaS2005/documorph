package com.documorph.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "upload_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadSession {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private Category category;

	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(name = "session_type", nullable = false)
	private SessionType sessionType;

	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false)
	@Builder.Default
	private SessionStatus status = SessionStatus.QUEUED;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private OffsetDateTime createdAt;

	public enum SessionType {
		CATEGORY_RUN, SINGLE_SESSION
	}

	public enum SessionStatus {
		QUEUED, PROCESSING, COMPLETED, PARTIAL_FAILURE, FAILED
	}
}