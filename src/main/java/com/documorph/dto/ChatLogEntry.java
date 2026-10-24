package com.documorph.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatLogEntry {
	private UUID id;
	private String question;
	private String answer;
	private String outcome;
	private String sources;
	private int passagesUsed;
	private int sanitizedPassages;
	private int redactions;
	private Integer latencyMs;
	private OffsetDateTime createdAt;
}
