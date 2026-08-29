package com.documorph.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ForumPostResponse {
	private UUID id;
	private UUID organizationId;
	private UUID authorId;
	private String authorEmail;
	private String title;
	private String message;
	private UUID sessionId;
	private UUID recordSetId;
	private long replyCount;
	private OffsetDateTime createdAt;
}