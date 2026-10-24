package com.documorph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForumReplyRequest {
	@NotBlank(message = "Message is required.")
	@Size(max = 10000, message = "Message must be at most 10000 characters.")
	private String message;
}
