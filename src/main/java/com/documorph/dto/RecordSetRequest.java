package com.documorph.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecordSetRequest {
	@NotNull(message = "Category is required.")
	private UUID categoryId;
}
