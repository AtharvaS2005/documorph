package com.documorph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

	@NotBlank(message = "Question is required.")
	@Size(max = 1000, message = "Question must be at most 1000 characters.")
	private String question;
}
