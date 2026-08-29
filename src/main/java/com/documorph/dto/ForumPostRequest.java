package com.documorph.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class ForumPostRequest {
	private String title;
	private String message;
	private UUID sessionId;
	private UUID recordSetId;
}