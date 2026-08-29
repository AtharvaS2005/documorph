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

@Entity
@Table(name = "file_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileItem {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	private UploadSession session;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "file_size", nullable = false)
	private Long fileSize;

	@Column(name = "mime_type")
	private String mimeType;

	@Column(name = "file_path", nullable = false)
	private String filePath;

	@CreationTimestamp
	@Column(name = "uploaded_at", updatable = false)
	private OffsetDateTime uploadedAt;
}