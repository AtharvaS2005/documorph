package com.documorph.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForumPostRequest {
	@NotBlank(message = "Title is required.")
	@Size(max = 200, message = "Title must be at most 200 characters.")
	private String title;

	@NotBlank(message = "Message is required.")
	@Size(max = 10000, message = "Message must be at most 10000 characters.")
	private String message;

	private UUID sessionId;
	private UUID recordSetId;
}
