package com.documorph.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

/** One question asked in the document chat, and what happened to it. */
@Entity
@Table(name = "chat_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatLog {

	/** ANSWERED, NOT_FOUND, BLOCKED, REFUSED and UNGROUNDED come from the pipeline. */
	public static final String RATE_LIMITED = "RATE_LIMITED";
	public static final String ERROR = "ERROR";

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workspace_id")
	private Workspace workspace;

	@Column(name = "question", nullable = false, columnDefinition = "text")
	private String question;

	@Column(name = "answer", columnDefinition = "text")
	private String answer;

	@Column(name = "outcome", nullable = false, length = 30)
	private String outcome;

	/** Cited passage numbers, comma-separated. */
	@Column(name = "sources")
	private String sources;

	@Column(name = "passages_used", nullable = false)
	private int passagesUsed;

	@Column(name = "sanitized_passages", nullable = false)
	private int sanitizedPassages;

	@Column(name = "redactions", nullable = false)
	private int redactions;

	@Column(name = "latency_ms")
	private Integer latencyMs;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private OffsetDateTime createdAt;
}
