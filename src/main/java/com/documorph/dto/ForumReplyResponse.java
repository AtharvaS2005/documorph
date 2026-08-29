package com.documorph.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ForumReplyResponse {
	private UUID id;
	private UUID postId;
	private UUID authorId;
	private String authorEmail;
	private String message;
	private OffsetDateTime createdAt;
}